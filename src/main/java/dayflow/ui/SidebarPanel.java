package dayflow.ui;

import dayflow.model.Occurrence;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.font.TextAttribute;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;

/** Always-visible briefing: this week's summary, open deadlines, and the selected day. */
final class SidebarPanel extends JPanel {
    private static final Color URGENT = new Color(0xD93025);
    private static final int DEADLINE_ROWS = 8;
    private static final int DAY_ROWS = 10;

    /**
     * @param selectedDayRow the row of the day list drawn as selected, or -1 for none
     * @param justDone       deadlines checked off a moment ago; they stay in the open list, struck through,
     *                       so the rows do not move while the user checks off several in a row
     */
    SidebarPanel(List<Occurrence> schedules, LocalDate today, LocalDate selected, int lookAheadDays,
                 int selectedDayRow, Set<Occurrence> justDone) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        setPreferredSize(new Dimension(330, 0));
        if (schedules.isEmpty()) {
            addGettingStarted();
            return;
        }

        LocalDate weekStart = today.minusDays(today.getDayOfWeek().getValue() % 7);
        LocalDate weekEnd = weekStart.plusDays(6);
        List<Occurrence> week = schedules.stream()
                .filter(s -> !s.date().isBefore(weekStart) && !s.date().isAfter(weekEnd) && !s.planned()).toList();
        long deadlines = week.stream().filter(Occurrence::deadline).count();
        long open = week.stream().filter(Occurrence::isOpenDeadline).count();
        long others = week.stream().filter(s -> !s.deadline() && !s.recurring()).count();
        long repeating = week.stream().filter(s -> !s.deadline() && s.recurring()).count();

        add(heading("This week"));
        add(line("<html><b>" + deadlines + (deadlines == 1 ? " deadline" : " deadlines") + "</b>, " + open
                + " still open</html>"));
        add(line(others + (others == 1 ? " other event, " : " other events, ") + repeating + " repeating"));
        add(Box.createVerticalStrut(10));

        JPanel deadlineHead = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        deadlineHead.add(heading("Open deadlines"));
        deadlineHead.add(new JLabel("  next"));
        deadlineHead.add(new JSpinner(new SpinnerNumberModel(lookAheadDays, 1, 30, 1)));
        deadlineHead.add(new JLabel(lookAheadDays == 1 ? "day" : "days"));
        add(left(deadlineHead));

