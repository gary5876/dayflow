package dayflow.store;

import dayflow.model.Category;
import dayflow.model.Kind;
import dayflow.model.Priority;
import dayflow.model.Repeat;
import dayflow.model.Schedule;
import dayflow.model.ScheduleDraft;
import dayflow.model.WorkTarget;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Reads and writes the data file in the line format of D-28. Nothing else knows the format. */
public final class ScheduleFile {
    private static final String HEADER = "DayFlow 1";
    private static final int FIELDS = 16;

    private final Path path;

    /**
     * Creates the reader and writer.
     *
     * @param dataFolder the folder that holds {@code dayflow.dat}
     */
    public ScheduleFile(Path dataFolder) {
        this.path = dataFolder.resolve("dayflow.dat");
    }

    /**
     * Returns the path of the data file.
     *
     * @return the path of the data file
     */
    public Path path() {
        return path;
    }

    /**
     * Reads all schedules. A missing file is created empty (R-SAVE-5). A file that cannot be read is renamed
     * to {@code dayflow.dat.bak} and reported with {@link DataFileException}.
     *
     * @return the schedules
     * @throws IOException if the file cannot be read or created
     * @throws DataFileException if the file is not in the expected format
     */
    public List<Schedule> load() throws IOException, DataFileException {
        if (Files.notExists(path)) {
            save(List.of());
            return new ArrayList<>();
        }
        try {
            return parse(Files.readString(path, StandardCharsets.UTF_8));
        } catch (CharacterCodingException | FormatException e) {
            Path backup = path.resolveSibling(path.getFileName() + ".bak");
            Files.move(path, backup, StandardCopyOption.REPLACE_EXISTING);
            throw new DataFileException(backup, e);
        }
    }

    /**
     * Writes all schedules to a temporary file, then swaps it in so a failure leaves the old file (R-SAVE-2).
     *
     * @param schedules the schedules to write
     * @throws IOException if writing fails
     */
    public void save(List<Schedule> schedules) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        StringBuilder text = new StringBuilder(HEADER).append('\n');
        for (Schedule s : schedules) {
            text.append(format(s)).append('\n');
        }
        Path temp = Files.createTempFile(path.toAbsolutePath().getParent(), "dayflow", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer bytes = ByteBuffer.wrap(text.toString().getBytes(StandardCharsets.UTF_8));
                while (bytes.hasRemaining()) {
                    channel.write(bytes);
                }
                channel.force(true);
            }
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static String format(Schedule s) {
        String[] f = new String[FIELDS];
        f[0] = s.id();
        f[1] = s.kind().name();
        f[2] = escape(s.toDraft().title());
        f[3] = s.date().toString();
        f[4] = text(s.start());
        f[5] = text(s.end());
        f[6] = s.toDraft().category().name();
        f[7] = s.priority().name();
        f[8] = escape(s.memo());
        f[9] = s.repeat().name();
        f[10] = text(s.repeatUntil());
        f[11] = Integer.toString(s.workMinutes());
        f[12] = dates(s.doneDates());
        f[13] = dates(s.skippedDates());
        WorkTarget target = s.plannedFor();
        f[14] = target == null ? "" : target.deadline().id();
        f[15] = target == null ? "" : target.date().toString();
        return String.join("\t", f);
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String dates(Set<LocalDate> dates) {
        return dates.stream().sorted().map(LocalDate::toString).collect(Collectors.joining(","));
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n");
    }

    private static String unescape(String value) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '\\') {
                out.append(c);
                continue;
            }
            char next = ++i < value.length() ? value.charAt(i) : '?';
            switch (next) {
                case '\\' -> out.append('\\');
                case 't' -> out.append('\t');
                case 'n' -> out.append('\n');
                default -> throw new FormatException("Unknown escape in text");
            }
        }
        return out.toString();
    }

    private static List<Schedule> parse(String content) {
        String[] lines = content.split("\n", -1);
        // A hand-edited file may use CRLF line ends.
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].endsWith("\r")) {
                lines[i] = lines[i].substring(0, lines[i].length() - 1);
            }
        }
        if (!lines[0].equals(HEADER)) {
            throw new FormatException("First line is not " + HEADER);
        }
        // The text ends with a newline, so the last element is empty.
        int count = lines[lines.length - 1].isEmpty() ? lines.length - 1 : lines.length;
        List<String[]> rows = new ArrayList<>();
        for (int i = 1; i < count; i++) {
            String[] fields = lines[i].split("\t", -1);
            if (fields.length != FIELDS) {
                throw new FormatException("Line " + (i + 1) + " has " + fields.length + " fields");
            }
            rows.add(fields);
        }
        try {
            return build(rows);
        } catch (RuntimeException e) {
            throw e instanceof FormatException ? e : new FormatException(e);
        }
    }

    /** Makes the deadlines and other schedules first, then the work blocks that point at them by id. */
    private static List<Schedule> build(List<String[]> rows) {
        Map<String, Schedule> byId = new LinkedHashMap<>();
        Map<String[], Schedule> made = new HashMap<>();
        for (String[] row : rows) {
            if (row[14].isEmpty()) {
                put(byId, made, row, null);
            }
        }
        for (String[] row : rows) {
            if (!row[14].isEmpty()) {
                Schedule deadline = byId.get(row[14]);
                if (deadline == null || deadline.kind() != Kind.DEADLINE) {
                    throw new FormatException("Work block points at a missing deadline");
                }
                put(byId, made, row, new WorkTarget(deadline, LocalDate.parse(row[15])));
            }
        }
        List<Schedule> result = new ArrayList<>();
        for (String[] row : rows) {
            result.add(made.get(row));
        }
        return result;
    }

    private static void put(Map<String, Schedule> byId, Map<String[], Schedule> made, String[] f,
            WorkTarget target) {
        ScheduleDraft draft = new ScheduleDraft(unescape(f[2]), Kind.valueOf(f[1]), LocalDate.parse(f[3]),
                f[4].isEmpty() ? null : LocalTime.parse(f[4]), f[5].isEmpty() ? null : LocalTime.parse(f[5]),
                Category.valueOf(f[6]), Priority.valueOf(f[7]), unescape(f[8]), Repeat.valueOf(f[9]),
                f[10].isEmpty() ? null : LocalDate.parse(f[10]), Integer.parseInt(f[11]), target);
        Schedule schedule = Schedule.restore(f[0], draft, parseDates(f[12]), parseDates(f[13]));
        if (byId.put(f[0], schedule) != null) {
            throw new FormatException("Duplicate id " + f[0]);
        }
        made.put(f, schedule);
    }

    private static Set<LocalDate> parseDates(String field) {
        Set<LocalDate> dates = new HashSet<>();
        if (!field.isEmpty()) {
            Arrays.stream(field.split(",", -1)).map(LocalDate::parse).forEach(dates::add);
        }
        return dates;
    }

    /** The file is not in the format of D-28. */
    private static final class FormatException extends RuntimeException {
        FormatException(String message) {
            super(message);
        }

        FormatException(Throwable cause) {
            super(cause);
        }
    }
}
