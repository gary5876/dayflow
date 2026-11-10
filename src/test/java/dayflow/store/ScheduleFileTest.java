package dayflow.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dayflow.model.Category;
import dayflow.model.Priority;
import dayflow.model.Repeat;
import dayflow.model.Schedule;
import dayflow.model.WorkTarget;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScheduleFileTest {
    private static final LocalDate DAY = LocalDate.of(2026, 11, 20);

    @TempDir
    Path folder;

    @Test
    void missingFileIsCreatedEmpty() throws Exception {
        ScheduleFile file = new ScheduleFile(folder);
        assertEquals(folder.resolve("dayflow.dat"), file.path());
        assertTrue(file.load().isEmpty());
        assertTrue(Files.exists(file.path()));
        assertTrue(file.load().isEmpty());
    }

    @Test
    void roundTripKeepsValuesIdsAndWorkBlockLinks() throws Exception {
        Schedule memo = Schedule.timed("Class", DAY, "09:00", "10:30", Category.STUDY)
                .memo("a\tb\nc\\d\\n").priority(Priority.HIGH);
        Schedule deadline = Schedule.deadline("Rent", DAY, "18:00", Category.LIFE)
                .repeat(Repeat.MONTHLY, DAY.plusMonths(3)).work(90)
                .skip(DAY.plusMonths(1)).setDone(DAY, true).setDone(DAY.plusMonths(2), true);
        Schedule block = Schedule.workBlock(new WorkTarget(deadline, DAY), DAY, LocalTime.of(11, 0),
                LocalTime.of(12, 0));
        Schedule allDay = Schedule.allDay("Festival", DAY, Category.HOBBY);
        ScheduleFile file = new ScheduleFile(folder);
        // The block comes before its deadline in the file, to check that links are made after reading all lines.
        file.save(List.of(memo, block, deadline, allDay));

        List<Schedule> loaded = file.load();
        assertEquals(4, loaded.size());
        Schedule m = loaded.get(0);
        assertEquals(memo.id(), m.id());
        assertEquals(memo.toDraft(), m.toDraft());
        assertEquals("a\tb\nc\\d\\n", m.memo());
        Schedule b = loaded.get(1);
        Schedule d = loaded.get(2);
        assertEquals(deadline.id(), d.id());
        assertEquals(deadline.toDraft(), d.toDraft());
        assertEquals(deadline.doneDates(), d.doneDates());
        assertEquals(deadline.skippedDates(), d.skippedDates());
        assertEquals(90, d.workMinutes());
        assertEquals(block.id(), b.id());
        assertSame(d, b.plannedFor().deadline());
        assertEquals(DAY, b.plannedFor().date());
        assertEquals(LocalTime.of(11, 0), b.start());
        Schedule a = loaded.get(3);
        assertNull(a.start());
        assertNull(a.repeatUntil());
    }

    @Test
    void aFileWithCrlfLineEndsIsRead() throws Exception {
        Schedule a = Schedule.allDay("Festival", DAY, Category.HOBBY);
        ScheduleFile file = new ScheduleFile(folder);
        file.save(List.of(a));
        Files.writeString(file.path(), Files.readString(file.path()).replace("\n", "\r\n"));
        List<Schedule> loaded = file.load();
        assertEquals(1, loaded.size());
        assertEquals(a.toDraft(), loaded.get(0).toDraft());
        assertFalse(Files.exists(folder.resolve("dayflow.dat.bak")));
    }

    @Test
    void wrongFirstLineMovesFileToBackup() throws Exception {
        Path dat = folder.resolve("dayflow.dat");
        Files.writeString(dat, "Other 2\n");
        DataFileException e = assertThrows(DataFileException.class, () -> new ScheduleFile(folder).load());
        assertEquals(folder.resolve("dayflow.dat.bak"), e.backup());
        assertTrue(Files.exists(e.backup()));
        assertFalse(Files.exists(dat));
    }

    @Test
    void wrongFieldCountMovesFileToBackup() throws Exception {
        Path dat = folder.resolve("dayflow.dat");
        Files.writeString(dat, "DayFlow 1\na\tb\tc\n");
        DataFileException e = assertThrows(DataFileException.class, () -> new ScheduleFile(folder).load());
        assertTrue(Files.exists(e.backup()));
        assertFalse(Files.exists(dat));
    }

    @Test
    void unreadableValueMovesFileToBackup() throws Exception {
        Path dat = folder.resolve("dayflow.dat");
        String line = String.join("\t", "id1", "TIMED", "T", "not-a-date", "", "", "LIFE", "MEDIUM", "", "NONE",
                "", "-1", "", "", "", "");
        Files.writeString(dat, "DayFlow 1\n" + line + "\n");
        assertThrows(DataFileException.class, () -> new ScheduleFile(folder).load());
        assertFalse(Files.exists(dat));
    }

    @Test
    void savingLeavesNoTemporaryFile() throws IOException {
        ScheduleFile file = new ScheduleFile(folder);
        file.save(List.of(Schedule.allDay("X", DAY, Category.LIFE)));
        file.save(List.of());
        try (Stream<Path> files = Files.list(folder)) {
            assertEquals(List.of("dayflow.dat"), files.map(p -> p.getFileName().toString()).toList());
        }
    }
}
