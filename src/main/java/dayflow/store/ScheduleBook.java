package dayflow.store;

import dayflow.model.Kind;
import dayflow.model.Occurrence;
import dayflow.model.Schedule;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The schedules in memory. Every change goes through {@link #change}, so that undo, saving, and
 * notification happen in one place (D-27).
 */
public final class ScheduleBook {
    /** Writes the schedules. {@code ScheduleFile::save} fits, and tests give one that fails. */
    @FunctionalInterface
    public interface Saver {
        /**
         * Writes the schedules.
         *
         * @param schedules the schedules to write
         * @throws IOException if writing fails
         */
        void save(List<Schedule> schedules) throws IOException;
    }

    /**
     * Adds a closing sentence to the status text of a change, for example that the plan became out of date
     * (R-PLAN-12). Gets the schedules before and after the change and returns the sentence, or an empty text.
     */
    @FunctionalInterface
    public interface StatusNote {
        /**
         * Returns the closing sentence.
         *
         * @param before the schedules before the change
         * @param after the schedules after the change
         * @return the sentence, or an empty text
         */
        String of(List<Schedule> before, List<Schedule> after);
    }

    private final List<Schedule> schedules;
    private final Saver saver;
    private final Clock clock;
    private final List<ScheduleListener> listeners = new CopyOnWriteArrayList<>();
    private List<Schedule> beforeLastChange;
    private Change lastChange;
    private StatusNote statusNote = (before, after) -> "";
    private LocalDateTime lastSavedAt;
    private boolean lastSaveFailed;

    /**
     * Creates the book.
     *
     * @param loaded the schedules read from the data file
     * @param saver writes the schedules after a change
     * @param clock the source of the current time
     */
    public ScheduleBook(List<Schedule> loaded, Saver saver, Clock clock) {
        this.schedules = new ArrayList<>(loaded);
        this.saver = saver;
        this.clock = clock;
        // I-8 once on load, so a hand-edited file cannot leave a block whose date is not an occurrence.
        // Nothing is saved here.
        removeBrokenBlocks();
    }

    /**
     * Every occurrence between the two dates, both included, by date and then by time.
     *
     * @param from the first date
     * @param to the last date
     * @return the occurrences
     */
    public List<Occurrence> occurrences(LocalDate from, LocalDate to) {
        List<Occurrence> result = new ArrayList<>();
        for (Schedule s : schedules) {
            result.addAll(s.occurrences(from, to));
        }
        result.sort(Comparator.comparing(Occurrence::date).thenComparing(Occurrence::sortTime));
        return result;
    }

    /**
     * A read-only view of the schedules.
     *
     * @return the schedules
     */
    public List<Schedule> schedules() {
        return Collections.unmodifiableList(schedules);
    }

    /**
     * Runs the edit on the schedule list and saves. The state before the edit is remembered for {@link #undo}.
     * The description becomes the status text, followed by what was removed along with it (R-UNDO-2).
     * Used by every action except the three that {@link #changeWithoutCount} serves, such as add, update,
     * delete, and marking done.
     *
     * @param description the status text of the change
     * @param edit the edit to run on the schedule list
     * @return the result of the change
     */
    public Change change(String description, Consumer<List<Schedule>> edit) {
        return run(description, edit, true);
    }

    /**
     * Same as {@link #change}, but the status text is the description alone. For the actions whose removal is
     * the action itself: Reopened (R-DONE-5), Plan cleared, and replacing a plan. R-DONE-2 still removes the
     * unstarted blocks of a deadline marked done.
     *
     * @param description the status text of the change
     * @param edit the edit to run on the schedule list
     * @return the result of the change
     */
    public Change changeWithoutCount(String description, Consumer<List<Schedule>> edit) {
        return run(description, edit, false);
    }

    private Change run(String description, Consumer<List<Schedule>> edit, boolean countRemoved) {
        List<Schedule> before = Schedule.copyAll(schedules);
        try {
            edit.accept(schedules);
        } catch (RuntimeException e) {
            replaceSchedules(before);
            throw e;
        }
        removeBrokenBlocks();
        removeUnstartedBlocksOfNewlyDone(before);
        String note = statusNote.of(before, schedules);
        String status = description + "." + (countRemoved ? removedText(before) : "")
                + (note.isEmpty() ? "" : " " + note);
        beforeLastChange = before;
        return finish(status, true);
    }

    /**
     * Sets the closing sentence of the status texts of later changes. Undo does not use it (R-UNDO-4).
     *
     * @param note the function that gives the sentence
     */
    public void statusNote(StatusNote note) {
        this.statusNote = note;
    }

    /**
     * Goes back to the state before the last change. Without one, the result has no text and no Undo.
     *
     * @return the result of the undo
     */
    public Change undo() {
        if (beforeLastChange == null) {
            return new Change("", false, lastSavedAt, lastSaveFailed);
        }
        replaceSchedules(beforeLastChange);
        beforeLastChange = null;
        return finish("Undone.", false);
    }

    /**
     * Returns the last change, if there was one.
     *
     * @return the last change, if there was one
     */
    public Optional<Change> lastChange() {
        return Optional.ofNullable(lastChange);
    }

    /**
     * Saves for the exit and tells whether it worked (R-SAVE-4).
     *
     * @return whether the save worked
     */
    public boolean close() {
        return trySave();
    }

    /**
     * Adds a listener that hears about every change.
     *
     * @param listener the listener to add
     */
    public void addListener(ScheduleListener listener) {
        listeners.add(listener);
    }

    private Change finish(String status, boolean canUndo) {
        boolean saved = trySave();
        lastChange = new Change(status, canUndo, saved ? lastSavedAt : null, !saved);
        for (ScheduleListener listener : listeners) {
            listener.scheduleChanged(lastChange);
        }
        return lastChange;
    }

    private boolean trySave() {
        try {
            saver.save(List.copyOf(schedules));
            lastSavedAt = LocalDateTime.now(clock);
            lastSaveFailed = false;
        } catch (IOException | RuntimeException e) {
            lastSaveFailed = true;
        }
        return !lastSaveFailed;
    }

    private void replaceSchedules(List<Schedule> with) {
        schedules.clear();
        schedules.addAll(with);
    }

    /** I-8: a work block needs a deadline in the list, and its date must still be an occurrence of it. */
    private void removeBrokenBlocks() {
        schedules.removeIf(s -> {
            if (s.plannedFor() == null) {
                return false;
            }
            Schedule deadline = s.plannedFor().deadline();
            LocalDate date = s.plannedFor().date();
            return s.kind() != Kind.TIMED || deadline.kind() != Kind.DEADLINE
                    || schedules.stream().noneMatch(other -> other == deadline)
                    || deadline.occurrences(date, date).isEmpty();
        });
    }

    /** R-DONE-2: a deadline occurrence marked done by this change loses the blocks that have not started. */
    private void removeUnstartedBlocksOfNewlyDone(List<Schedule> before) {
        Map<String, Schedule> old = byId(before);
        LocalDateTime now = LocalDateTime.now(clock);
        for (Schedule s : new ArrayList<>(schedules)) {
            Schedule was = old.get(s.id());
            if (was == null || s.kind() != Kind.DEADLINE) {
                continue;
            }
            for (LocalDate date : s.doneDates()) {
                if (!was.isDone(date)) {
                    schedules.removeIf(b -> b.plannedFor() != null && b.plannedFor().deadline() == s
                            && b.plannedFor().date().equals(date) && b.date().atTime(b.start()).isAfter(now));
                }
            }
        }
    }

    /** The text for what the change took away: done marks of schedules that stay, and work blocks. */
    private String removedText(List<Schedule> before) {
        Map<String, Schedule> now = byId(schedules);
        int marks = 0;
        int blocks = 0;
        for (Schedule was : before) {
            Schedule is = now.get(was.id());
            if (was.plannedFor() != null) {
                blocks += is == null ? 1 : 0;
            } else if (is != null) {
                // A mark on a date that is newly skipped left with the occurrence or moved to the new schedule.
                marks += (int) was.doneDates().stream()
                        .filter(d -> !is.isDone(d))
                        .filter(d -> !is.skippedDates().contains(d) || was.skippedDates().contains(d))
                        .count();
            }
        }
        List<String> parts = new ArrayList<>();
        if (marks > 0) {
            parts.add(marks + (marks == 1 ? " done mark" : " done marks"));
        }
        if (blocks > 0) {
            parts.add(blocks + (blocks == 1 ? " work block" : " work blocks"));
        }
        return parts.isEmpty() ? "" : " " + String.join(" and ", parts) + " removed.";
    }

    private static Map<String, Schedule> byId(List<Schedule> list) {
        Map<String, Schedule> map = new HashMap<>();
        for (Schedule s : list) {
            map.put(s.id(), s);
        }
        return map;
    }
}
