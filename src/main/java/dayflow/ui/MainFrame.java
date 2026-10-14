package dayflow.ui;

import dayflow.model.Occurrence;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;

/** Main window: toolbar, import banner, Calendar, Week and List tabs, briefing sidebar, status bar. */
final class MainFrame extends JFrame {
    static final int CALENDAR_TAB = 0;
    static final int WEEK_TAB = 1;
    static final int LIST_TAB = 2;

    /** What the import banner shows. */
    enum BannerKind { OFFER, ANALYZING, FOUND, FAILED, NO_KEY }

    /** {@code buttons} is only used by FAILED, whose buttons depend on the reason. */
    record Banner(BannerKind kind, String text, String... buttons) {
    }

    /** The state of the window to draw. Fields left unset keep their defaults. */
    static final class View {
        final int tab;
        final LocalDate selected;
        final ListPanel.Filters filters;
        Banner banner;
        boolean importOn = true;
        String statusMessage;
        boolean undoable;
        boolean saved = true;
        Set<Occurrence> justDone = Set.of();
        int selectedDayRow = -1;
        int selectedListRow = -1;

        View(int tab, LocalDate selected, ListPanel.Filters filters) {
            this.tab = tab;
            this.selected = selected;
            this.filters = filters;
        }

        View banner(BannerKind kind, String text, String... buttons) {
            this.banner = new Banner(kind, text, buttons);
            return this;
        }

        View importOff() {
            this.importOn = false;
            return this;
        }

        /** What the status bar says; every change to the schedules can be undone, "Undone." cannot. */
        View status(String message, boolean canUndo) {
            this.statusMessage = message;
            this.undoable = canUndo;
            return this;
        }

        View notSaved() {
            this.saved = false;
            return this;
        }

        /** Deadlines checked off a moment ago, which stay in the open list until something else happens. */
        View justDone(Set<Occurrence> occurrences) {
            this.justDone = occurrences;
            return this;
        }

        /** Only one row is selected in the whole window. */
        View selectDayRow(int row) {
            this.selectedDayRow = row;
            this.selectedListRow = -1;
            return this;
        }

        View selectListRow(int row) {
            this.selectedListRow = row;
            this.selectedDayRow = -1;
            return this;
        }
    }

    MainFrame(List<Occurrence> schedules, LocalDateTime now, View view) {
        super("DayFlow");
        LocalDate today = now.toLocalDate();
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setJMenuBar(menuBar());
        LocalDate weekStart = view.selected.minusDays(view.selected.getDayOfWeek().getValue() % 7);

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(toolbar(today, weekStart, view));
        if (view.banner != null) {
            top.add(banner(view.banner));
        }
        add(top, BorderLayout.NORTH);

        CalendarPanel calendar = new CalendarPanel(schedules, YearMonth.from(view.selected), today);
        calendar.select(view.selected);
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Calendar", calendar);
        tabs.addTab("Week", new WeekPanel(schedules, weekStart, now));
        tabs.addTab("List", new ListPanel(schedules, today, view.filters, view.selectedListRow));
        tabs.setSelectedIndex(view.tab);
        add(tabs, BorderLayout.CENTER);

        add(SidebarPanel.wrap(new SidebarPanel(schedules, today, view.selected, 3, view.selectedDayRow, view.justDone)),
                BorderLayout.EAST);
        add(statusBar(schedules.isEmpty(), view), BorderLayout.SOUTH);
        setSize(1240, 850);
    }

    private static JMenuBar menuBar() {
        JMenuBar bar = new JMenuBar();
        JMenu file = new JMenu("File");
        file.add("Import Timetable Image...");
        file.add("Paste and Import        Cmd+Shift+V");
        file.addSeparator();
        // The clipboard is only looked at while this is on.
        file.add(new JCheckBoxMenuItem("Offer to import from clipboard", true));
        file.add("Gemini API Key...");
        file.addSeparator();
        file.add("Exit");
        bar.add(file);
        bar.add(new JMenu("Help"));
        return bar;
    }

