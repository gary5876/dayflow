package dayflow.model;

import dayflow.plan.WorkBlock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * The rules for changing a list of schedules. Each method edits the list in place and knows nothing
 * about saving or undo, so it can be tested without a window.
 */
public final class ScheduleEdits {
    private ScheduleEdits() {
    }

    /**
     * Adds a new schedule made from the values and returns it.
     *
     * @param all the list to edit
     * @param values the values of the new schedule
     * @return the new schedule
     */
    public static Schedule add(List<Schedule> all, ScheduleDraft values) {
        Schedule created = values.toSchedule();
        all.add(created);
        return created;
    }

    /**
     * Changes the whole schedule and fixes records and work blocks that point at dates (R-FORM-11, R-FORM-12).
     *
     * @param all the list to edit
     * @param target the schedule to change
     * @param values the new values
     */
    public static void updateAll(List<Schedule> all, Schedule target, ScheduleDraft values) {
        Kind oldKind = target.kind();
        LocalDate oldDate = target.date();
        Repeat oldRepeat = target.repeat();
        LocalDate oldUntil = target.repeatUntil();
        target.update(values);

        if (oldKind == Kind.DEADLINE && target.kind() != Kind.DEADLINE) {
            target.clearDone();
            all.removeIf(s -> pointsAt(s, target));
        }
        boolean movedSingle = oldRepeat == Repeat.NONE && target.repeat() == Repeat.NONE
                && !oldDate.equals(target.date());
        if (movedSingle) {
            target.moveRecords(oldDate, target.date());
            for (Schedule s : all) {
                if (pointsAt(s, target) && s.plannedFor().date().equals(oldDate)) {
                    s.retarget(new WorkTarget(target, target.date()));
                }
            }
        } else if (!oldDate.equals(target.date()) || oldRepeat != target.repeat()
                || !Objects.equals(oldUntil, target.repeatUntil())) {
            target.pruneRecords();
            all.removeIf(s -> pointsAt(s, target) && !target.hasOccurrenceOn(s.plannedFor().date()));
        }
    }

    /**
     * Changes one occurrence only. The date is skipped in the series and a new schedule without repeat takes
     * its place, with the done mark and the work blocks of that occurrence. Returns the new schedule.
     *
     * @param all the list to edit
     * @param occurrence the occurrence to change
     * @param values the new values
     * @return the new schedule
     */
    public static Schedule updateOne(List<Schedule> all, Occurrence occurrence, ScheduleDraft values) {
        Schedule series = occurrence.schedule();
        ScheduleDraft single = new ScheduleDraft(values.title(), values.kind(), values.date(), values.start(),
                values.end(), values.category(), values.priority(), values.memo(), Repeat.NONE, null,
                values.workMinutes(), values.plannedFor());
        Schedule created = single.toSchedule();
        boolean deadline = created.kind() == Kind.DEADLINE;
        boolean wasDone = occurrence.done();
        series.skip(occurrence.date());
        series.setDone(occurrence.date(), false);
        if (deadline && wasDone) {
            created.setDone(created.date(), true);
        }
        all.removeIf(s -> !deadline && s.plannedFor() != null && s.plannedFor().is(occurrence));
        for (Schedule s : all) {
            if (s.plannedFor() != null && s.plannedFor().is(occurrence)) {
                s.retarget(new WorkTarget(created, created.date()));
            }
        }
        all.add(created);
        return created;
    }

    /**
     * Deletes the schedule and every work block that points at it, started or not (R-PLAN-14).
     *
     * @param all the list to edit
     * @param target the schedule to delete
     */
    public static void deleteAll(List<Schedule> all, Schedule target) {
        all.removeIf(s -> s == target || pointsAt(s, target));
    }

    /**
     * Deletes one occurrence: the date is skipped, its done mark goes, and only the work blocks of that
     * occurrence go.
     *
     * @param all the list to edit
     * @param occurrence the occurrence to delete
     */
    public static void deleteOne(List<Schedule> all, Occurrence occurrence) {
        occurrence.schedule().skip(occurrence.date());
        occurrence.schedule().setDone(occurrence.date(), false);
        all.removeIf(s -> s.plannedFor() != null && s.plannedFor().is(occurrence));
    }

    /**
     * Marks one occurrence done or not done.
     *
     * @param all the list to edit
     * @param occurrence the occurrence to mark
     * @param done whether it is done
     */
    public static void setDone(List<Schedule> all, Occurrence occurrence, boolean done) {
        occurrence.schedule().setDone(occurrence.date(), done);
    }

    /**
     * Sets the priority of a schedule.
     *
     * @param target the schedule to change
     * @param priority the new priority
     */
    public static void setPriority(Schedule target, Priority priority) {
        target.priority(priority);
    }

    /**
     * Replaces the work blocks that lie in the period and have not started with the new ones (R-PLAN-6).
     * A block that has started, including one in progress, stays, and so does a block outside the period.
     *
     * @param all the list to edit
     * @param now the current moment
     * @param from the first day of the period
     * @param until the last day of the period
     * @param blocks the new work blocks
     */
    public static void replacePlan(List<Schedule> all, LocalDateTime now, LocalDate from, LocalDate until,
            List<WorkBlock> blocks) {
        all.removeIf(s -> notStartedBlockBetween(s, now, from, until));
        for (WorkBlock block : blocks) {
            WorkTarget target = new WorkTarget(block.deadline().schedule(), block.deadline().date());
            all.add(Schedule.workBlock(target, block.date(), block.start(), block.end()));
        }
    }

    /**
     * Removes the work blocks of the week that have not started (R-PLAN-8). The week starts on {@code weekStart}.
     *
     * @param all the list to edit
     * @param now the current moment
     * @param weekStart the first day of the week
     */
    public static void clearPlan(List<Schedule> all, LocalDateTime now, LocalDate weekStart) {
        all.removeIf(s -> notStartedBlockBetween(s, now, weekStart, weekStart.plusDays(6)));
    }

    private static boolean pointsAt(Schedule s, Schedule deadline) {
        return s.plannedFor() != null && s.plannedFor().deadline() == deadline;
    }

    private static boolean notStartedBlockBetween(Schedule s, LocalDateTime now, LocalDate from, LocalDate until) {
        return s.plannedFor() != null && !s.date().isBefore(from) && !s.date().isAfter(until)
                && s.date().atTime(s.start()).isAfter(now);
    }
}
