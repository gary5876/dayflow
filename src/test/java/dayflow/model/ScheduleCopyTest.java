package dayflow.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScheduleCopyTest {
    private static final LocalDate DAY = LocalDate.of(2026, 10, 20);

    @Test
    void copyAllReconnectsWorkBlocksToTheCopiedDeadline() {
        Schedule deadline = Schedule.deadline("D", DAY, "18:00", Category.JOB);
        Schedule block = Schedule.workBlock(new WorkTarget(deadline, DAY), DAY, LocalTime.of(9, 0), LocalTime.of(10, 0));
        List<Schedule> copy = Schedule.copyAll(List.of(deadline, block));

        Schedule deadlineCopy = copy.get(0);
        Schedule blockCopy = copy.get(1);
        assertNotSame(deadline, deadlineCopy);
        assertEquals(deadline.id(), deadlineCopy.id());
        assertSame(deadlineCopy, blockCopy.plannedFor().deadline());
        assertNotSame(deadline, blockCopy.plannedFor().deadline());
        assertEquals(DAY, blockCopy.plannedFor().date());
    }

    @Test
    void changingTheCopyLeavesTheOriginal() {
        Schedule deadline = Schedule.deadline("D", DAY, "18:00", Category.JOB);
        Schedule copy = Schedule.copyAll(List.of(deadline)).get(0);
        copy.setDone(DAY, true);
        assertTrue(copy.isDone(DAY));
        assertTrue(!deadline.isDone(DAY));
    }

    @Test
    void doneDatesViewCannotBeModified() {
        Schedule deadline = Schedule.deadline("D", DAY, "18:00", Category.JOB);
        assertThrows(UnsupportedOperationException.class, () -> deadline.doneDates().add(DAY));
        assertThrows(UnsupportedOperationException.class, () -> deadline.skippedDates().add(DAY));
    }
}
