package dayflow.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One entry stored in the data file. A schedule with a repeat rule stands for a whole series,
 * and the views work with its {@link Occurrence}s on concrete dates.
 */
public final class Schedule {
    private final String id;
    private String title;
    private Kind kind;
    private LocalDate date;
    private LocalTime start;
    private LocalTime end;
    private Category category;
    private Priority priority = Priority.MEDIUM;
    private String memo = "";
    private Repeat repeat = Repeat.NONE;
    private LocalDate repeatUntil;
    private final Set<LocalDate> skippedDates = new HashSet<>();
    private final Set<LocalDate> doneDates = new HashSet<>();
    private int workMinutes = -1;
    private WorkTarget plannedFor;

    private Schedule(String title, Kind kind, LocalDate date, LocalTime start, LocalTime end, Category category) {
        this.id = UUID.randomUUID().toString();
        this.title = title;
        this.kind = kind;
        this.date = date;
        this.start = start;
        this.end = end;
        this.category = category;
    }

    private Schedule(String id, ScheduleDraft values) {
        this.id = id;
        update(values);
    }

    /**
     * A new schedule with a new id from values that were not saved yet.
     *
     * @param values the values to store
     * @return the new schedule
     */
    static Schedule create(ScheduleDraft values) {
        return new Schedule(UUID.randomUUID().toString(), values);
    }

    /**
     * A schedule read back from the data file, keeping its stored id and records.
     *
     * @param id the stored id
     * @param values the stored values
     * @param doneDates the dates marked done
     * @param skippedDates the dates removed from the series
     * @return the restored schedule
     */
    public static Schedule restore(String id, ScheduleDraft values, Set<LocalDate> doneDates,
            Set<LocalDate> skippedDates) {
        Schedule schedule = new Schedule(id, values);
        schedule.doneDates.addAll(doneDates);
        schedule.skippedDates.addAll(skippedDates);
        return schedule;
    }

    /**
     * Copies the schedules, keeping ids and records, and points each work block at the copied deadline.
     *
     * @param schedules the schedules to copy
     * @return the copies in the same order
     */
    public static List<Schedule> copyAll(List<Schedule> schedules) {
        Map<Schedule, Schedule> copies = new IdentityHashMap<>();
        for (Schedule s : schedules) {
            copies.put(s, restore(s.id, s.toDraft(), s.doneDates, s.skippedDates));
        }
        List<Schedule> result = new ArrayList<>();
        for (Schedule s : schedules) {
            Schedule copy = copies.get(s);
            if (s.plannedFor != null) {
                Schedule deadline = copies.getOrDefault(s.plannedFor.deadline(), s.plannedFor.deadline());
                copy.plannedFor = new WorkTarget(deadline, s.plannedFor.date());
            }
            result.add(copy);
        }
        return result;
    }

    /**
     * A timed schedule with a start and an end time.
     *
     * @param title the title
     * @param date the date
     * @param start the start time, as {@code HH:mm}
     * @param end the end time, as {@code HH:mm}
     * @param category the category
     * @return the new schedule
     */
    public static Schedule timed(String title, LocalDate date, String start, String end, Category category) {
        return new Schedule(title, Kind.TIMED, date, LocalTime.parse(start), LocalTime.parse(end), category);
    }

    /**
     * A deadline keeps its due time in {@code start}.
     *
     * @param title the title
     * @param date the due date
     * @param due the due time, as {@code HH:mm}
     * @param category the category
     * @return the new schedule
     */
    public static Schedule deadline(String title, LocalDate date, String due, Category category) {
        return new Schedule(title, Kind.DEADLINE, date, LocalTime.parse(due), null, category);
    }

    /**
     * An all-day schedule.
     *
     * @param title the title
     * @param date the date
     * @param category the category
     * @return the new schedule
     */
    public static Schedule allDay(String title, LocalDate date, Category category) {
        return new Schedule(title, Kind.ALL_DAY, date, null, null, category);
    }

