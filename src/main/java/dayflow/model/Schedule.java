package dayflow.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * One entry stored in the data file. A schedule with a repeat rule stands for a whole series;
 * the views work with its {@link Occurrence}s on concrete dates.
 */
public final class Schedule {
    private final String id = UUID.randomUUID().toString();
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
        this.title = title;
        this.kind = kind;
        this.date = date;
        this.start = start;
        this.end = end;
        this.category = category;
    }

    public static Schedule timed(String title, LocalDate date, String start, String end, Category category) {
        return new Schedule(title, Kind.TIMED, date, LocalTime.parse(start), LocalTime.parse(end), category);
    }

    /** A deadline keeps its due time in {@code start}. */
    public static Schedule deadline(String title, LocalDate date, String due, Category category) {
        return new Schedule(title, Kind.DEADLINE, date, LocalTime.parse(due), null, category);
    }

    public static Schedule allDay(String title, LocalDate date, Category category) {
        return new Schedule(title, Kind.ALL_DAY, date, null, null, category);
    }

    public Schedule repeat(Repeat rule, LocalDate until) {
        this.repeat = rule;
        this.repeatUntil = until;
        return this;
    }

    /** Removes one occurrence from the series ("this occurrence only"). */
    public Schedule skip(LocalDate occurrence) {
        skippedDates.add(occurrence);
        return this;
    }

    /** Done is kept per occurrence so that a repeating deadline can be checked off one date at a time. */
    public Schedule setDone(LocalDate occurrence, boolean done) {
        if (done) {
            doneDates.add(occurrence);
        } else {
            doneDates.remove(occurrence);
        }
        return this;
    }

    /** How long the work for a deadline takes; used by the planner. */
    public Schedule work(int minutes) {
        this.workMinutes = minutes;
        return this;
    }

    public Schedule priority(Priority value) {
        this.priority = value;
        return this;
    }

    public Schedule memo(String text) {
        this.memo = text;
        return this;
    }

    /** A block of work that the planner placed for one occurrence of a deadline. */
    public static Schedule workBlock(WorkTarget target, LocalDate date, LocalTime start, LocalTime end) {
        Schedule block = new Schedule("", Kind.TIMED, date, start, end, target.deadline().category());
        block.plannedFor = target;
        return block;
    }

    /** Every date on which this schedule happens between the two dates, both included. */
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

    public String id() {
        return id;
    }

    /** A work block has no title of its own; it follows the title of its deadline. */
    public String title() {
        return plannedFor == null ? title : "Work: " + plannedFor.deadline().title();
    }

    public Kind kind() {
        return kind;
    }

    public LocalDate date() {
        return date;
    }

    public LocalTime start() {
        return start;
    }

    public LocalTime end() {
        return end;
    }

    public Category category() {
        return plannedFor == null ? category : plannedFor.deadline().category();
    }

    public Priority priority() {
        return priority;
    }

    public String memo() {
        return memo;
    }

    public Repeat repeat() {
        return repeat;
    }

    public LocalDate repeatUntil() {
        return repeatUntil;
    }

    public boolean isDone(LocalDate occurrence) {
        return doneDates.contains(occurrence);
    }

    /** The user's own estimate, or -1 when the default of the category applies. */
    public int workMinutes() {
        return workMinutes;
    }

    /** The estimate that is actually used: the user's own, or the default of the category. */
    public int effectiveWorkMinutes() {
        return workMinutes >= 0 ? workMinutes : category.defaultWorkMinutes();
    }

    public WorkTarget plannedFor() {
        return plannedFor;
    }
}
