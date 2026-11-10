package dayflow.ui;

import dayflow.model.Occurrence;
import dayflow.plan.PlanStatus;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

/**
 * Week timetable: days across, hours down. Deadlines and all-day entries sit in a strip
 * under the day names, and work blocks placed by the planner are drawn with a dashed border.
 */
final class WeekPanel extends JPanel {
    private static final int DEFAULT_FIRST_HOUR = 8;
    private static final int DEFAULT_LAST_HOUR = 22;
    private static final int HOUR_HEIGHT = 40;
    private static final int GUTTER = 48;
    private static final int HEADER = 24;
    private static final int STRIP_LINES = 3;
    private static final int STRIP = STRIP_LINES * 15 + 8;

    private final List<Occurrence> schedules;
    private final LocalDate weekStart;
    private final LocalDate today;
    private final int firstHour;
    private final int lastHour;
    private final PanelEvents events;
    /** What the last paint drew where, so a click can tell which entry it hit. */
    private final List<Hit> hits = new ArrayList<>();
    private boolean painted;

    private record Hit(Rectangle area, Occurrence occurrence) {
    }

    WeekPanel(List<Occurrence> schedules, LocalDate weekStart, LocalDateTime now) {
        this(schedules, weekStart, now, PanelEvents.NONE);
    }

