package dayflow.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ScheduleDraftTest {
    private static final LocalDate DAY = LocalDate.of(2026, 10, 20);

    private static ScheduleDraft timed(String title, String start, String end) {
        return new ScheduleDraft(title, Kind.TIMED, DAY, LocalTime.parse(start), LocalTime.parse(end), Category.STUDY,
                Priority.MEDIUM, "", Repeat.NONE, null, -1, null);
    }

    private static ScheduleDraft deadline(LocalTime end) {
        return new ScheduleDraft("Report", Kind.DEADLINE, DAY, LocalTime.of(18, 0), end, Category.JOB,
                Priority.HIGH, "memo", Repeat.NONE, null, 90, null);
    }

    @Test
    void blankTitleIsAProblem() {
        assertTrue(timed("  ", "10:00", "11:00").problem().isPresent());
    }

    @Test
    void timedEndMustBeAfterStart() {
        assertTrue(timed("A", "10:00", "10:00").problem().isPresent());
        assertTrue(timed("A", "10:00", "09:00").problem().isPresent());
    }

    @Test
    void deadlineMustNotHaveAnEnd() {
        assertTrue(deadline(LocalTime.of(19, 0)).problem().isPresent());
    }

    @Test
    void repeatNeedsAnUntilDateNotBeforeTheDate() {
        ScheduleDraft none = new ScheduleDraft("A", Kind.ALL_DAY, DAY, null, null, Category.LIFE, Priority.LOW, "",
                Repeat.WEEKLY, null, -1, null);
        assertTrue(none.problem().isPresent());
        ScheduleDraft before = new ScheduleDraft("A", Kind.ALL_DAY, DAY, null, null, Category.LIFE, Priority.LOW, "",
                Repeat.WEEKLY, DAY.minusDays(1), -1, null);
        assertTrue(before.problem().isPresent());
    }

    @Test
    void workBlockMustNotRepeat() {
        Schedule deadline = Schedule.deadline("D", DAY, "18:00", Category.JOB);
        ScheduleDraft block = new ScheduleDraft("Write intro", Kind.TIMED, DAY, LocalTime.of(9, 0),
                LocalTime.of(10, 0), Category.JOB, Priority.MEDIUM, "", Repeat.DAILY, DAY.plusDays(3), -1, new WorkTarget(deadline, DAY));
        assertEquals("A work block does not repeat", block.problem().orElseThrow());
    }

    @Test
    void validDraftsHaveNoProblem() {
        assertTrue(timed("A", "10:00", "11:00").problem().isEmpty());
        assertTrue(deadline(null).problem().isEmpty());
        ScheduleDraft allDay = new ScheduleDraft("A", Kind.ALL_DAY, DAY, null, null, Category.LIFE, Priority.LOW, "",
                Repeat.NONE, null, -1, null);
        assertTrue(allDay.problem().isEmpty());
    }

    @Test
    void normalizedTrimsAndCutsTheTitleAt200() {
        ScheduleDraft draft = timed("  " + "x".repeat(201) + "  ", "10:00", "11:00");
        String title = draft.normalized().title();
        assertEquals(200, title.length());
        assertEquals("x".repeat(200), title);
    }

    @Test
    void restoreKeepsTheIdAndDraftRoundTrips() {
        ScheduleDraft values = deadline(null);
        Schedule restored = Schedule.restore("abc", values, Set.of(DAY), Set.of());
        assertEquals("abc", restored.id());
        assertTrue(restored.isDone(DAY));
        assertEquals(values, restored.toDraft());
        assertEquals(values, values.toSchedule().toDraft());
    }

    @Test
    void updateKeepsIdAndRecords() {
        Schedule schedule = Schedule.restore("abc", deadline(null), Set.of(DAY), Set.of(DAY.plusDays(7)));
        ScheduleDraft changed = timed("Other", "10:00", "11:00");
        schedule.update(changed);
        assertEquals("abc", schedule.id());
        assertEquals(changed, schedule.toDraft());
        assertTrue(schedule.isDone(DAY));
        assertEquals(Set.of(DAY.plusDays(7)), schedule.skippedDates());
    }
}
