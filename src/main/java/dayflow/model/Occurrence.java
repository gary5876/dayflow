package dayflow.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * A schedule on one concrete date. This is what the calendar, timetable, and list show.
 *
 * @param schedule the schedule this occurrence belongs to
 * @param date the date on which it happens
 */
public record Occurrence(Schedule schedule, LocalDate date) {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Returns the title of the schedule.
     *
     * @return the title of the schedule
     */
    public String title() {
        return schedule.title();
    }

    /**
     * Returns the start time, or the due time of a deadline.
     *
     * @return the start time, or the due time of a deadline
     */
    public LocalTime start() {
        return schedule.start();
    }

    /**
     * Returns the end time of the schedule.
     *
     * @return the end time of the schedule
     */
    public LocalTime end() {
        return schedule.end();
    }

    /**
     * Returns the category of the schedule.
     *
     * @return the category of the schedule
     */
    public Category category() {
        return schedule.category();
    }

    /**
     * Returns the priority of the schedule.
     *
     * @return the priority of the schedule
     */
    public Priority priority() {
        return schedule.priority();
    }

    /**
     * Returns the memo of the schedule.
     *
     * @return the memo of the schedule
     */
    public String memo() {
        return schedule.memo();
    }

    /**
     * Returns whether this is a deadline.
     *
     * @return whether this is a deadline
     */
    public boolean deadline() {
        return schedule.kind() == Kind.DEADLINE;
    }

    /**
     * Returns whether this has a start and an end time.
     *
     * @return whether this has a start and an end time
     */
    public boolean timed() {
        return schedule.kind() == Kind.TIMED;
    }

    /**
     * Returns whether this lasts the whole day.
     *
     * @return whether this lasts the whole day
     */
    public boolean allDay() {
        return schedule.kind() == Kind.ALL_DAY;
    }

    /**
     * Returns whether this occurrence is marked done.
     *
     * @return whether this occurrence is marked done
     */
    public boolean done() {
        return schedule.isDone(date);
    }

    /**
     * Returns whether the schedule repeats.
     *
     * @return whether the schedule repeats
     */
    public boolean recurring() {
        return schedule.repeat() != Repeat.NONE;
    }

    /**
     * A work block placed by the planner.
     *
     * @return whether this is a work block
     */
    public boolean planned() {
        return schedule.plannedFor() != null;
    }

    /**
     * Returns whether this is a deadline that is not done yet.
     *
     * @return whether this is a deadline that is not done yet
     */
    public boolean isOpenDeadline() {
        return deadline() && !done();
    }

    /**
     * Position within a day: all-day entries first, then by time.
     *
     * @return the time used to order the entries of a day
     */
    public LocalTime sortTime() {
        return schedule.start() == null ? LocalTime.MIN : schedule.start();
    }

    /**
     * The moment a deadline is due.
     *
     * @return the due date and time
     */
    public LocalDateTime due() {
        return date.atTime(schedule.start());
    }

    /**
     * "~18:00" for a deadline, "14:00-16:00" for a timed schedule, "All day" otherwise.
     *
     * @return the time as shown on screen
     */
    public String timeText() {
        return switch (schedule.kind()) {
            case DEADLINE -> "~" + TIME.format(start());
            case TIMED -> TIME.format(start()) + "-" + TIME.format(end());
            case ALL_DAY -> "All day";
        };
    }

    /**
     * "D-day", "D-3", or "D+1" once the date has passed.
     *
     * @param today the current date
     * @return the label
     */
    public String dDayLabel(LocalDate today) {
        long days = ChronoUnit.DAYS.between(today, date);
        return days == 0 ? "D-day" : days > 0 ? "D-" + days : "D+" + (-days);
    }
}
