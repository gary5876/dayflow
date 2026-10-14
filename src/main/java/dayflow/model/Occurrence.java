package dayflow.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/** A schedule on one concrete date. This is what the calendar, timetable, and list show. */
public record Occurrence(Schedule schedule, LocalDate date) {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    public String title() {
        return schedule.title();
    }

    public LocalTime start() {
        return schedule.start();
    }

    public LocalTime end() {
        return schedule.end();
    }

    public Category category() {
        return schedule.category();
    }

    public Priority priority() {
        return schedule.priority();
    }

    public String memo() {
        return schedule.memo();
    }

    public boolean deadline() {
        return schedule.kind() == Kind.DEADLINE;
    }

    public boolean timed() {
        return schedule.kind() == Kind.TIMED;
    }

    public boolean allDay() {
        return schedule.kind() == Kind.ALL_DAY;
    }

    public boolean done() {
        return schedule.isDone(date);
    }

    public boolean recurring() {
        return schedule.repeat() != Repeat.NONE;
    }

    /** A work block placed by the planner. */
    public boolean planned() {
        return schedule.plannedFor() != null;
    }

    public boolean isOpenDeadline() {
        return deadline() && !done();
    }

    /** Position within a day: all-day entries first, then by time. */
    public LocalTime sortTime() {
        return schedule.start() == null ? LocalTime.MIN : schedule.start();
    }

    /** The moment a deadline is due. */
    public LocalDateTime due() {
        return date.atTime(schedule.start());
    }

    /** "~18:00" for a deadline, "14:00-16:00" for a timed schedule, "All day" otherwise. */
    public String timeText() {
        return switch (schedule.kind()) {
            case DEADLINE -> "~" + TIME.format(start());
            case TIMED -> TIME.format(start()) + "-" + TIME.format(end());
            case ALL_DAY -> "All day";
        };
    }

    /** "D-day", "D-3", or "D+1" once the date has passed. */
    public String dDayLabel(LocalDate today) {
        long days = ChronoUnit.DAYS.between(today, date);
        return days == 0 ? "D-day" : days > 0 ? "D-" + days : "D+" + (-days);
    }
}
