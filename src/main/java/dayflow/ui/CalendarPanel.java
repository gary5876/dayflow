package dayflow.ui;

import dayflow.model.Occurrence;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;

/** Month view: a JTable with one cell per day. */
final class CalendarPanel extends JPanel {
    private static final String[] DAYS = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
    /** Lines that fit in a cell under the day number. */
    private static final int LINES = 4;

    private final List<Occurrence> schedules;
    private final LocalDate today;
    private final YearMonth month;
    private final LocalDate firstCell;
    private final JTable table;

    CalendarPanel(List<Occurrence> schedules, YearMonth month, LocalDate today) {
        super(new BorderLayout());
        this.schedules = schedules;
        this.month = month;
        this.today = today;
        LocalDate first = month.atDay(1);
        this.firstCell = first.minusDays(first.getDayOfWeek().getValue() % 7);

        table = new JTable(new MonthModel());
        table.setRowHeight(104);
        table.setCellSelectionEnabled(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setDefaultRenderer(LocalDate.class, new DayCell());
        table.setGridColor(new Color(0xE0E0E0));
        table.setShowGrid(true);
        table.getTableHeader().setReorderingAllowed(false);
        table.setFillsViewportHeight(true);
        add(new JScrollPane(table), BorderLayout.CENTER);
        select(today);
    }

    void select(LocalDate date) {
        int index = (int) ChronoUnit.DAYS.between(firstCell, date);
        if (index >= 0 && index < 42) {
            table.changeSelection(index / 7, index % 7, false, false);
        }
    }

    private final class MonthModel extends AbstractTableModel {
        @Override
        public int getRowCount() {
            return 6;
        }

        @Override
        public int getColumnCount() {
            return 7;
        }

        @Override
        public String getColumnName(int column) {
            return DAYS[column];
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return LocalDate.class;
        }

        @Override
        public Object getValueAt(int row, int column) {
            return firstCell.plusDays(row * 7L + column);
        }
    }

    /** Draws the day number, a deadline count, and as many schedule titles as fit in the cell. */
    private final class DayCell extends JComponent implements TableCellRenderer {
        private LocalDate date;
        private boolean selected;

        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            date = (LocalDate) value;
            selected = isSelected;
            return this;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics;
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean inMonth = YearMonth.from(date).equals(month);
            g.setColor(selected ? new Color(0xDCE9FD) : inMonth ? Color.WHITE : new Color(0xF7F7F7));
            g.fillRect(0, 0, getWidth(), getHeight());
            if (selected) {
                g.setColor(new Color(0x1A73E8));
                g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
            }

            Font base = getFont().deriveFont(11f);
            g.setFont(base.deriveFont(Font.BOLD, 12f));
            String number = String.valueOf(date.getDayOfMonth());
            if (date.equals(today)) {
                g.setColor(new Color(0x1A73E8));
                g.fillOval(4, 3, 20, 20);
                g.setColor(Color.WHITE);
                g.drawString(number, 14 - g.getFontMetrics().stringWidth(number) / 2, 18);
            } else {
                g.setColor(inMonth ? new Color(0x202124) : new Color(0xA0A0A0));
                g.drawString(number, 8, 18);
            }

            List<Occurrence> day = schedules.stream().filter(s -> s.date().equals(date)).toList();
            int y = 38;
            long deadlines = day.stream().filter(Occurrence::deadline).count();
            long open = day.stream().filter(Occurrence::isOpenDeadline).count();
            if (deadlines > 0) {
                long daysLeft = ChronoUnit.DAYS.between(today, date);
                boolean urgent = open > 0 && daysLeft >= 0 && daysLeft <= 2;
                g.setFont(base.deriveFont(urgent ? Font.BOLD : Font.PLAIN));
                // Grey when nothing is left to do or the date has passed; red only within two days.
                g.setColor(open == 0 || daysLeft < 0 ? new Color(0x9AA0A6)
                        : urgent ? new Color(0xD93025) : new Color(0xB06000));
                String text = deadlines + (deadlines == 1 ? " deadline" : " deadlines")
                        + (open == 0 ? " (done)" : open < deadlines ? " (" + open + " open)" : "");
                g.drawString(text, 8, y);
                y += 15;
            }

            // One-off schedules first: weekly classes would otherwise hide them.
            // Planned work blocks belong to the week timetable and are left out here.
            List<Occurrence> timed = day.stream().filter(s -> !s.deadline() && !s.planned())
                    .sorted(Comparator.comparing(Occurrence::recurring).thenComparing(Occurrence::sortTime)).toList();
            g.setFont(base);
            FontMetrics fm = g.getFontMetrics();
            // The deadline line takes one line. If the titles do not fit in the rest,
            // the last line becomes "+n more".
            int capacity = deadlines > 0 ? LINES - 1 : LINES;
            int shown = timed.size() <= capacity ? timed.size() : capacity - 1;
            for (int i = 0; i < shown; i++) {
                Occurrence s = timed.get(i);
                g.setColor(s.category().color());
                g.fillRoundRect(6, y - 9, 3, 11, 2, 2);
                g.setColor(inMonth ? new Color(0x3C4043) : new Color(0xA0A0A0));
                g.drawString(clip(s.allDay() ? s.title() : s.start() + " " + s.title(), fm, getWidth() - 18), 13, y);
                y += 15;
            }
            if (timed.size() > shown) {
                g.setColor(new Color(0x80868B));
                g.drawString("+" + (timed.size() - shown) + " more", 13, y);
            }
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
