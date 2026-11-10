package dayflow.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dayflow.model.Category;
import dayflow.model.Occurrence;
import dayflow.model.Schedule;
import dayflow.model.ScheduleEdits;
import dayflow.model.WorkTarget;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScheduleBookTest {
    private static final ZoneId ZONE = ZoneId.of("UTC");
    private static final LocalDate DAY = LocalDate.of(2026, 11, 20);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-20T12:00:00Z"), ZONE);

    private int saves;
    private boolean failing;
    private List<Schedule> lastSaved;

    private ScheduleBook book(Schedule... initial) {
        return new ScheduleBook(new ArrayList<>(List.of(initial)), list -> {
            saves++;
            if (failing) {
                throw new IOException("disk full");
            }
            lastSaved = new ArrayList<>(list);
        }, CLOCK);
    }

    private static Schedule item(String title) {
        return Schedule.allDay(title, DAY, Category.LIFE);
    }

    @Test
    void changeSavesOnceAndCanBeUndone() {
        ScheduleBook book = book();
        Change change = book.change("Added: A", list -> list.add(item("A")));
        assertEquals("Added: A.", change.statusText());
        assertTrue(change.canUndo());
        assertFalse(change.saveFailed());
        assertEquals(LocalDateTime.of(2026, 11, 20, 12, 0), change.savedAt());
        assertEquals(1, saves);
        assertEquals(1, book.schedules().size());
        assertEquals(change, book.lastChange().orElseThrow());

        Change undone = book.undo();
        assertEquals("Undone.", undone.statusText());
        assertFalse(undone.canUndo());
        assertTrue(book.schedules().isEmpty());
        assertEquals(2, saves);
        assertTrue(lastSaved.isEmpty());
    }

    @Test
    void undoRevertsOnlyTheSecondOfTwoChanges() {
        ScheduleBook book = book();
        book.change("Added: A", list -> list.add(item("A")));
        book.change("Added: B", list -> list.add(item("B")));
        book.undo();
        assertEquals(1, book.schedules().size());
        assertEquals("A", book.schedules().get(0).title());
        assertFalse(book.undo().canUndo());
        assertEquals(1, book.schedules().size());
    }

    @Test
    void undoWithNothingToUndoSaysNothing() {
        Change change = book().undo();
        assertEquals("", change.statusText());
        assertFalse(change.canUndo());
    }

    @Test
    void markingDoneRemovesOnlyUnstartedBlocks() {
        Schedule deadline = Schedule.deadline("Report", DAY, "18:00", Category.STUDY);
        WorkTarget target = new WorkTarget(deadline, DAY);
        Schedule started = Schedule.workBlock(target, DAY, LocalTime.of(9, 0), LocalTime.of(10, 0));
        Schedule inProgress = Schedule.workBlock(target, DAY, LocalTime.of(11, 30), LocalTime.of(12, 30));
        Schedule later1 = Schedule.workBlock(target, DAY, LocalTime.of(13, 0), LocalTime.of(14, 0));
        Schedule later2 = Schedule.workBlock(target, DAY, LocalTime.of(15, 0), LocalTime.of(16, 0));
        ScheduleBook book = book(deadline, started, inProgress, later1, later2);

        Change change = book.change("Marked done: Report",
                list -> ScheduleEdits.setDone(list, new Occurrence(list.get(0), DAY), true));
        assertEquals("Marked done: Report. 2 work blocks removed.", change.statusText());
        assertEquals(3, book.schedules().size());

        book.undo();
        assertEquals(5, book.schedules().size());
        assertFalse(book.schedules().get(0).isDone(DAY));
    }

    @Test
    void oneBlockIsSingular() {
        Schedule deadline = Schedule.deadline("Report", DAY, "18:00", Category.STUDY);
        Schedule later = Schedule.workBlock(new WorkTarget(deadline, DAY), DAY, LocalTime.of(15, 0),
                LocalTime.of(16, 0));
        Change change = book(deadline, later).change("Marked done: Report",
                list -> ScheduleEdits.setDone(list, new Occurrence(list.get(0), DAY), true));
        assertEquals("Marked done: Report. 1 work block removed.", change.statusText());
    }

    @Test
    void removingDoneMarksAndBlocksTogetherCountsBoth() {
        Schedule deadline = Schedule.deadline("Rent", DAY, "18:00", Category.LIFE).setDone(DAY, true);
        Schedule later = Schedule.workBlock(new WorkTarget(deadline, DAY), DAY, LocalTime.of(15, 0),
                LocalTime.of(16, 0));
        Change change = book(deadline, later).change("Updated: Rent", list -> {
            ScheduleEdits.updateAll(list, list.get(0), Schedule.allDay("Rent", DAY, Category.LIFE).toDraft());
        });
        assertEquals("Updated: Rent. 1 done mark and 1 work block removed.", change.statusText());
    }

    @Test
    void blocksThatBreakTheRulesAreRemoved() {
        Schedule deadline = Schedule.deadline("D", DAY, "18:00", Category.JOB);
        Schedule block = Schedule.workBlock(new WorkTarget(deadline, DAY), DAY.plusDays(1), LocalTime.of(9, 0),
                LocalTime.of(10, 0));
        ScheduleBook book = book(deadline, block);
        // An edit that removes the deadline only, leaving the block with nothing to point at.
        book.change("Deleted: D", list -> list.remove(0));
        assertTrue(book.schedules().isEmpty());
    }

    @Test
    void failedSaveKeepsDataAndIsRetriedOnNextChange() {
        ScheduleBook book = book();
        failing = true;
        Change change = book.change("Added: A", list -> list.add(item("A")));
        assertTrue(change.saveFailed());
        assertNull(change.savedAt());
        assertTrue(change.canUndo());
        assertEquals(1, book.schedules().size());
        assertFalse(book.close());

        failing = false;
        Change next = book.change("Added: B", list -> list.add(item("B")));
        assertFalse(next.saveFailed());
        assertNotNull(next.savedAt());
        assertEquals(2, lastSaved.size());
        assertTrue(book.close());
    }

    @Test
    void listenersHearChangeAndUndo() {
        ScheduleBook book = book();
        List<String> heard = new ArrayList<>();
        book.addListener(c -> heard.add(c.statusText()));
        book.change("Added: A", list -> list.add(item("A")));
        book.undo();
        assertEquals(List.of("Added: A.", "Undone."), heard);
    }

    @Test
    void occurrencesAreSortedByDateThenTime() {
        Schedule late = Schedule.timed("Late", DAY, "15:00", "16:00", Category.LIFE);
        Schedule early = Schedule.timed("Early", DAY, "08:00", "09:00", Category.LIFE);
        Schedule next = Schedule.allDay("Next", DAY.plusDays(1), Category.LIFE);
        Schedule allDay = item("All");
        List<Occurrence> found = book(next, late, early, allDay).occurrences(DAY, DAY.plusDays(1));
        assertEquals(List.of("All", "Early", "Late", "Next"), found.stream().map(Occurrence::title).toList());
    }

    @Test
    void reopeningHasNoCountSuffix() {
        Schedule deadline = Schedule.deadline("X", DAY, "18:00", Category.LIFE).setDone(DAY, true);
        Change change = book(deadline).changeWithoutCount("Reopened: X",
                list -> ScheduleEdits.setDone(list, new Occurrence(list.get(0), DAY), false));
        assertEquals("Reopened: X.", change.statusText());
        assertTrue(change.canUndo());
    }

    @Test
    void clearingBlocksWithoutCountGivesPlainText() {
        Schedule deadline = Schedule.deadline("X", DAY, "18:00", Category.LIFE);
        WorkTarget target = new WorkTarget(deadline, DAY);
        Schedule b1 = Schedule.workBlock(target, DAY, LocalTime.of(14, 0), LocalTime.of(15, 0));
        Schedule b2 = Schedule.workBlock(target, DAY, LocalTime.of(16, 0), LocalTime.of(17, 0));
        ScheduleBook book = book(deadline, b1, b2);
        Change change = book.changeWithoutCount("Plan cleared", list -> list.removeIf(s -> s.plannedFor() != null));
        assertEquals("Plan cleared.", change.statusText());
        assertEquals(1, book.schedules().size());
    }

    @Test
    void markingDoneWithoutCountStillRemovesUnstartedBlocks() {
        Schedule deadline = Schedule.deadline("X", DAY, "18:00", Category.LIFE);
        Schedule later = Schedule.workBlock(new WorkTarget(deadline, DAY), DAY, LocalTime.of(15, 0),
                LocalTime.of(16, 0));
        ScheduleBook book = book(deadline, later);
        Change change = book.changeWithoutCount("Marked done: X",
                list -> ScheduleEdits.setDone(list, new Occurrence(list.get(0), DAY), true));
        assertEquals("Marked done: X.", change.statusText());
        assertEquals(1, book.schedules().size());
    }

    @Test
    void aThrowingEditLeavesSchedulesAndLastChangeUnchanged() {
        ScheduleBook book = book(item("A"));
        Change first = book.change("Added: B", list -> list.add(item("B")));
        List<String> before = book.schedules().stream().map(Schedule::id).toList();
        assertThrows(IllegalStateException.class, () -> book.change("Broken", list -> {
            list.clear();
            throw new IllegalStateException("boom");
        }));
        assertEquals(before, book.schedules().stream().map(Schedule::id).toList());
        assertEquals(first, book.lastChange().orElseThrow());
    }

    @Test
    void aSaverThatThrowsARuntimeExceptionCountsAsAFailedSave() {
        ScheduleBook book = new ScheduleBook(new ArrayList<>(), list -> {
            throw new UnsupportedOperationException("bad saver");
        }, CLOCK);
        Change change = book.change("Added: A", list -> list.add(item("A")));
        assertTrue(change.saveFailed());
        assertFalse(book.close());
    }

    @Test
    void loadingRemovesABlockWhoseDateIsSkippedAndSavesNothing() {
        Schedule deadline = Schedule.deadline("Rent", DAY, "18:00", Category.LIFE)
                .repeat(dayflow.model.Repeat.WEEKLY, DAY.plusWeeks(4)).skip(DAY.plusWeeks(1));
        Schedule fine = Schedule.workBlock(new WorkTarget(deadline, DAY), DAY.minusDays(1), LocalTime.of(9, 0),
                LocalTime.of(10, 0));
        Schedule broken = Schedule.workBlock(new WorkTarget(deadline, DAY.plusWeeks(1)), DAY, LocalTime.of(9, 0),
                LocalTime.of(10, 0));
        ScheduleBook book = book(deadline, fine, broken);
        assertEquals(List.of(deadline.id(), fine.id()), book.schedules().stream().map(Schedule::id).toList());
        assertEquals(0, saves);
    }
}