        // Upcoming ones first, so a pile of overdue deadlines never pushes today's work out of sight.
        // Overdue ones follow, most recent first, and stay until the user checks or deletes them.
        LocalDate limit = today.plusDays(lookAheadDays);
        List<Occurrence> openList = new java.util.ArrayList<>(schedules.stream()
                .filter(s -> s.isOpenDeadline() || justDone.contains(s))
                .filter(s -> !s.date().isBefore(today) && !s.date().isAfter(limit))
                .sorted(Comparator.comparing(Occurrence::date).thenComparing(Occurrence::sortTime))
                .toList());
        openList.addAll(schedules.stream()
                .filter(s -> s.isOpenDeadline() || justDone.contains(s))
                .filter(s -> s.date().isBefore(today))
                .sorted(Comparator.comparing(Occurrence::date).thenComparing(Occurrence::sortTime).reversed())
                .toList());
        if (openList.isEmpty()) {
            add(line(lookAheadDays == 1 ? "Nothing due in the next day"
                    : "Nothing due in the next " + lookAheadDays + " days"));
        } else {
            JList<Occurrence> list = list(openList, new RowRenderer(today, true));
            list.setVisibleRowCount(Math.min(openList.size(), DEADLINE_ROWS));
            JScrollPane scroll = new JScrollPane(list, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                    JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            scroll.setBorder(BorderFactory.createEmptyBorder());
            scroll.setOpaque(false);
            scroll.getViewport().setOpaque(false);
            add(left(scroll));
        }
        // The link counts what it opens: every open deadline of the next three months, and the overdue ones.
        LocalDate listTo = today.plusMonths(3);
        long allOpen = schedules.stream().filter(Occurrence::isOpenDeadline)
                .filter(s -> !s.date().isAfter(listTo)).count();
        long overdue = schedules.stream().filter(Occurrence::isOpenDeadline)
                .filter(s -> s.date().isBefore(today)).count();
        JLabel all = new JLabel("<html><font color='#1A73E8'><u>Show all in List</u></font> <font color='#5F6368'>"
                + (allOpen == 0 ? "No open deadlines" : allOpen + " open" + (overdue > 0 ? " (" + overdue + " overdue)" : ""))
                + "</font></html>");
        all.setBorder(BorderFactory.createEmptyBorder(4, 4, 0, 0));
        add(left(all));
        add(Box.createVerticalStrut(10));

        // Everything on the selected day, deadlines included, so a day clicked in the calendar is complete.
        add(heading(selected.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH))));
        List<Occurrence> day = schedules.stream()
                .filter(s -> s.date().equals(selected))
                .sorted(Comparator.comparingInt((Occurrence s) -> s.deadline() ? 0 : s.allDay() ? 1 : 2)
                        .thenComparing(Occurrence::sortTime))
                .toList();
        if (day.isEmpty()) {
            add(line("No schedules"));
        } else {
            JList<Occurrence> dayList = list(day, new RowRenderer(today, false));
            // A long day scrolls inside the list, so the buttons under it always stay in the window.
            dayList.setVisibleRowCount(Math.min(day.size(), DAY_ROWS));
            if (selectedDayRow >= 0 && selectedDayRow < day.size()) {
                dayList.setSelectedIndex(selectedDayRow);
            }
            JScrollPane dayScroll = new JScrollPane(dayList, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                    JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            dayScroll.setBorder(BorderFactory.createEmptyBorder());
            dayScroll.setOpaque(false);
            dayScroll.getViewport().setOpaque(false);
            add(left(dayScroll));
        }
        // The buttons act on the row selected in either list; a row is selected by clicking its text.
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        buttons.add(new JButton("Add"));
        JButton edit = new JButton("Edit");
        JButton delete = new JButton("Delete");
        boolean hasSelection = selectedDayRow >= 0 && selectedDayRow < day.size();
        edit.setEnabled(hasSelection);
        delete.setEnabled(hasSelection);
        buttons.add(edit);
        buttons.add(delete);
        add(left(buttons));
        add(Box.createVerticalGlue());
    }

    private static JList<Occurrence> list(List<Occurrence> items, RowRenderer renderer) {
        JList<Occurrence> list = new JList<>(items.toArray(new Occurrence[0]));
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(renderer);
        list.setOpaque(false);
        return list;
    }

    /** First run: the import functions are the fastest way to fill the calendar, so they are offered here. */
    private void addGettingStarted() {
        add(heading("Start by importing"));
        add(line("<html>DayFlow has no schedules yet.<br>You can fill it without typing:</html>"));
        add(Box.createVerticalStrut(10));
        add(left(new JButton("Import timetable image...")));
        add(line("<html><font color='#5F6368'>A whole semester of classes from the image<br>"
                + "saved in the Everytime app. Works offline.</font></html>"));
        add(Box.createVerticalStrut(12));
        add(left(new JButton("Paste a job posting or notice")));
        add(line("<html><font color='#5F6368'>Copy a posting URL or any text with dates,<br>"
                + "then come back to this window.<br>Needs a Gemini API key (File menu).</font></html>"));
        add(Box.createVerticalStrut(12));
        add(left(new JButton("+ New schedule")));
        add(Box.createVerticalGlue());
    }

    /**
     * A deadline is drawn with a checkbox, other entries with a coloured bar.
     * Clicking the box toggles done; clicking the text selects the row; double-clicking the text opens it.
     * The time comes before the title, and only the title is shortened when the row is too narrow,
     * so a long title never pushes the due time out of sight.
     */
    private static final class RowRenderer implements ListCellRenderer<Occurrence> {
        private static final Color FADED = new Color(0x9AA0A6);
        private final LocalDate today;
        private final boolean withDDay;

        RowRenderer(LocalDate today, boolean withDDay) {
            this.today = today;
            this.withDDay = withDDay;
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Occurrence> list, Occurrence s, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            long daysLeft = ChronoUnit.DAYS.between(today, s.date());
            boolean urgent = s.isOpenDeadline() && daysLeft >= 0 && daysLeft <= 2;
            boolean faded = s.done() || (s.deadline() && daysLeft < 0);

            JPanel lead = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            lead.setOpaque(false);
            if (s.deadline()) {
                JCheckBox box = new JCheckBox("", s.done());
                box.setOpaque(false);
                lead.add(box);
            } else {
                JLabel bar = new JLabel("\u25AE");
                bar.setForeground(s.category().color());
                bar.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 5));
                lead.add(bar);
            }
            if (withDDay && s.deadline()) {
                JLabel badge = new JLabel(s.dDayLabel(today));
                badge.setForeground(faded ? FADED : urgent ? URGENT : new Color(0x3C4043));
                badge.setFont(badge.getFont().deriveFont(urgent ? Font.BOLD : Font.PLAIN));
                badge.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 7));
                lead.add(badge);
            }
            if (!s.allDay()) {
                JLabel time = new JLabel(s.timeText());
                time.setForeground(faded ? FADED : new Color(0x5F6368));
                time.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 7));
                lead.add(time);
            }

            // A plain label, not HTML: Swing then shortens an overlong title with "..." by itself.
            JLabel title = new JLabel(s.title());
            if (s.done()) {
                title.setFont(title.getFont().deriveFont(Map.of(TextAttribute.STRIKETHROUGH, TextAttribute.STRIKETHROUGH_ON)));
            }
            title.setForeground(faded ? FADED : new Color(0x202124));

            JPanel row = new JPanel(new BorderLayout());
            row.add(lead, BorderLayout.WEST);
            row.add(title, BorderLayout.CENTER);
            row.setToolTipText(s.title());
            row.setOpaque(isSelected);
            if (isSelected) {
                row.setBackground(new Color(0xDCE9FD));
            }
            return row;
        }
    }

    private static JLabel heading(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 13f));
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }

    private static JLabel line(String text) {
        JLabel label = new JLabel(text);
        label.setBorder(BorderFactory.createEmptyBorder(3, 2, 0, 0));
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }

    private static <T extends JComponent> T left(T component) {
        component.setAlignmentX(LEFT_ALIGNMENT);
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, component.getPreferredSize().height));
        return component;
    }

    static JPanel wrap(SidebarPanel sidebar) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, new Color(0xD0D0D0)));
        panel.add(sidebar, BorderLayout.CENTER);
        return panel;
    }
}
