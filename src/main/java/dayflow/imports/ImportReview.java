package dayflow.imports;

import dayflow.model.Category;
import dayflow.model.Kind;
import dayflow.model.Occurrence;
import dayflow.model.Priority;
import dayflow.model.Repeat;
import dayflow.model.ScheduleDraft;
import dayflow.timetable.ClassBlock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns what an import found into the rows of the preview window: the draft to save, whether it starts
 * checked, and the warning to show. It knows nothing about windows, so it can be tested alone.
 */
public final class ImportReview {
    /** The warning of a row that is already in the schedules (R-IMP-8). */
    public static final String DUPLICATE = "Already in your schedules";

    private ImportReview() {
    }

    /**
     * One preview row. {@code note} is empty when there is nothing to warn about.
     *
     * @param draft the values of the schedule to add
     * @param checked whether the row is selected for import
     * @param note the warning to show, or empty
     */
    public record Row(ScheduleDraft draft, boolean checked, String note) {
    }

    /**
     * R-TT-6: the Monday of the week that holds today, with Sunday as the first day. On a Sunday, the next day.
     *
     * @param today the current date
     * @return the first date of the period
     */
    public static LocalDate defaultFrom(LocalDate today) {
        if (today.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return today.plusDays(1);
        }
        return today.minusDays(today.getDayOfWeek().getValue() - 1L);
    }

    /**
     * R-TT-6: the first June 20 or December 20 after {@code from}, about when a Korean semester ends.
     *
     * @param from the first date of the period
     * @return the last date of the period
     */
    public static LocalDate defaultUntil(LocalDate from) {
        LocalDate june = from.withMonth(6).withDayOfMonth(20);
        LocalDate december = from.withMonth(12).withDayOfMonth(20);
        if (from.isBefore(june)) {
            return june;
        }
        return from.isBefore(december) ? december : june.plusYears(1);
    }

    /**
     * R-TT-6, R-IMP-7, R-IMP-8 and R-IMP-9: one row per class block, in the same order. Every row starts
     * checked except a duplicate. {@code existing} are the occurrences already in the schedules, of which
     * only those on the first class date are looked at. The memo holds the room and the professor (R-IMP-9).
     *
     * @param blocks the classes read from the image
     * @param from the first date of the period
     * @param until the last date of the period
     * @param existing the occurrences already in the schedules
     * @return the rows, one per class block
     */
    public static List<Row> fromTimetable(List<ClassBlock> blocks, LocalDate from, LocalDate until,
            List<Occurrence> existing) {
        List<Row> rows = new ArrayList<>();
        for (ClassBlock b : blocks) {
            LocalDate date = firstOn(from, b.day());
            ScheduleDraft draft = new ScheduleDraft(b.name(), Kind.TIMED, date, b.start(), b.end(), Category.STUDY,
                    Priority.MEDIUM, memo(b), Repeat.WEEKLY, repeatUntil(date, from, until), -1, null);
            boolean duplicate = isDuplicate(draft, existing);
            rows.add(new Row(draft, !duplicate, duplicate ? DUPLICATE : ""));
        }
        return rows;
    }

    /**
     * The same rows for another period. Each draft keeps its title, time and memo, takes the first date of
     * its own weekday on or after {@code from}, and is compared with the schedules again. A row whose
     * duplicate state changed gets the check that goes with it, and the others keep the check they have.
     *
     * @param rows the rows to move
     * @param from the first date of the new period
     * @param until the last date of the new period
     * @param existing the occurrences already in the schedules
     * @return the moved rows
     */
    public static List<Row> rebase(List<Row> rows, LocalDate from, LocalDate until, List<Occurrence> existing) {
        List<Row> result = new ArrayList<>();
        for (Row row : rows) {
            ScheduleDraft d = row.draft();
            LocalDate date = firstOn(from, d.date().getDayOfWeek());
            ScheduleDraft moved = new ScheduleDraft(d.title(), d.kind(), date, d.start(), d.end(), d.category(),
                    d.priority(), d.memo(), d.repeat(), repeatUntil(date, from, until), d.workMinutes(),
                    d.plannedFor());
            result.add(recheck(new Row(moved, row.checked(), row.note()), existing));
        }
        return result;
    }

    /**
     * R-IMP-8 again for a row whose draft changed, for example after the user edited it. {@code existing}
     * are the occurrences on the date of the draft. A row whose duplicate state changed gets the check that
     * goes with it, and the others keep the check they have.
     *
     * @param row the row to check again
     * @param existing the occurrences on the date of the draft
     * @return the row with its note and check updated
     */
    public static Row recheck(Row row, List<Occurrence> existing) {
        boolean duplicate = isDuplicate(row.draft(), existing);
        boolean wasDuplicate = row.note().equals(DUPLICATE);
        return new Row(row.draft(), duplicate == wasDuplicate ? row.checked() : !duplicate,
                duplicate ? DUPLICATE : "");
    }

    /** R-TT-6: until is raised to from when it is earlier, and never ends before the first class. */
    private static LocalDate repeatUntil(LocalDate date, LocalDate from, LocalDate until) {
        LocalDate end = until.isBefore(from) ? from : until;
        return end.isBefore(date) ? date : end;
    }

    private static LocalDate firstOn(LocalDate from, DayOfWeek day) {
        return from.plusDays(Math.floorMod(day.getValue() - from.getDayOfWeek().getValue(), 7));
    }

    private static String memo(ClassBlock b) {
        List<String> parts = new ArrayList<>();
        if (!b.room().isBlank()) {
            parts.add("Room " + b.room().strip());
        }
        if (!b.professor().isBlank()) {
            parts.add("Prof. " + b.professor().strip());
        }
        return String.join(", ", parts);
    }

    /** R-IMP-8 for a class: same first date and start, and one title holds the other without spaces and case. */
    private static boolean isDuplicate(ScheduleDraft draft, List<Occurrence> existing) {
        String title = squash(draft.title());
        if (title.isEmpty()) {
            return false;
        }
        for (Occurrence o : existing) {
            if (o.planned() || !o.date().equals(draft.date()) || !draft.start().equals(o.start())) {
                continue;
            }
            String other = squash(o.title());
            if (!other.isEmpty() && (other.contains(title) || title.contains(other))) {
                return true;
            }
        }
        return false;
    }

    private static String squash(String text) {
        return text.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }
}