    /** @param events what a click on an entry or on a free time does */
    WeekPanel(List<Occurrence> schedules, LocalDate weekStart, LocalDateTime now, PanelEvents events) {
        super(new BorderLayout());
        this.events = events;
        this.schedules = schedules;
        this.weekStart = weekStart;
        this.today = now.toLocalDate();
        PlanStatus status = PlanStatus.of(schedules, weekStart, now);
        // The grid grows to include anything of this week that is earlier or later than the usual hours.
        List<Occurrence> timed = schedules.stream().filter(o -> o.timed() && inWeek(o)).toList();
        this.firstHour = Math.min(DEFAULT_FIRST_HOUR,
                timed.stream().mapToInt(o -> o.start().getHour()).min().orElse(DEFAULT_FIRST_HOUR));
        this.lastHour = Math.max(DEFAULT_LAST_HOUR, timed.stream()
                .mapToInt(o -> o.end().getMinute() == 0 ? o.end().getHour() : o.end().getHour() + 1)
                .max().orElse(DEFAULT_LAST_HOUR));

        JPanel bar = new JPanel(new BorderLayout());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        // A week that is already over cannot be planned.
        JButton plan = new JButton("Plan my week");
        plan.setEnabled(!weekStart.plusDays(6).isBefore(today));
        plan.addActionListener(e -> events.planWeek());
        buttons.add(plan);
        JButton clear = new JButton("Clear plan");
        clear.setEnabled(status.clearable());
        clear.addActionListener(e -> events.clearPlan());
        buttons.add(clear);
        bar.add(buttons, BorderLayout.WEST);
        // The summary can be long, so the legend is kept short and gives way to it.
        if (status.hasPlan()) {
            JLabel summary = new JLabel(summaryText(status));
            summary.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 10));
            bar.add(summary, BorderLayout.CENTER);
        }
        JLabel legend = new JLabel("Dashed blocks: planned work");
        legend.setForeground(new Color(0x5F6368));
        legend.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 10));
        bar.add(legend, BorderLayout.EAST);
        add(bar, BorderLayout.NORTH);

        Grid grid = new Grid();
        if (events != PanelEvents.NONE) {
            grid.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    clicked(grid, e);
                }
            });
        }
        grid.setPreferredSize(new Dimension(600, HEADER + STRIP + (lastHour - firstHour) * HOUR_HEIGHT + 6));
        add(new JScrollPane(grid), BorderLayout.CENTER);
    }

    /** The plan line: hours planned, work that no block covers, and whether the plan still fits. */
    private static String summaryText(PlanStatus status) {
        StringBuilder text = new StringBuilder("<html>Planned " + hours(status.plannedMinutes()));
        if (status.unplannedMinutes() > 0) {
            text.append(" &nbsp;<font color='#D93025'><b>").append(hours(status.unplannedMinutes()))
                    .append(" not planned for ").append(status.unplannedDeadlines())
                    .append(status.unplannedDeadlines() == 1 ? " deadline" : " deadlines").append("</b></font>");
        }
        if (status.outOfDate()) {
            text.append(" &nbsp;<font color='#B06000'><b>Plan is out of date</b></font>");
        }
        return text.append("</html>").toString();
    }

    static String hours(int minutes) {
        return minutes % 60 == 0 ? minutes / 60 + " h" : String.format("%.1f h", minutes / 60.0);
    }

    /**
     * R-WEEK-7 and R-WEEK-8. One click on an entry selects its day and the entry, two open it. Two clicks on a
     * free time open a new schedule for that day and that half hour.
     */
    private void clicked(Grid grid, MouseEvent e) {
        // A click that arrives before the first paint of a rebuilt panel would see no entries and be taken for a free time.
        if (!painted) {
            return;
        }
        Occurrence hit = null;
        for (Hit h : hits) {
            if (h.area().contains(e.getPoint())) {
                hit = h.occurrence();
            }
        }
        if (hit != null) {
            if (e.getClickCount() == 1) {
                events.select(hit, ViewState.Origin.DAY_LIST);
                events.selectDate(hit.date());
            } else if (e.getClickCount() == 2) {
                events.edit(hit);
            }
            return;
        }
        int column = (grid.getWidth() - GUTTER - 2) / 7;
        int top = HEADER + STRIP;
        int day = (e.getX() - GUTTER) / column;
        if (e.getClickCount() != 2 || e.getX() < GUTTER || day < 0 || day > 6 || e.getY() < top) {
            return;
        }
        int minutes = firstHour * 60 + (e.getY() - top) * 60 / HOUR_HEIGHT;
        int slot = Math.min(minutes / 30 * 30, 23 * 60 + 30);
        events.add(weekStart.plusDays(day), LocalTime.of(slot / 60, slot % 60));
    }

    private boolean inWeek(Occurrence o) {
        long day = o.date().toEpochDay() - weekStart.toEpochDay();
        return day >= 0 && day < 7;
    }

    private final class Grid extends JComponent {
        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics;
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int width = getWidth();
            int column = (width - GUTTER - 2) / 7;
            int top = HEADER + STRIP;
            Font base = getFont().deriveFont(11f);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, getHeight());
            hits.clear();

            for (int d = 0; d < 7; d++) {
                LocalDate date = weekStart.plusDays(d);
                int x = GUTTER + d * column;
                if (date.equals(today)) {
                    g.setColor(new Color(0xF3F8FF));
                    g.fillRect(x, 0, column, getHeight());
                }
                g.setFont(base.deriveFont(Font.BOLD, 12f));
                g.setColor(date.equals(today) ? new Color(0x1A73E8) : new Color(0x202124));
                g.drawString(date.format(DateTimeFormatter.ofPattern("EEE d", Locale.ENGLISH)), x + 6, 16);
                paintStrip(g, base, date, x, column);
            }

            g.setFont(base);
            for (int h = firstHour; h <= lastHour; h++) {
                int y = top + (h - firstHour) * HOUR_HEIGHT;
                g.setColor(new Color(0xE3E3E3));
                g.drawLine(GUTTER, y, width, y);
                g.setColor(new Color(0x80868B));
                g.drawString(String.format("%02d:00", h), 8, y + 4);
            }
            g.setColor(new Color(0xE3E3E3));
            for (int d = 0; d <= 7; d++) {
                g.drawLine(GUTTER + d * column, 0, GUTTER + d * column, getHeight());
            }
            g.drawLine(0, HEADER, width, HEADER);

            for (int d = 0; d < 7; d++) {
                paintDay(g, base, weekStart.plusDays(d), GUTTER + d * column, column, top);
            }
            painted = true;
        }

        /**
         * Blocks that overlap in time share the width of the day column, side by side.
         * Only the blocks of one overlapping group are narrowed, and the rest of the day keeps the full width.
         */
        private void paintDay(Graphics2D g, Font base, LocalDate date, int x, int column, int top) {
            List<Occurrence> day = schedules.stream().filter(o -> o.timed() && o.date().equals(date))
                    .sorted(java.util.Comparator.comparing(Occurrence::start)).toList();
            int i = 0;
            while (i < day.size()) {
                // Collect one group: blocks chained together by overlaps.
                java.util.List<java.time.LocalTime> laneEnds = new java.util.ArrayList<>();
                java.util.List<Integer> lanes = new java.util.ArrayList<>();
                java.time.LocalTime groupEnd = day.get(i).start();
                int first = i;
                while (i < day.size() && (i == first || day.get(i).start().isBefore(groupEnd))) {
                    Occurrence o = day.get(i);
                    int free = 0;
                    while (free < laneEnds.size() && laneEnds.get(free).isAfter(o.start())) {
                        free++;
                    }
                    if (free == laneEnds.size()) {
                        laneEnds.add(o.end());
                    } else {
                        laneEnds.set(free, o.end());
                    }
                    lanes.add(free);
                    groupEnd = o.end().isAfter(groupEnd) ? o.end() : groupEnd;
                    i++;
                }
                int width = column / laneEnds.size();
                for (int k = first; k < i; k++) {
                    paintBlock(g, base, day.get(k), x + lanes.get(k - first) * width, width, top);
                }
            }
        }

        /** Deadlines and all-day entries of one day, listed above the hour grid. */
        private void paintStrip(Graphics2D g, Font base, LocalDate date, int x, int column) {
            // Deadlines first, by due time, then all-day entries: the same order as the day list.
            List<Occurrence> items = schedules.stream()
                    .filter(o -> o.date().equals(date) && !o.timed())
                    .sorted(Comparator.comparing((Occurrence o) -> !o.deadline()).thenComparing(Occurrence::sortTime))
                    .toList();
            long daysLeft = ChronoUnit.DAYS.between(today, date);
            int y = HEADER + 14;
            g.setFont(base);
            FontMetrics fm = g.getFontMetrics();
            for (int i = 0; i < items.size() && i < STRIP_LINES; i++) {
                Occurrence o = items.get(i);
                boolean lastLine = i == STRIP_LINES - 1 && items.size() > STRIP_LINES;
                String text = lastLine ? "+" + (items.size() - i) + " more"
                        : o.deadline() ? o.timeText() + " " + o.title() : o.title();
                // Only an open deadline within two days is emphasised, and an overdue open one is grey.
                boolean urgent = !lastLine && o.isOpenDeadline() && daysLeft >= 0 && daysLeft <= 2;
                boolean overdue = !lastLine && o.isOpenDeadline() && daysLeft < 0;
                g.setFont(urgent ? base.deriveFont(Font.BOLD) : base);
                fm = g.getFontMetrics();
                g.setColor(lastLine || o.done() || overdue ? new Color(0x9AA0A6)
                        : urgent ? new Color(0xD93025) : o.category().color());
                String shown = clip(text, fm, column - 10);
                g.drawString(shown, x + 5, y);
                if (!lastLine) {
                    hits.add(new Hit(new Rectangle(x + 1, y - 12, column - 2, 15), o));
                }
                if (o.done() && !lastLine) {
                    g.drawLine(x + 5, y - 4, x + 5 + fm.stringWidth(shown), y - 4);
                }
                y += 15;
            }
        }

        private void paintBlock(Graphics2D g, Font base, Occurrence o, int x, int column, int top) {
            int y1 = top + minutesFromTop(o.start().getHour() * 60 + o.start().getMinute());
            int y2 = top + minutesFromTop(o.end().getHour() * 60 + o.end().getMinute());
            Color c = o.category().color();
            int bx = x + 3;
            int bw = column - 6;
            int bh = Math.max(y2 - y1 - 2, 12);
            hits.add(new Hit(new Rectangle(bx, y1 + 1, bw, bh), o));
            if (o.planned()) {
                g.setColor(Color.WHITE);
                g.fillRoundRect(bx, y1 + 1, bw, bh, 6, 6);
                g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] {5f, 4f}, 0f));
                g.setColor(c);
                g.drawRoundRect(bx, y1 + 1, bw, bh, 6, 6);
                g.setStroke(new BasicStroke(1f));
            } else {
                g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 46));
                g.fillRoundRect(bx, y1 + 1, bw, bh, 6, 6);
                g.setColor(c);
                g.fillRoundRect(bx, y1 + 1, 4, bh, 4, 4);
            }
            Shape clip = g.getClip();
            g.clipRect(bx, y1 + 1, bw, bh);
            g.setFont(base.deriveFont(o.planned() ? Font.ITALIC : Font.PLAIN));
            g.setColor(new Color(0x202124));
            FontMetrics fm = g.getFontMetrics();
            g.drawString(clip(o.title(), fm, bw - 14), bx + 9, y1 + 14);
            if (bh >= 30) {
                g.setColor(new Color(0x5F6368));
                g.drawString(o.timeText(), bx + 9, y1 + 28);
            }
            g.setClip(clip);
        }

        private int minutesFromTop(int minuteOfDay) {
            return (minuteOfDay - firstHour * 60) * HOUR_HEIGHT / 60;
        }

        private String clip(String text, FontMetrics fm, int width) {
            if (fm.stringWidth(text) <= width) {
                return text;
            }
            String s = text;
            while (s.length() > 1 && fm.stringWidth(s + "...") > width) {
                s = s.substring(0, s.length() - 1);
            }
            return s + "...";
        }
    }
}