    private static JPanel toolbar(LocalDate today, LocalDate weekStart, View view) {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

        // The arrows move by month in the calendar and by week in the timetable; the list has no period.
        boolean movable = view.tab != LIST_TAB;
        DateTimeFormatter day = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
        String period = view.tab == WEEK_TAB ? weekLabel(weekStart, day)
                : YearMonth.from(view.selected).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        left.add(new JButton("+ New"));
        left.add(Box.createHorizontalStrut(12));
        left.add(enabled(new JButton("<"), movable));
        JLabel title = new JLabel(period);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        title.setEnabled(movable);
        left.add(title);
        left.add(enabled(new JButton(">"), movable));
        left.add(enabled(new JButton("Today"), movable));
        bar.add(left, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        String keyword = view.filters.keyword();
        JTextField search = new JTextField(keyword == null ? "" : keyword, 18);
        search.putClientProperty("JTextField.variant", "search");
        right.add(search);
        right.add(new JButton("Search"));
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    /** "Oct 11 - Oct 17, 2026"; a week across New Year names both years. */
    static String weekLabel(LocalDate weekStart, DateTimeFormatter day) {
        LocalDate weekEnd = weekStart.plusDays(6);
        return weekStart.getYear() == weekEnd.getYear()
                ? weekStart.format(day) + " - " + weekEnd.format(day) + ", " + weekEnd.getYear()
                : weekStart.format(day) + ", " + weekStart.getYear() + " - " + weekEnd.format(day) + ", " + weekEnd.getYear();
    }

    private static JButton enabled(JButton button, boolean enabled) {
        button.setEnabled(enabled);
        return button;
    }

    /**
     * Import banner. An offer appears when the clipboard holds a URL or text with a date;
     * nothing is sent until the user clicks Create schedules. When the analysis is finished the
     * banner announces the result and waits for Review, so it never interrupts what the user is doing.
     */
    private static JPanel banner(Banner b) {
        JPanel banner = new JPanel(new BorderLayout(8, 0));
        Color fill = switch (b.kind()) {
            case NO_KEY -> new Color(0xF1F3F4);
            case FOUND -> new Color(0xE6F4EA);
            case FAILED -> new Color(0xFCE8E6);
            default -> new Color(0xFFF4CE);
        };
        Color line = switch (b.kind()) {
            case NO_KEY -> new Color(0xD0D0D0);
            case FOUND -> new Color(0x9BD0A8);
            case FAILED -> new Color(0xE5A9A3);
            default -> new Color(0xE6C766);
        };
        banner.setBackground(fill);
        banner.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 1, 0, line),
                BorderFactory.createEmptyBorder(4, 12, 4, 8)));
        banner.add(new JLabel(b.text()), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setOpaque(false);
        switch (b.kind()) {
            case OFFER -> {
                buttons.add(new JButton("Create schedules"));
                buttons.add(new JButton("Dismiss"));
            }
            case ANALYZING -> {
                JProgressBar progress = new JProgressBar();
                progress.setIndeterminate(true);
                progress.setPreferredSize(new Dimension(160, 14));
                buttons.add(progress);
                buttons.add(new JButton("Cancel"));
            }
            case FOUND -> {
                buttons.add(new JButton("Review"));
                buttons.add(new JButton("Dismiss"));
            }
            case FAILED -> {
                // A failed background import is reported here, never in a window that interrupts.
                for (String label : b.buttons()) {
                    buttons.add(new JButton(label));
                }
                buttons.add(new JButton("Dismiss"));
            }
            case NO_KEY -> {
                buttons.add(new JButton("Set key..."));
                buttons.add(new JButton("Add manually"));
                buttons.add(new JButton("Dismiss"));
            }
        }
        banner.add(buttons, BorderLayout.EAST);
        banner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        return banner;
    }

    private static JPanel statusBar(boolean empty, View view) {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0xD0D0D0)),
                BorderFactory.createEmptyBorder(3, 10, 3, 10)));
        // Undo follows every change to the schedules.
        String saved = view.saved ? "<font color='#5F6368'>Saved at 10:42</font>"
                : "<font color='#D93025'><b>Not saved</b></font>";
        String undo = !view.undoable ? "" : " &nbsp;<font color='#1A73E8'><u>Undo</u></font>";
        String left = "<html>" + (empty ? "New data file created." : view.statusMessage == null ? "" : view.statusMessage)
                + undo + " &nbsp;" + saved + "</html>";
        bar.add(new JLabel(left), BorderLayout.WEST);
        bar.add(new JLabel("Import by URL and text: " + (view.importOn ? "on" : "off (no API key)")),
                BorderLayout.EAST);
        return bar;
    }
}