    /**
     * Sets the repeat rule.
     *
     * @param rule how the schedule repeats
     * @param until the last date of the series
     * @return this schedule
     */
    public Schedule repeat(Repeat rule, LocalDate until) {
        this.repeat = rule;
        this.repeatUntil = until;
        return this;
    }

    /**
     * Removes one occurrence from the series ("this occurrence only").
     *
     * @param occurrence the date to remove
     * @return this schedule
     */
    public Schedule skip(LocalDate occurrence) {
        skippedDates.add(occurrence);
        return this;
    }

    /**
     * Done is kept per occurrence so that a repeating deadline can be checked off one date at a time.
     *
     * @param occurrence the date of the occurrence
     * @param done whether it is done
     * @return this schedule
     */
    public Schedule setDone(LocalDate occurrence, boolean done) {
        if (done) {
            doneDates.add(occurrence);
        } else {
            doneDates.remove(occurrence);
        }
        return this;
    }

    /**
     * How long the work for a deadline takes. The planner uses it.
     *
     * @param minutes the estimate in minutes
     * @return this schedule
     */
    public Schedule work(int minutes) {
        this.workMinutes = minutes;
        return this;
    }

    /**
     * Sets the priority.
     *
     * @param value the new priority
     * @return this schedule
     */
    public Schedule priority(Priority value) {
        this.priority = value;
        return this;
    }

    /**
     * Sets the memo.
     *
     * @param text the new memo
     * @return this schedule
     */
    public Schedule memo(String text) {
        this.memo = text;
        return this;
    }

    /**
     * A block of work that the planner placed for one occurrence of a deadline.
     *
     * @param target the deadline occurrence the block is for
     * @param date the date of the block
     * @param start the start time
     * @param end the end time
     * @return the new work block
     */
    public static Schedule workBlock(WorkTarget target, LocalDate date, LocalTime start, LocalTime end) {
        Schedule block = new Schedule("", Kind.TIMED, date, start, end, target.deadline().category());
        block.plannedFor = target;
        return block;
    }

    /**
     * Every date on which this schedule happens between the two dates, both included.
     *
     * @param from the first date
     * @param to the last date
     * @return the occurrences in date order
     */
    public List<Occurrence> occurrences(LocalDate from, LocalDate to) {
        List<Occurrence> result = new ArrayList<>();
        LocalDate end = repeat == Repeat.NONE ? date : repeatUntil;
        LocalDate last = end == null || end.isAfter(to) ? to : end;
        for (int n = 0; ; n++) {
            LocalDate d = repeat.nth(date, n);
            if (d.isAfter(last)) {
                break;
            }
            if (!d.isBefore(from) && !skippedDates.contains(d)) {
                result.add(new Occurrence(this, d));
            }
        }
        return result;
    }

    /**
     * The current values as a draft.
     *
     * @return the values as a draft
     */
    public ScheduleDraft toDraft() {
        return new ScheduleDraft(title, kind, date, start, end, category, priority, memo, repeat, repeatUntil,
                workMinutes, plannedFor);
    }

    /**
     * Replaces the values. The id, done dates, and skipped dates stay as they are.
     *
     * @param values the new values
     */
    public void update(ScheduleDraft values) {
        this.title = values.title();
        this.kind = values.kind();
        this.date = values.date();
        this.start = values.start();
        this.end = values.end();
        this.category = values.category();
        this.priority = values.priority();
        this.memo = values.memo();
        this.repeat = values.repeat();
        this.repeatUntil = values.repeatUntil();
        this.workMinutes = values.workMinutes();
        this.plannedFor = values.plannedFor();
    }

    /**
     * The dates of occurrences marked as done, as a read-only copy.
     *
     * @return the done dates
     */
    public Set<LocalDate> doneDates() {
        return Set.copyOf(doneDates);
    }

