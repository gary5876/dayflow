package dayflow.timetable;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/**
 * Reads an Everytime timetable image.
 *
 * Day and time come from where each coloured block sits on the grid, so they are exact.
 * Only the words (course, professor, room, day and hour labels) come from text recognition.
 */
public final class TimetableImageParser {
    private static final String DAY_LABELS = "월화수목금토일";
    private static final Pattern HOUR_LABEL = Pattern.compile("(\\d{1,2})\\s*시");
    private static final int MINUTE_STEP = 5;

    private final TextRecognizer recognizer;

    public TimetableImageParser(TextRecognizer recognizer) {
        this.recognizer = recognizer;
    }

    public List<ClassBlock> parse(Path imagePath) throws IOException, TimetableFormatException {
        BufferedImage image = ImageIO.read(imagePath.toFile());
        if (image == null) {
            throw new TimetableFormatException("Not an image file.");
        }
        Grid grid = findGrid(image);
        List<TextLine> texts = recognizer.recognize(imagePath);
        int firstHour = findFirstHour(grid, texts);

        List<ClassBlock> blocks = new ArrayList<>();
        for (int col = 1; col + 1 < grid.columnLines.size(); col++) {
            int left = grid.columnLines.get(col) + 1;
            int right = grid.columnLines.get(col + 1) - 1;
            DayOfWeek day = findDay(grid, texts, left, right, col);
            for (Rectangle area : findBlocks(image, grid, left, right)) {
                List<TextLine> inside = texts.stream()
                        .filter(t -> area.contains(t.centerX(), t.centerY()))
                        .sorted(Comparator.comparingInt(TextLine::centerY))
                        .toList();
                blocks.add(toClassBlock(day, area, inside, grid, firstHour,
                        new Color(image.getRGB(right - 3, area.y))));
            }
        }
        if (blocks.isEmpty()) {
            throw new TimetableFormatException("No classes were found in the timetable.");
        }
        blocks.sort(Comparator.comparing(ClassBlock::day).thenComparing(ClassBlock::start));
        return blocks;
    }

    /** Grid lines of the timetable; the first column holds hour labels and the first row day labels. */
    private record Grid(int lineColor, List<Integer> columnLines, List<Integer> rowLines) {
        int headerBottom() {
            return rowLines.get(0);
        }

        int rowHeight() {
            return rowLines.get(1) - rowLines.get(0);
        }
    }

    private static Grid findGrid(BufferedImage image) throws TimetableFormatException {
        int lineColor = image.getRGB(0, 0);
        int headerBottom = -1;
        for (int y = 1; y < image.getHeight(); y++) {
            if (image.getRGB(1, y) == lineColor) {
                headerBottom = y;
                break;
            }
        }
        if (headerBottom < 2) {
            throw new TimetableFormatException("The timetable grid was not found.");
        }

        List<Integer> columnLines = new ArrayList<>();
        for (int x = 0; x < image.getWidth(); x++) {
            if (isLine(image, lineColor, x, 1, x, headerBottom - 1)) {
                columnLines.add(x);
            }
        }
        if (columnLines.size() < 3) {
            throw new TimetableFormatException("The timetable columns were not found.");
        }

        List<Integer> rowLines = new ArrayList<>();
        int labelRight = columnLines.get(1) - 1;
        for (int y = headerBottom; y < image.getHeight(); y++) {
            if (isLine(image, lineColor, 1, y, labelRight, y)) {
                rowLines.add(y);
            }
        }
        if (rowLines.size() < 2) {
            throw new TimetableFormatException("The timetable rows were not found.");
        }
        return new Grid(lineColor, columnLines, rowLines);
    }

    private static boolean isLine(BufferedImage image, int lineColor, int x1, int y1, int x2, int y2) {
        for (int y = y1; y <= y2; y++) {
            for (int x = x1; x <= x2; x++) {
                if (image.getRGB(x, y) != lineColor) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int findFirstHour(Grid grid, List<TextLine> texts) throws TimetableFormatException {
        int labelRight = grid.columnLines.get(1);
        for (TextLine t : texts) {
            Matcher m = HOUR_LABEL.matcher(t.text());
            if (t.centerX() < labelRight && t.centerY() > grid.headerBottom() && m.find()) {
                int row = (t.centerY() - grid.headerBottom() - 1) / grid.rowHeight();
                return Integer.parseInt(m.group(1)) - row;
            }
        }
        throw new TimetableFormatException("The hour labels could not be read.");
    }

    private static DayOfWeek findDay(Grid grid, List<TextLine> texts, int left, int right, int column) {
        for (TextLine t : texts) {
            if (t.centerY() < grid.headerBottom() && t.centerX() >= left && t.centerX() <= right) {
                int index = DAY_LABELS.indexOf(t.text().strip());
                if (index >= 0 && t.text().strip().length() == 1) {
                    return DayOfWeek.of(index + 1);
                }
            }
        }
        return DayOfWeek.MONDAY.plus(column - 1);
    }

    /** Scans a strip near the right edge of a day column, where no text is drawn. */
    private static List<Rectangle> findBlocks(BufferedImage image, Grid grid, int left, int right) {
        List<Rectangle> blocks = new ArrayList<>();
        int x = right - 3;
        int y = grid.headerBottom() + 1;
        while (y < image.getHeight()) {
            int rgb = image.getRGB(x, y);
            if (!isBlockColor(rgb, grid.lineColor)) {
                y++;
                continue;
            }
            int top = y;
            while (y < image.getHeight() && image.getRGB(x, y) == rgb) {
                y++;
            }
            blocks.add(new Rectangle(left, top, right - left + 1, y - top));
        }
        return blocks;
    }

    /** Blocks are pastel colours; the background, header, and grid lines are pure greys. */
    private static boolean isBlockColor(int rgb, int lineColor) {
        if (rgb == lineColor) {
            return false;
        }
        Color c = new Color(rgb);
        int max = Math.max(c.getRed(), Math.max(c.getGreen(), c.getBlue()));
        int min = Math.min(c.getRed(), Math.min(c.getGreen(), c.getBlue()));
        return max - min >= 5;
    }

    private static ClassBlock toClassBlock(DayOfWeek day, Rectangle area, List<TextLine> lines,
                                           Grid grid, int firstHour, Color color) {
        LocalTime start = toTime(area.y, grid, firstHour);
        LocalTime end = toTime(area.y + area.height, grid, firstHour);

        // Everytime writes the course name first, then the professor, then the room.
        int n = lines.size();
        String room = n >= 3 ? lines.get(n - 1).text() : "";
        String professor = n >= 2 ? lines.get(n >= 3 ? n - 2 : 1).text() : "";
        StringBuilder name = new StringBuilder();
        for (int i = 0; i < Math.max(1, n - 2) && i < n; i++) {
            name.append(lines.get(i).text());
        }
        return new ClassBlock(day, start, end, name.toString(), professor, room, color);
    }

    private static LocalTime toTime(int y, Grid grid, int firstHour) {
        double hours = (y - grid.headerBottom() - 1) / (double) grid.rowHeight();
        long minutes = Math.round(hours * 60 / MINUTE_STEP) * MINUTE_STEP + firstHour * 60L;
        return LocalTime.of((int) (minutes / 60) % 24, (int) (minutes % 60));
    }
}
