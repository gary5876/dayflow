package dayflow.imports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dayflow.model.Category;
import dayflow.model.Kind;
import dayflow.model.Occurrence;
import dayflow.model.Priority;
import dayflow.model.Repeat;
import dayflow.model.Schedule;
import dayflow.timetable.ClassBlock;
import java.awt.Color;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ImportReviewTest {
    private static final LocalDate FROM = LocalDate.of(2026, 10, 26);
    private static final LocalDate UNTIL = LocalDate.of(2026, 12, 20);

    private static ClassBlock tuesday(String name) {
        return new ClassBlock(DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(10, 15), name, "Kim", "301",
                Color.CYAN);
    }

    private static List<Occurrence> existing(Schedule s) {
        return s.occurrences(FROM, FROM.plusDays(6));
    }

    @Test
    void defaultFromIsTheMondayOfTheWeekAndTheNextDayOnSunday() {
        assertEquals(LocalDate.of(2026, 10, 26), ImportReview.defaultFrom(LocalDate.of(2026, 10, 29)));
        assertEquals(LocalDate.of(2026, 10, 26), ImportReview.defaultFrom(LocalDate.of(2026, 10, 26)));
        assertEquals(LocalDate.of(2026, 10, 26), ImportReview.defaultFrom(LocalDate.of(2026, 11, 1).minusDays(6)));
        assertEquals(LocalDate.of(2026, 11, 2), ImportReview.defaultFrom(LocalDate.of(2026, 11, 1)));
    }

    @Test
    void defaultUntilIsTheNextJune20OrDecember20() {
        assertEquals(LocalDate.of(2026, 12, 20), ImportReview.defaultUntil(LocalDate.of(2026, 10, 26)));
        assertEquals(LocalDate.of(2027, 6, 20), ImportReview.defaultUntil(LocalDate.of(2027, 1, 4)));
        assertEquals(LocalDate.of(2026, 6, 20), ImportReview.defaultUntil(LocalDate.of(2026, 3, 2)));
        assertEquals(LocalDate.of(2027, 6, 20), ImportReview.defaultUntil(LocalDate.of(2026, 12, 21)));
        assertEquals(LocalDate.of(2027, 6, 20), ImportReview.defaultUntil(LocalDate.of(2026, 12, 20)));
    }

    @Test
    void aClassBecomesAWeeklyTimedStudyDraftOnTheFirstMatchingDay() {
        List<ImportReview.Row> rows = ImportReview.fromTimetable(List.of(tuesday("Algorithms")), FROM, UNTIL,
                List.of());

        assertEquals(1, rows.size());
        ImportReview.Row row = rows.get(0);
        assertTrue(row.checked());
        assertEquals("", row.note());
        assertEquals("Algorithms", row.draft().title());
        assertEquals(Category.STUDY, row.draft().category());
        assertEquals(Kind.TIMED, row.draft().kind());
        assertEquals(Repeat.WEEKLY, row.draft().repeat());
        assertEquals(LocalDate.of(2026, 10, 27), row.draft().date());
        assertEquals(LocalTime.of(9, 0), row.draft().start());
        assertEquals(LocalTime.of(10, 15), row.draft().end());
        assertEquals(UNTIL, row.draft().repeatUntil());
        assertEquals(Priority.MEDIUM, row.draft().priority());
        assertEquals("Room 301, Prof. Kim", row.draft().memo());
        assertTrue(row.draft().problem().isEmpty());
    }

    @Test
    void aClassOnTheFromDayStartsThatDay() {
        ClassBlock monday = new ClassBlock(DayOfWeek.MONDAY, LocalTime.of(13, 0), LocalTime.of(14, 0), "Math",
                "", "", Color.RED);
        ImportReview.Row row = ImportReview.fromTimetable(List.of(monday), FROM, UNTIL, List.of()).get(0);
        assertEquals(FROM, row.draft().date());
        assertEquals("", row.draft().memo());
    }

    @Test
    void anUntilBeforeFromIsRaisedToFrom() {
        ClassBlock monday = new ClassBlock(DayOfWeek.MONDAY, LocalTime.of(13, 0), LocalTime.of(14, 0), "Math",
                "", "", Color.RED);
        ImportReview.Row row = ImportReview.fromTimetable(List.of(monday), FROM, FROM.minusDays(30), List.of()).get(0);
        assertEquals(FROM, row.draft().repeatUntil());
        assertTrue(row.draft().problem().isEmpty());
    }

    @Test
    void aClassAlreadyThereAtTheSameTimeIsMarkedAndUnchecked() {
        Schedule same = Schedule.timed("algorithms 1", LocalDate.of(2026, 10, 27), "09:00", "10:15", Category.STUDY)
                .repeat(Repeat.WEEKLY, UNTIL);
        List<ImportReview.Row> rows = ImportReview.fromTimetable(
                List.of(tuesday("Algorithms"), tuesday("Databases")), FROM, UNTIL, existing(same));

        assertFalse(rows.get(0).checked());
        assertEquals("Already in your schedules", rows.get(0).note());
        assertTrue(rows.get(1).checked());
        assertEquals("", rows.get(1).note());
    }

    @Test
    void titlesAreComparedWithoutSpacesAndCaseInEitherDirection() {
        Schedule longer = Schedule.timed("Data  Structures Lab", LocalDate.of(2026, 10, 27), "09:00", "10:15",
                Category.STUDY);
        assertFalse(ImportReview.fromTimetable(List.of(tuesday("datastructures")), FROM, UNTIL, existing(longer)).get(0).checked());
        Schedule shorter = Schedule.timed("Data", LocalDate.of(2026, 10, 27), "09:00", "10:15", Category.STUDY);
        assertFalse(ImportReview.fromTimetable(List.of(tuesday("Data Structures")), FROM, UNTIL, existing(shorter)).get(0).checked());
    }

    @Test
    void aDifferentStartTimeOrANextWeekDateIsNotADuplicate() {
        Schedule later = Schedule.timed("Algorithms", LocalDate.of(2026, 10, 27), "10:00", "11:15", Category.STUDY);
        assertTrue(ImportReview.fromTimetable(List.of(tuesday("Algorithms")), FROM, UNTIL, existing(later))
                .get(0).checked());
        Schedule nextWeek = Schedule.timed("Algorithms", LocalDate.of(2026, 11, 3), "09:00", "10:15", Category.STUDY);
        assertTrue(ImportReview.fromTimetable(List.of(tuesday("Algorithms")), FROM, UNTIL,
                nextWeek.occurrences(FROM, FROM.plusDays(14))).get(0).checked());
    }

    @Test
    void aWorkBlockIsNeverADuplicate() {
        Schedule deadline = Schedule.deadline("Algorithms", LocalDate.of(2026, 11, 20), "17:00", Category.STUDY);
        Schedule block = Schedule.workBlock(new dayflow.model.WorkTarget(deadline, deadline.date()),
                LocalDate.of(2026, 10, 27), LocalTime.of(9, 0), LocalTime.of(10, 15));
        assertTrue(ImportReview.fromTimetable(List.of(tuesday("Algorithms")), FROM, UNTIL, existing(block))
                .get(0).checked());
    }

    @Test
    void aClassWithoutTextHasAnEmptyTitleAndIsNeverADuplicate() {
        Schedule any = Schedule.timed("Algorithms", LocalDate.of(2026, 10, 27), "09:00", "10:15", Category.STUDY);
        ImportReview.Row row = ImportReview.fromTimetable(List.of(tuesday("")), FROM, UNTIL, existing(any))
                .get(0);
        assertTrue(row.checked());
        assertTrue(row.draft().problem().isPresent());
    }

    @Test
    void rebasingMovesTheDatesAndRechecksDuplicates() {
        List<ImportReview.Row> rows = ImportReview.fromTimetable(List.of(tuesday("Algorithms")), FROM, UNTIL,
                List.of());
        LocalDate nextFrom = LocalDate.of(2026, 11, 2);
        Schedule there = Schedule.timed("Algorithms", LocalDate.of(2026, 11, 3), "09:00", "10:15", Category.STUDY);
        List<ImportReview.Row> moved = ImportReview.rebase(rows, nextFrom, LocalDate.of(2027, 1, 30),
                there.occurrences(nextFrom, nextFrom.plusDays(6)));

        assertEquals(LocalDate.of(2026, 11, 3), moved.get(0).draft().date());
        assertEquals(LocalDate.of(2027, 1, 30), moved.get(0).draft().repeatUntil());
        assertFalse(moved.get(0).checked());
        assertEquals("Already in your schedules", moved.get(0).note());
    }

    @Test
    void theMemoLeavesOutTheValueThatIsMissing() {
        ClassBlock roomOnly = new ClassBlock(DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(10, 0), "A", "",
                "301", Color.RED);
        ClassBlock professorOnly = new ClassBlock(DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(10, 0), "B",
                "Kim", " ", Color.RED);
        List<ImportReview.Row> rows = ImportReview.fromTimetable(List.of(roomOnly, professorOnly), FROM, UNTIL,
                List.of());
        assertEquals("Room 301", rows.get(0).draft().memo());
        assertEquals("Prof. Kim", rows.get(1).draft().memo());
    }

    @Test
    void recheckingAnEditedRowMarksAndUnchecksItWhenItBecameADuplicate() {
        ImportReview.Row row = ImportReview.fromTimetable(List.of(tuesday("Algorithms")), FROM, UNTIL, List.of())
                .get(0);
        Schedule there = Schedule.timed("Algorithms", LocalDate.of(2026, 10, 27), "09:00", "10:15", Category.STUDY);

        ImportReview.Row marked = ImportReview.recheck(row, existing(there));
        assertFalse(marked.checked());
        assertEquals("Already in your schedules", marked.note());

        ImportReview.Row cleared = ImportReview.recheck(marked, List.of());
        assertTrue(cleared.checked());
        assertEquals("", cleared.note());
    }

    @Test
    void recheckingKeepsTheCheckOfARowWhoseStateDidNotChange() {
        ImportReview.Row row = ImportReview.fromTimetable(List.of(tuesday("Algorithms")), FROM, UNTIL, List.of())
                .get(0);
        ImportReview.Row off = new ImportReview.Row(row.draft(), false, "");
        assertFalse(ImportReview.recheck(off, List.of()).checked());
    }
}