    /**
     * The dates removed from the series, as a read-only copy.
     *
     * @return the skipped dates
     */
    public Set<LocalDate> skippedDates() {
        return Set.copyOf(skippedDates);
    }

    /** True when the repeat rule has an occurrence on the date, whether or not it was skipped. */
    boolean hasOccurrenceOn(LocalDate day) {
        LocalDate last = repeat == Repeat.NONE || repeatUntil == null ? day : repeatUntil;
        for (int n = 0; ; n++) {
            LocalDate d = repeat.nth(date, n);
            if (d.isAfter(day) || d.isAfter(last)) {
                return false;
            }
            if (d.equals(day)) {
                return true;
            }
        }
    }

    /** Moves the done and skipped records of the only occurrence to its new date. */
    void moveRecords(LocalDate from, LocalDate to) {
        if (doneDates.remove(from)) {
            doneDates.add(to);
        }
        if (skippedDates.remove(from)) {
            skippedDates.add(to);
        }
    }

    /** Drops the done and skipped records whose date is no longer an occurrence. */
    void pruneRecords() {
        doneDates.removeIf(d -> !hasOccurrenceOn(d));
        skippedDates.removeIf(d -> !hasOccurrenceOn(d));
    }

    void clearDone() {
        doneDates.clear();
    }

    /** Points a work block at another deadline occurrence. */
    void retarget(WorkTarget target) {
        this.plannedFor = target;
    }

    /**
     * Returns the id.
     *
     * @return the id
     */
    public String id() {
        return id;
    }

    /**
     * A work block has no title of its own. It follows the title of its deadline.
     *
     * @return the title
     */
    public String title() {
        return plannedFor == null ? title : "Work: " + plannedFor.deadline().title();
    }

    /**
     * Returns the kind.
     *
     * @return the kind
     */
    public Kind kind() {
        return kind;
    }

    /**
     * Returns the first date.
     *
     * @return the first date
     */
    public LocalDate date() {
        return date;
    }

    /**
     * Returns the start time, or the due time of a deadline.
     *
     * @return the start time, or the due time of a deadline
     */
    public LocalTime start() {
        return start;
    }

    /**
     * Returns the end time.
     *
     * @return the end time
     */
    public LocalTime end() {
        return end;
    }

    /**
     * Returns the category. A work block takes it from its deadline.
     *
     * @return the category. A work block takes it from its deadline
     */
    public Category category() {
        return plannedFor == null ? category : plannedFor.deadline().category();
    }

    /**
     * Returns the priority.
     *
     * @return the priority
     */
    public Priority priority() {
        return priority;
    }

    /**
     * Returns the memo.
     *
     * @return the memo
     */
    public String memo() {
        return memo;
    }

    /**
     * Returns the repeat rule.
     *
     * @return the repeat rule
     */
    public Repeat repeat() {
        return repeat;
    }

    /**
     * Returns the last date of the series.
     *
     * @return the last date of the series
     */
    public LocalDate repeatUntil() {
        return repeatUntil;
    }

    /**
     * Tells whether one occurrence is marked done.
     *
     * @param occurrence the date of the occurrence
     * @return whether it is marked done
     */
    public boolean isDone(LocalDate occurrence) {
        return doneDates.contains(occurrence);
    }

    /**
     * The user's own estimate, or -1 when the default of the category applies.
     *
     * @return the estimate in minutes, or -1
     */
    public int workMinutes() {
        return workMinutes;
    }

    /**
     * The estimate that is actually used: the user's own, or the default of the category.
     *
     * @return the estimate in minutes
     */
    public int effectiveWorkMinutes() {
        return workMinutes >= 0 ? workMinutes : category.defaultWorkMinutes();
    }

    /**
     * Returns the deadline occurrence this work block is for, or null.
     *
     * @return the deadline occurrence this work block is for, or null
     */
    public WorkTarget plannedFor() {
        return plannedFor;
    }
}
