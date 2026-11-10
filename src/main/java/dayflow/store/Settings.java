package dayflow.store;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The {@code settings} file of the data folder (D-31). One {@code key=value} per line in UTF-8. Keys this
 * version does not know are kept when the file is written. A value that cannot be read gives the default,
 * and a broken file is never moved aside because nothing in it is worth a backup.
 */
public final class Settings {
    /** The default of the open deadline period N (R-DL-5). */
    public static final int DEFAULT_OPEN_DEADLINE_DAYS = 3;
    /** The smallest period N (R-DL-5). */
    public static final int MIN_OPEN_DEADLINE_DAYS = 1;
    /** The largest period N (R-DL-5). */
    public static final int MAX_OPEN_DEADLINE_DAYS = 30;

    private static final String OPEN_DEADLINE_DAYS = "openDeadlineDays";

    private final Path path;
    private final Map<String, String> entries = new LinkedHashMap<>();

    private Settings(Path path) {
        this.path = path;
    }

    /**
     * Reads the settings of a data folder. A missing or unreadable file gives the defaults.
     *
     * @param dataFolder the folder that holds {@code settings}
     * @return the settings
     */
    public static Settings load(Path dataFolder) {
        Settings settings = new Settings(dataFolder.resolve("settings"));
        try {
            for (String line : Files.readAllLines(settings.path, StandardCharsets.UTF_8)) {
                int eq = line.indexOf('=');
                if (eq > 0) {
                    settings.entries.put(line.substring(0, eq).strip(), line.substring(eq + 1).strip());
                }
            }
        } catch (IOException | RuntimeException e) {
            settings.entries.clear();
        }
        return settings;
    }

    /**
     * Settings that are never written, for windows drawn without a data folder.
     *
     * @return settings with the defaults
     */
    public static Settings inMemory() {
        return new Settings(null);
    }

    /**
     * Returns the open deadline period N.
     *
     * @return N, from 1 to 30
     */
    public int openDeadlineDays() {
        try {
            int value = Integer.parseInt(entries.getOrDefault(OPEN_DEADLINE_DAYS, ""));
            if (value >= MIN_OPEN_DEADLINE_DAYS && value <= MAX_OPEN_DEADLINE_DAYS) {
                return value;
            }
        } catch (NumberFormatException e) {
            // Not a number, so the default below.
        }
        return DEFAULT_OPEN_DEADLINE_DAYS;
    }

    /**
     * Sets the period N and writes the file. If the write fails the value still holds in memory.
     *
     * @param days N, from 1 to 30
     * @return false if the file could not be written
     */
    public boolean setOpenDeadlineDays(int days) {
        if (days < MIN_OPEN_DEADLINE_DAYS || days > MAX_OPEN_DEADLINE_DAYS) {
            throw new IllegalArgumentException("N must be from 1 to 30: " + days);
        }
        entries.put(OPEN_DEADLINE_DAYS, Integer.toString(days));
        return write();
    }

    private boolean write() {
        if (path == null) {
            return true;
        }
        StringBuilder text = new StringBuilder();
        entries.forEach((key, value) -> text.append(key).append('=').append(value).append('\n'));
        try {
            Files.createDirectories(path.getParent());
            Path temp = path.resolveSibling("settings.tmp");
            Files.writeString(temp, text, StandardCharsets.UTF_8);
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
