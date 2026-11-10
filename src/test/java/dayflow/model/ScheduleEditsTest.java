package dayflow.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dayflow.plan.WorkBlock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ScheduleEditsTest {
    private static final LocalDate D20 = LocalDate.of(2026, 10, 20);
    private static final LocalDate D22 = LocalDate.of(2026, 10, 22);
    private static final LocalDate D27 = LocalDate.of(2026, 10, 27);
    private final List<Schedule> all = new ArrayList<>();

    private Schedule add(Schedule schedule) {
        all.add(schedule);
        return schedule;
    }

    private Schedule block(Schedule deadline, LocalDate target, LocalDate date, String start, String end) {
        return add(Schedule.workBlock(new WorkTarget(deadline, target), date, LocalTime.parse(start),
                LocalTime.parse(end)));
    }

    private static ScheduleDraft with(Schedule s, Kind kind, LocalDate date, Repeat repeat, LocalDate until) {
        ScheduleDraft d = s.toDraft();
        LocalTime start = kind == Kind.ALL_DAY ? null : LocalTime.of(18, 0);
        LocalTime end = kind == Kind.TIMED ? LocalTime.of(19, 0) : null;
        return new ScheduleDraft(d.title(), kind, date, start, end, d.category(), d.priority(), d.memo(), repeat, until,
                d.workMinutes(), null);
    }

    @Test
    void addPutsANewScheduleInTheList() {
        ScheduleDraft draft = new ScheduleDraft("A", Kind.ALL_DAY, D20, null, null, Category.LIFE, Priority.LOW, "",
                Repeat.NONE, null, -1, null);
        Schedule created = ScheduleEdits.add(all, draft);
        assertEquals(List.of(created), all);
        assertEquals(draft, created.toDraft());
    }

    @Test
    void updateOneSplitsOffAnOccurrence() {
        Schedule weekly = add(Schedule.deadline("Rent", D20.minusDays(7), "18:00", Category.LIFE)
                .repeat(Repeat.WEEKLY, D20.plusDays(21)));
        weekly.setDone(D20, true);
        Schedule mine = block(weekly, D20, D20, "09:00", "10:00");
        Schedule other = block(weekly, D27, D27, "09:00", "10:00");
        Occurrence occurrence = new Occurrence(weekly, D20);

        ScheduleDraft values = with(weekly, Kind.DEADLINE, D22, Repeat.NONE, null);
        Schedule created = ScheduleEdits.updateOne(all, occurrence, values);

        assertTrue(weekly.skippedDates().contains(D20));
        assertEquals(Repeat.NONE, created.repeat());
        assertTrue(all.contains(created));
        assertTrue(created.isDone(D22));
        assertFalse(weekly.isDone(D20));
        assertSame(created, mine.plannedFor().deadline());
        assertEquals(D22, mine.plannedFor().date());
        assertSame(weekly, other.plannedFor().deadline());
    }

    @Test
    void updateAllMovesRecordsOfASingleOccurrence() {
        Schedule deadline = add(Schedule.deadline("Report", D20, "18:00", Category.JOB));
        deadline.setDone(D20, true);
        Schedule b = block(deadline, D20, D20, "09:00", "10:00");

        ScheduleEdits.updateAll(all, deadline, with(deadline, Kind.DEADLINE, D22, Repeat.NONE, null));

        assertEquals(Set.of(D22), deadline.doneDates());
        assertEquals(D22, b.plannedFor().date());
        assertEquals(D20, b.date());
        assertTrue(all.contains(b));
    }

    @Test
    void updateAllDropsRecordsThatAreNoLongerOccurrences() {
        LocalDate first = D20;
        Schedule weekly = add(Schedule.deadline("Rent", first, "18:00", Category.LIFE)
                .repeat(Repeat.WEEKLY, first.plusDays(28)));
        weekly.setDone(first, true);
        weekly.setDone(first.plusDays(7), true);
        Schedule b0 = block(weekly, first, first, "09:00", "10:00");

        ScheduleEdits.updateAll(all, weekly,
                with(weekly, Kind.DEADLINE, first.plusDays(1), Repeat.WEEKLY, first.plusDays(28)));

        assertTrue(weekly.doneDates().isEmpty());
        assertFalse(all.contains(b0));
    }

    @Test
    void updateAllKeepsRecordsThatStayOccurrences() {
        Schedule weekly = add(Schedule.deadline("Rent", D20, "18:00", Category.LIFE)
                .repeat(Repeat.WEEKLY, D20.plusDays(28)));
        weekly.setDone(D27, true);
        Schedule b = block(weekly, D27, D27, "09:00", "10:00");

        ScheduleEdits.updateAll(all, weekly, with(weekly, Kind.DEADLINE, D20, Repeat.WEEKLY, D20.plusDays(14)));

        assertEquals(Set.of(D27), weekly.doneDates());
        assertTrue(all.contains(b));
    }

    @Test
    void updateAllToAnotherKindClearsDoneAndBlocks() {
        Schedule deadline = add(Schedule.deadline("Report", D20, "18:00", Category.JOB));
        deadline.setDone(D20, true);
        Schedule b = block(deadline, D20, D20, "09:00", "10:00");

        ScheduleEdits.updateAll(all, deadline, with(deadline, Kind.TIMED, D20, Repeat.NONE, null));

        assertTrue(deadline.doneDates().isEmpty());
        assertFalse(all.contains(b));
        assertEquals(Kind.TIMED, deadline.kind());
    }

    @Test
    void deleteAllRemovesTheScheduleAndAllItsBlocks() {
        Schedule deadline = add(Schedule.deadline("Report", D20, "18:00", Category.JOB));
        Schedule started = block(deadline, D20, D20.minusDays(5), "09:00", "10:00");
        Schedule coming = block(deadline, D20, D20, "09:00", "10:00");
        Schedule unrelated = add(Schedule.allDay("X", D20, Category.LIFE));

        ScheduleEdits.deleteAll(all, deadline);

        assertEquals(List.of(unrelated), all);
        assertFalse(all.contains(started) || all.contains(coming));
    }

    @Test
    void deleteOneSkipsTheDateAndRemovesOnlyItsBlocks() {
        Schedule weekly = add(Schedule.deadline("Rent", D20, "18:00", Category.LIFE)
                .repeat(Repeat.WEEKLY, D20.plusDays(28)));
        weekly.setDone(D20, true);
        Schedule mine = block(weekly, D20, D20, "09:00", "10:00");
        Schedule other = block(weekly, D27, D27, "09:00", "10:00");

        ScheduleEdits.deleteOne(all, new Occurrence(weekly, D20));

        assertTrue(weekly.skippedDates().contains(D20));
        assertFalse(weekly.isDone(D20));
        assertFalse(all.contains(mine));
        assertTrue(all.contains(other));
        assertTrue(all.contains(weekly));
    }

    @Test
    void setDoneAndPriorityChangeTheSchedule() {
        Schedule deadline = add(Schedule.deadline("Report", D20, "18:00", Category.JOB));
        ScheduleEdits.setDone(all, new Occurrence(deadline, D20), true);
        assertTrue(deadline.isDone(D20));
        ScheduleEdits.setDone(all, new Occurrence(deadline, D20), false);
        assertFalse(deadline.isDone(D20));
        ScheduleEdits.setPriority(deadline, Priority.HIGH);
        assertEquals(Priority.HIGH, deadline.priority());
    }

    @Test
    void replacePlanReplacesOnlyUnstartedBlocksInThePeriod() {
        LocalDate due = LocalDate.of(2026, 11, 20);
        Schedule deadline = add(Schedule.deadline("Report", due, "18:00", Category.JOB));
        LocalDateTime now = LocalDateTime.of(2026, 10, 21, 12, 0);
        LocalDate from = LocalDate.of(2026, 10, 18);
        LocalDate until = LocalDate.of(2026, 10, 24);
        Schedule past = block(deadline, due, D20, "09:00", "10:00");
        Schedule inProgress = block(deadline, due, D22.minusDays(1), "11:00", "13:00");
        Schedule coming = block(deadline, due, D22, "09:00", "10:00");
        Schedule outside = block(deadline, due, D27, "09:00", "10:00");

        WorkBlock fresh = new WorkBlock(new Occurrence(deadline, due), D22, LocalTime.of(14, 0), LocalTime.of(16, 0));
        ScheduleEdits.replacePlan(all, now, from, until, List.of(fresh));

        assertTrue(all.contains(past));
        assertTrue(all.contains(inProgress));
        assertFalse(all.contains(coming));
        assertTrue(all.contains(outside));
        Schedule added = all.get(all.size() - 1);
        assertSame(deadline, added.plannedFor().deadline());
        assertEquals(due, added.plannedFor().date());
        assertEquals(D22, added.date());
        assertEquals(LocalTime.of(14, 0), added.start());
        assertEquals(LocalTime.of(16, 0), added.end());
    }

    @Test
    void clearPlanRemovesUnstartedBlocksOfTheWeekOnly() {
        LocalDate due = LocalDate.of(2026, 11, 20);
        Schedule deadline = add(Schedule.deadline("Report", due, "18:00", Category.JOB));
        LocalDateTime now = LocalDateTime.of(2026, 10, 21, 12, 0);
        LocalDate sunday = LocalDate.of(2026, 10, 18);
        Schedule past = block(deadline, due, D20, "09:00", "10:00");
        Schedule coming = block(deadline, due, D22, "09:00", "10:00");
        Schedule saturday = block(deadline, due, LocalDate.of(2026, 10, 24), "09:00", "10:00");
        Schedule nextWeek = block(deadline, due, D27, "09:00", "10:00");

        ScheduleEdits.clearPlan(all, now, sunday);

        assertTrue(all.contains(past));
        assertFalse(all.contains(coming));
        assertFalse(all.contains(saturday));
        assertTrue(all.contains(nextWeek));
    }
}
