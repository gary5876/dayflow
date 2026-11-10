package dayflow.ui;

import dayflow.model.Occurrence;
import dayflow.store.Settings;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextAttribute;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.swing.AbstractAction;
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
import javax.swing.KeyStroke;
import javax.swing.JSpinner;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;

/** Always-visible briefing: this week's summary, open deadlines, and the selected day. */
final class SidebarPanel extends JPanel {
    private static final Color URGENT = new Color(0xD93025);
    private static final int DEADLINE_ROWS = 8;
    private static final int DAY_ROWS = 10;
    /** The width at the left of a row where its checkbox is. A click there is on the box and not on the text. */
    private static final int BOX_WIDTH = new JCheckBox().getPreferredSize().width;

    private final PanelEvents events;
    private final LocalDate selectedDate;
    private List<Occurrence> openItems = List.of();
    private List<Occurrence> dayItems = List.of();
    private RowRenderer openRenderer;
    private RowRenderer dayRenderer;
    private JList<Occurrence> openList;
    private JList<Occurrence> dayList;
    private JButton editButton;
    private JButton deleteButton;

    /**
     * @param selectedDayRow the row of the day list drawn as selected, or -1 for none
     * @param justDone       deadlines checked off a moment ago. They stay in the open list, struck through,
     *                       so the rows do not move while the user checks off several in a row
     */
    SidebarPanel(List<Occurrence> schedules, LocalDate today, LocalDate selected, int lookAheadDays,
                 int selectedDayRow, Set<Occurrence> justDone) {
        this(schedules, today, selected, lookAheadDays, selectedDayRow, justDone, PanelEvents.NONE);
    }

