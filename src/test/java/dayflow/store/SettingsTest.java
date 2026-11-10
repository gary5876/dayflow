package dayflow.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SettingsTest {
    @TempDir
    Path folder;

    @Test
    void missingFileGivesTheDefault() {
        assertEquals(3, Settings.load(folder).openDeadlineDays());
        assertFalse(Files.exists(folder.resolve("settings")));
    }

    @Test
    void valueIsSavedAndReadBack() throws IOException {
        assertTrue(Settings.load(folder).setOpenDeadlineDays(12));
        assertEquals(12, Settings.load(folder).openDeadlineDays());
        assertEquals(List.of("openDeadlineDays=12"), Files.readAllLines(folder.resolve("settings")));
    }

    @Test
    void unknownKeysAreKeptOnWrite() throws IOException {
        Files.writeString(folder.resolve("settings"), "clipboard=off\nopenDeadlineDays=5\nkeyHint=가\n",
                StandardCharsets.UTF_8);
        Settings settings = Settings.load(folder);
        assertEquals(5, settings.openDeadlineDays());
        settings.setOpenDeadlineDays(7);
        assertEquals(List.of("clipboard=off", "openDeadlineDays=7", "keyHint=가"),
                Files.readAllLines(folder.resolve("settings"), StandardCharsets.UTF_8));
    }

    @Test
    void unreadableValueFallsBackToTheDefault() throws IOException {
        for (String bad : List.of("abc", "", "0", "31", "-4", "2.5")) {
            Files.writeString(folder.resolve("settings"), "openDeadlineDays=" + bad + "\n");
            assertEquals(3, Settings.load(folder).openDeadlineDays(), bad);
        }
    }

    @Test
    void brokenFileIsNotMovedAside() throws IOException {
        Files.write(folder.resolve("settings"), new byte[] {(byte) 0xFF, (byte) 0xFE, 0x00, '=', (byte) 0xC3});
        assertEquals(3, Settings.load(folder).openDeadlineDays());
        assertTrue(Files.exists(folder.resolve("settings")));
        try (var files = Files.list(folder)) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void failedWriteKeepsTheValueInMemory() throws IOException {
        Files.createDirectories(folder.resolve("settings").resolve("blocker"));
        Settings settings = Settings.load(folder);
        assertFalse(settings.setOpenDeadlineDays(9));
        assertEquals(9, settings.openDeadlineDays());
    }

    @Test
    void periodOutsideTheRangeIsRefused() {
        Settings settings = Settings.load(folder);
        assertThrows(IllegalArgumentException.class, () -> settings.setOpenDeadlineDays(0));
        assertThrows(IllegalArgumentException.class, () -> settings.setOpenDeadlineDays(31));
    }

    @Test
    void inMemorySettingsWriteNothing() {
        Settings settings = Settings.inMemory();
        assertTrue(settings.setOpenDeadlineDays(4));
        assertEquals(4, settings.openDeadlineDays());
    }
}
