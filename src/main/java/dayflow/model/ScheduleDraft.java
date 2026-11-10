package dayflow.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

/**
 * The values of a schedule that has not been saved yet: what the input window returns, what an import
 * finds, and what a preview row holds. It is a {@link Schedule} without id, done dates, and skipped dates.
 *
 * @param title the title
 * @param kind the kind
 * @param date the date
 * @param start the start time, or the due time of a deadline
 * @param end the end time
 * @param category the category
 * @param priority the priority
 * @param memo the memo
 * @param repeat the repeat rule
 * @param repeatUntil the last date of the series
 * @param workMinutes the work estimate in minutes, or -1
 * @param plannedFor the deadline occurrence of a work block, or null
 */
public record ScheduleDraft(String title, Kind kind, LocalDate date, LocalTime start, LocalTime end,
        Category category, Priority priority, String memo, Repeat repeat, LocalDate repeatUntil,
        int workMinutes, WorkTarget plannedFor) {
    /** The longest title the input window accepts (R-FORM-4). */
    public static final int MAX_TITLE = 200;

    /**
     * Empty when the values may be saved. Otherwise the first problem to show on screen (I-1 to I-5, I-9).
     *
     * @return the problem text, if any
     */
    public Optional<String> problem() {
        if (title == null || title.strip().isEmpty()) {
            return Optional.of("Title is empty");
        }
        if (kind == null || date == null) {
            return Optional.of("Kind and date are required");
        }
        switch (kind) {
            case TIMED -> {
                if (start == null || end == null || !end.isAfter(start)) {
                    return Optional.of("End must be after start");
                }
            }
            case DEADLINE -> {
                if (start == null) {
                    return Optional.of("Deadline needs a due time");
                }
                if (end != null) {
                    return Optional.of("Deadline has no end time");
                }
            }
            case ALL_DAY -> {
                if (start != null || end != null) {
                    return Optional.of("All day has no time");
                }
            }
        }
        if (repeat != Repeat.NONE) {
            if (repeatUntil == null) {
                return Optional.of("Repeat needs an until date");
            }
            if (repeatUntil.isBefore(date)) {
                return Optional.of("Until is before the date");
            }
            if (plannedFor != null) {
                return Optional.of("A work block does not repeat");
            }
        }
        return Optional.empty();
    }

    /**
     * A copy with the title trimmed and cut at 200 characters (R-FORM-4).
     *
     * @return the cleaned copy
     */
    public ScheduleDraft normalized() {
        String trimmed = title == null ? "" : title.strip();
        if (trimmed.length() > MAX_TITLE) {
            trimmed = trimmed.substring(0, MAX_TITLE);
        }
        return new ScheduleDraft(trimmed, kind, date, start, end, category, priority, memo, repeat, repeatUntil,
                workMinutes, plannedFor);
    }

    /**
     * A new schedule with a new id and no records.
     *
     * @return the new schedule
     */
    public Schedule toSchedule() {
        return Schedule.create(this);
    }
}