    /** @param events what the clicks in the lists and the buttons do */
    SidebarPanel(List<Occurrence> schedules, LocalDate today, LocalDate selected, int lookAheadDays,
                 int selectedDayRow, Set<Occurrence> justDone, PanelEvents events) {
        this.events = events;
        this.selectedDate = selected;
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
        JSpinner period = new JSpinner(new SpinnerNumberModel(lookAheadDays, Settings.MIN_OPEN_DEADLINE_DAYS,
                Settings.MAX_OPEN_DEADLINE_DAYS, 1));
        if (events != PanelEvents.NONE) {
            period.addChangeListener(e -> events.setOpenDeadlineDays((Integer) period.getValue()));
        }
        deadlineHead.add(period);
        deadlineHead.add(new JLabel(lookAheadDays == 1 ? "day" : "days"));
        add(left(deadlineHead));

        // Upcoming ones first, so a pile of overdue deadlines never pushes today's work out of sight.
        // Overdue ones follow, most recent first, and stay until the user checks or deletes them.
        LocalDate limit = today.plusDays(lookAheadDays);
        List<Occurrence> openRows = new java.util.ArrayList<>(schedules.stream()
                .filter(s -> s.isOpenDeadline() || justDone.contains(s))
                .filter(s -> !s.date().isBefore(today) && !s.date().isAfter(limit))
                .sorted(Comparator.comparing(Occurrence::date).thenComparing(Occurrence::sortTime))
                .toList());
        openRows.addAll(schedules.stream()
                .filter(s -> s.isOpenDeadline() || justDone.contains(s))
                .filter(s -> s.date().isBefore(today))
                .sorted(Comparator.comparing(Occurrence::date).thenComparing(Occurrence::sortTime).reversed())
                .toList());
        if (openRows.isEmpty()) {
            add(line(lookAheadDays == 1 ? "Nothing due in the next day"
                    : "Nothing due in the next " + lookAheadDays + " days"));
        } else {
            this.openItems = openRows;
            this.openRenderer = new RowRenderer(today, true);
            JList<Occurrence> list = list(openRows, openRenderer, ViewState.Origin.OPEN_LIST);
            this.openList = list;
            list.setVisibleRowCount(Math.min(openRows.size(), DEADLINE_ROWS));
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
        if (events != PanelEvents.NONE) {
            all.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            all.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    events.showAllInList();
                }
            });
        }
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
            this.dayItems = day;
            this.dayRenderer = new RowRenderer(today, false);
            this.dayList = list(day, dayRenderer, ViewState.Origin.DAY_LIST);
            // A long day scrolls inside the list, so the buttons under it always stay in the window.
            dayList.setVisibleRowCount(Math.min(day.size(), DAY_ROWS));
            if (selectedDayRow >= 0 && selectedDayRow < day.size()) {
                dayRenderer.selectedIndex = selectedDayRow;
            }
            JScrollPane dayScroll = new JScrollPane(dayList, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                    JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            dayScroll.setBorder(BorderFactory.createEmptyBorder());
            dayScroll.setOpaque(false);
            dayScroll.getViewport().setOpaque(false);
            add(left(dayScroll));
        }
        // The buttons act on the row selected in either list, and a row is selected by clicking its text.
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        JButton add = new JButton("Add");
        add.addActionListener(e -> events.add(selectedDate, null));
        buttons.add(add);
        editButton = new JButton("Edit");
        deleteButton = new JButton("Delete");
        boolean hasSelection = selectedDayRow >= 0 && selectedDayRow < day.size();
        editButton.setEnabled(hasSelection);
        deleteButton.setEnabled(hasSelection);
        editButton.addActionListener(e -> selectedOccurrence().ifPresent(events::edit));
        deleteButton.addActionListener(e -> selectedOccurrence().ifPresent(events::delete));
        buttons.add(editButton);
        buttons.add(deleteButton);
        add(left(buttons));
        add(Box.createVerticalGlue());
    }

    private JList<Occurrence> list(List<Occurrence> items, RowRenderer renderer, ViewState.Origin origin) {
        // The list is as wide as its scroll pane, so a row longer than that is cut with "..." and not clipped.
        JList<Occurrence> list = new JList<>(items.toArray(new Occurrence[0])) {
            @Override
            public boolean getScrollableTracksViewportWidth() {
                return true;
            }
        };
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(renderer);
        list.setOpaque(false);
        if (events != PanelEvents.NONE) {
            wireEvents(list, items, renderer, origin);
        }
        return list;
    }

    /**
     * R-LIST-1 to R-LIST-3 and R-LIST-5. The row is drawn as selected from the window's state and not from the
     * list's own selection, because a click on the checkbox must not select the row.
     */
    private void wireEvents(JList<Occurrence> list, List<Occurrence> items, RowRenderer renderer,
                            ViewState.Origin origin) {
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int index = list.locationToIndex(e.getPoint());
                Rectangle bounds = index < 0 ? null : list.getCellBounds(index, index);
                if (bounds == null || !bounds.contains(e.getPoint())) {
                    return;
                }
                Occurrence row = items.get(index);
                if (row.deadline() && e.getX() - bounds.x < BOX_WIDTH) {
                    events.toggleDone(row);
                } else if (e.getClickCount() == 1) {
                    events.select(row, origin);
                } else if (e.getClickCount() == 2) {
                    events.edit(row);
                }
            }
        });
        // Delete and Backspace both, because the key marked delete on a Mac keyboard is Backspace.
        for (int key : new int[] {KeyEvent.VK_DELETE, KeyEvent.VK_BACK_SPACE}) {
            String name = "dayflow.delete." + key;
            list.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key, 0), name);
            list.getActionMap().put(name, new AbstractAction() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    if (renderer.selectedIndex >= 0 && renderer.selectedIndex < items.size()) {
                        events.delete(items.get(renderer.selectedIndex));
                    }
                }
            });
        }
    }

    /** The row selected in either list, for the Edit and Delete buttons (R-LIST-5). */
    private java.util.Optional<Occurrence> selectedOccurrence() {
        if (openRenderer != null && openRenderer.selectedIndex >= 0 && openRenderer.selectedIndex < openItems.size()) {
            return java.util.Optional.of(openItems.get(openRenderer.selectedIndex));
        }
        if (dayRenderer != null && dayRenderer.selectedIndex >= 0 && dayRenderer.selectedIndex < dayItems.size()) {
            return java.util.Optional.of(dayItems.get(dayRenderer.selectedIndex));
        }
        return java.util.Optional.empty();
    }

    /** R-LIST-4: moves the highlight to the selected row of the window. Only the list it was clicked in shows it. */
    void showSelection(ViewState.RowKey key, ViewState.Origin origin) {
        if (openRenderer != null) {
            openRenderer.selectedIndex = key != null && origin == ViewState.Origin.OPEN_LIST ? indexOf(openItems, key) : -1;
            openList.repaint();
        }
        if (dayRenderer != null) {
            dayRenderer.selectedIndex = key != null && origin == ViewState.Origin.DAY_LIST ? indexOf(dayItems, key) : -1;
            dayList.repaint();
        }
        if (editButton != null) {
            boolean selected = selectedOccurrence().isPresent();
            editButton.setEnabled(selected);
            deleteButton.setEnabled(selected);
        }
    }

    private static int indexOf(List<Occurrence> items, ViewState.RowKey key) {
        for (int i = 0; i < items.size(); i++) {
            if (ViewState.RowKey.of(items.get(i)).equals(key)) {
                return i;
            }
        }
        return -1;
    }

    /** First run: the import functions are the fastest way to fill the calendar, so they are offered here. */
    private void addGettingStarted() {
        add(heading("Start by importing"));
        add(line("<html>DayFlow has no schedules yet.<br>You can fill it without typing:</html>"));
        add(Box.createVerticalStrut(10));
        JButton timetable = new JButton("Import timetable image...");
        timetable.setEnabled(events.canImport());
        timetable.addActionListener(e -> events.importTimetable());
        add(left(timetable));
        add(line("<html><font color='#5F6368'>A whole semester of classes from the image<br>"
                + "saved in the Everytime app. Works offline.</font></html>"));
        add(Box.createVerticalStrut(12));
        JButton paste = new JButton("Paste a job posting or notice");
        if (events.pasteDeferred()) {
            paste.setEnabled(false);
            paste.setToolTipText("Coming in the next version");
        }
        add(left(paste));
        // D-30: the beta has no key feature, so the live window says so. The report figure keeps the design.
        String pasteHelp = events.pasteDeferred()
                ? "Coming in the next version."
                : "Copy a posting URL or any text with dates,<br>then come back to this window.<br>"
                + "Needs a Gemini API key (File menu).";
        add(line("<html><font color='#5F6368'>" + pasteHelp + "</font></html>"));
        add(Box.createVerticalStrut(12));
        JButton newSchedule = new JButton("+ New schedule");
        newSchedule.addActionListener(e -> events.add(selectedDate, null));
        add(left(newSchedule));
        add(Box.createVerticalGlue());
    }

    /**
     * A deadline is drawn with a checkbox, other entries with a coloured bar.
     * Clicking the box toggles done, clicking the text selects the row, and double-clicking the text opens it.
     * The time comes before the title, and only the title is shortened when the row is too narrow,
     * so a long title never pushes the due time out of sight.
     */
    private static final class RowRenderer implements ListCellRenderer<Occurrence> {
        private static final Color FADED = new Color(0x9AA0A6);
        private final LocalDate today;
        private final boolean withDDay;
        /** The row drawn as selected, or -1. Set from the state of the window. */
        int selectedIndex = -1;

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
            boolean selected = index == selectedIndex;
            row.setOpaque(selected);
            if (selected) {
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
