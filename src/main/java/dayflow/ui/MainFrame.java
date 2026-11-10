package dayflow.ui;

import dayflow.model.Occurrence;
import dayflow.model.Schedule;
import dayflow.store.Change;
import dayflow.store.ScheduleBook;
import dayflow.store.Settings;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.Timer;
import javax.swing.WindowConstants;

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

    /** What the buttons of the banner do. The banner of a snapshot has buttons that do nothing. */
    private record BannerActions(Runnable review, Runnable dismiss, Runnable cancel) {
        static final BannerActions NONE = new BannerActions(() -> { }, () -> { }, () -> { });
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

        /** What the status bar says. Every change to the schedules can be undone but "Undone." cannot. */
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

    /** The buttons of the toolbar. A frame drawn from a snapshot has none, so its buttons do nothing. */
    private record Navigation(Runnable previous, Runnable next, Runnable today, Runnable add,
                              Consumer<String> search) {
        static final Navigation NONE = new Navigation(() -> { }, () -> { }, () -> { }, () -> { }, text -> { });
    }

    /** What the panels report in the live window. Selections go to the state, and the rest to the actions. */
    private final class LiveEvents implements PanelEvents {
        @Override
        public void selectDate(LocalDate date) {
            state.select(date);
        }

        @Override
        public void add(LocalDate date, LocalTime start) {
            actions.add(date, start);
        }

        @Override
        public void select(Occurrence occurrence, ViewState.Origin origin) {
            state.selectRow(ViewState.RowKey.of(occurrence), origin);
        }

        @Override
        public void edit(Occurrence occurrence) {
            actions.edit(occurrence);
        }

        @Override
        public void delete(Occurrence occurrence) {
            actions.delete(occurrence);
        }

        @Override
        public void toggleDone(Occurrence occurrence) {
            actions.toggleDone(occurrence);
        }

        @Override
        public void setPriority(Occurrence occurrence, dayflow.model.Priority priority) {
            actions.setPriority(occurrence, priority);
        }

        @Override
        public void setFilters(ListPanel.Filters filters) {
            state.setFilters(filters);
        }

        @Override
        public void resetFilters() {
            state.resetFilters();
        }

        @Override
        public void setOpenDeadlineDays(int days) {
            state.setOpenDeadlineDays(days);
        }

        @Override
        public void showAllInList() {
            state.showAllInList();
        }

        @Override
        public void listScrolled(int value) {
            state.listScroll(value);
        }

        @Override
        public void planWeek() {
            actions.planWeek();
        }

        @Override
        public void clearPlan() {
            actions.clearPlan();
        }

        @Override
        public boolean pasteDeferred() {
            return true;
        }

        @Override
        public boolean canImport() {
            return imports.canStart();
        }

        @Override
        public void importTimetable() {
            imports.chooseAndStart();
        }
    }

    private static final String LATER = "Coming in the next version";
    private static final String SAVE_FAILED_EXIT = "The data could not be saved. Exit without saving?";

    private final ScheduleBook book;
    private final ViewState state;
    private final ScheduleActions actions;
    private final TimetableImport imports;
    private BannerActions bannerActions = BannerActions.NONE;
    private JMenuItem importItem;
    private final Timer dayCheck;
    private final LocalTime openedAt;
    private PanelEvents events = PanelEvents.NONE;
    private SidebarPanel sidebar;
    private ListPanel listPanel;
    private String startupMessage = "";
    private boolean startupSaveFailed;

    /** Draws one snapshot, which is how the figures of the report are made. */
    MainFrame(List<Occurrence> schedules, LocalDateTime now, View view) {
        super("DayFlow");
        this.book = null;
        this.state = null;
        this.actions = null;
        this.imports = null;
        this.dayCheck = null;
        this.openedAt = null;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setJMenuBar(menuBar(null, null));
        draw(schedules, now, view.tab, view.selected, view, Navigation.NONE, null, null);
        setSize(1240, 850);
    }

    /** The live window. It redraws from the book and the view state whenever either one changes. */
    MainFrame(ScheduleBook book, ViewState state, ScheduleActions actions) {
        super("DayFlow");
        this.book = book;
        this.state = state;
        this.actions = actions;
        this.openedAt = state.now().toLocalTime();
        this.events = new LiveEvents();
        this.imports = new TimetableImport(book, actions, state, () -> this, TimetableImport::installedRecognizer,
                this::redraw);
        this.bannerActions = new BannerActions(imports::review, imports::dismiss, imports::cancel);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setJMenuBar(menuBar(this::exit, imports::chooseAndStart));
        installQuitHandler();
        setTransferHandler(imageDrop());
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                exit();
            }
        });
        // R-TODAY-1: the day is checked when the window gets focus and once a minute.
        addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowGainedFocus(WindowEvent e) {
                state.refreshToday();
            }
        });
        dayCheck = new Timer(60_000, e -> state.refreshToday());
        dayCheck.start();
        book.addListener(change -> redraw());
        state.addListener(this::redraw);
        state.addSelectionListener(this::refreshSelection);
        redraw();
        setSize(1240, 850);
    }

    /**
     * R-IMP-19 and R-SAVE-4: Cmd+Q and the Quit item of the macOS app menu ask the same exit questions as the
     * window close button. Only the live window installs this, never the snapshot one.
     */
    private void installQuitHandler() {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.APP_QUIT_HANDLER)) {
            return;
        }
        Desktop.getDesktop().setQuitHandler((event, response) -> {
            if (exit()) {
                response.performQuit();
            } else {
                response.cancelQuit();
            }
        });
    }

    /** R-LIST-4: moves the highlight of the lists without rebuilding them. */
    private void refreshSelection() {
        listPanel.showSelection(state.selectedRow(), state.selectedOrigin());
        sidebar.showSelection(state.selectedRow(), state.selectedOrigin());
    }

    /** Shown in the status bar until the first change, such as {@code New data file created.}. */
    void startupMessage(String message) {
        this.startupMessage = message;
        redraw();
    }

    /** True when the empty file written at the start could not be saved, so the bar says Not saved (R-SAVE-4). */
    void startupSaveFailed(boolean failed) {
        this.startupSaveFailed = failed;
        redraw();
    }

    /** Whether the status bar says Saved at. Before any change it depends on the save at the start. */
    static boolean showsSaved(Change last, boolean startupSaveFailed) {
        return last == null ? !startupSaveFailed : !last.saveFailed();
    }

    /** R-SAVE-1 and R-SAVE-4: saves for the exit, and asks when that fails. */
    private boolean exit() {
        // R-IMP-19: the question comes first. A running analysis is thrown away.
        if (imports.unreviewed() && JOptionPane.showConfirmDialog(this, "An import has not been reviewed. Exit anyway?",
                "DayFlow", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return false;
        }
        if (imports.analyzing()) {
            imports.cancel();
        }
        if (!book.close()) {
            int answer = JOptionPane.showConfirmDialog(this, SAVE_FAILED_EXIT, "DayFlow", JOptionPane.YES_NO_OPTION);
            if (answer != JOptionPane.YES_OPTION) {
                return false;
            }
        }
        dayCheck.stop();
        dispose();
        return true;
    }

    private void redraw() {
        LocalDate today = state.today();
        LocalDate selected = state.selected();
        // Overdue deadlines stay on the list however old they are, so the range starts at the earliest date.
        LocalDate from = today;
        for (Schedule s : book.schedules()) {
            if (s.date().isBefore(from)) {
                from = s.date();
            }
        }
        LocalDate to = (selected.isAfter(today) ? selected : today).plusYears(2);
        List<Occurrence> occurrences = book.occurrences(from, to);

        // R-DONE-3: the keys in the state become the occurrences of this redraw.
        Set<ViewState.RowKey> doneKeys = state.justDone();
        Set<Occurrence> justDone = occurrences.stream().filter(o -> doneKeys.contains(ViewState.RowKey.of(o)))
                .collect(Collectors.toSet());
        View view = new View(state.tab(), selected, state.filters()).importOff().justDone(justDone);
        view.banner = imports.banner();
        importItem.setEnabled(imports.canStart());
        Change last = book.lastChange().orElse(null);
        DateTimeFormatter hourMinute = DateTimeFormatter.ofPattern("HH:mm");
        String saved;
        if (last == null) {
            saved = showsSaved(null, startupSaveFailed) ? openedAt.format(hourMinute) : null;
            if (saved == null) {
                view.notSaved();
            }
            view.statusMessage = startupMessage;
        } else {
            view.status(last.statusText(), last.canUndo());
            if (last.saveFailed()) {
                view.notSaved();
            }
            saved = last.saveFailed() ? null : last.savedAt().format(hourMinute);
        }
        draw(occurrences, state.now(), state.tab(), selected, view,
                new Navigation(state::previous, state::next, state::goToday, () -> actions.add(state.selected()),
                        state::search), saved, actions::undo);
        // The table needs its size before a scroll position can be set, and a hidden tab has none.
        getContentPane().validate();
        if (state.tab() == LIST_TAB) {
            listPanel.keepScroll(state.listScroll(), state::listScroll);
        }
        getContentPane().repaint();
    }

    /**
     * Puts the toolbar, tabs, sidebar and status bar in the window. Both the snapshot and the live window
     * come through here, so they look the same.
     *
     * @param saved the time after {@code Saved at}, or null to take the one of the snapshot
     * @param undo what the Undo button does, or null for the plain Undo link of a snapshot
     */
    private void draw(List<Occurrence> schedules, LocalDateTime now, int tab, LocalDate selected, View view,
                      Navigation navigation, String saved, Runnable undo) {
        getContentPane().removeAll();
        LocalDate today = now.toLocalDate();
        int lookAheadDays = state != null ? state.openDeadlineDays() : Settings.DEFAULT_OPEN_DEADLINE_DAYS;
        LocalDate weekStart = selected.minusDays(selected.getDayOfWeek().getValue() % 7);

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(toolbar(weekStart, view, navigation));
        if (view.banner != null) {
            top.add(banner(view.banner, bannerActions));
        }
        add(top, BorderLayout.NORTH);

        CalendarPanel calendar = new CalendarPanel(schedules, YearMonth.from(selected), today, events);
        calendar.select(selected);
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Calendar", calendar);
        tabs.addTab("Week", new WeekPanel(schedules, weekStart, now, events));
        listPanel = new ListPanel(schedules, today, view.filters, view.selectedListRow, view.justDone, events);
        tabs.addTab("List", listPanel);
        tabs.setSelectedIndex(tab);
        if (state != null) {
            // The change comes later, because this event happens inside the tabs that the redraw replaces.
            tabs.addChangeListener(e -> {
                int index = tabs.getSelectedIndex();
                SwingUtilities.invokeLater(() -> state.setTab(index));
            });
        }
        add(tabs, BorderLayout.CENTER);

        sidebar = new SidebarPanel(schedules, today, selected, lookAheadDays, view.selectedDayRow, view.justDone,
                events);
        add(SidebarPanel.wrap(sidebar), BorderLayout.EAST);
        if (state != null) {
            // R-LIST-4 and R-UNDO-4: the selected row of the window survives a redraw while the row exists.
            listPanel.showSelection(state.selectedRow(), state.selectedOrigin());
            sidebar.showSelection(state.selectedRow(), state.selectedOrigin());
        }
        add(statusBar(state == null && schedules.isEmpty(), view, saved, undo), BorderLayout.SOUTH);
    }

    /**
     * The menu. In the beta, an item for something that has not been built yet is shown but disabled.
     * The ones that the URL and text import, the API key and the clipboard need say so in a tooltip.
     * Timetable import works. Without {@code exit}, the menu is the plain one that the report figure shows.
     */
    private JMenuBar menuBar(Runnable exit, Runnable importTimetable) {
        boolean live = exit != null;
        JMenuBar bar = new JMenuBar();
        JMenu file = new JMenu("File");
        JMenuItem timetable = new JMenuItem("Import Timetable Image...");
        if (live) {
            timetable.addActionListener(e -> importTimetable.run());
        }
        file.add(timetable);
        importItem = timetable;
        file.add(item("Paste and Import        Cmd+Shift+V", live, LATER));
        file.addSeparator();
        // The clipboard is only looked at while this is on.
        JCheckBoxMenuItem offer = new JCheckBoxMenuItem("Offer to import from clipboard", true);
        if (live) {
            // The beta does not look at the clipboard, so the box must not claim that it does.
            offer.setSelected(false);
            offer.setEnabled(false);
            offer.setToolTipText(LATER);
        }
        file.add(offer);
        file.add(item("Gemini API Key...", live, LATER));
        file.addSeparator();
        JMenuItem exitItem = file.add("Exit");
        if (live) {
            exitItem.addActionListener(e -> exit.run());
        }
        bar.add(file);
        bar.add(new JMenu("Help"));
        return bar;
    }

    private static JMenuItem item(String text, boolean live, String tooltip) {
        JMenuItem item = new JMenuItem(text);
        if (live) {
            item.setEnabled(false);
            item.setToolTipText(tooltip);
        }
        return item;
    }

    /** F-03: an image file dropped on the window starts the same import as the menu. */
    private TransferHandler imageDrop() {
        return new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor) && imports.canStart();
            }

            @Override
            public boolean importData(TransferSupport support) {
                if (!canImport(support)) {
                    return false;
                }
                try {
                    @SuppressWarnings("unchecked")
                    List<File> files = (List<File>) support.getTransferable()
                            .getTransferData(DataFlavor.javaFileListFlavor);
                    for (File file : files) {
                        if (TimetableImport.isImage(file.toPath())) {
                            imports.start(file.toPath());
                            return true;
                        }
                    }
                } catch (UnsupportedFlavorException | IOException e) {
                    return false;
                }
                return false;
            }
        };
    }

    private static JPanel toolbar(LocalDate weekStart, View view, Navigation navigation) {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

        // The arrows move by month in the calendar and by week in the timetable. The list has no period.
        boolean movable = view.tab != LIST_TAB;
        DateTimeFormatter day = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
        String period = view.tab == WEEK_TAB ? weekLabel(weekStart, day)
                : YearMonth.from(view.selected).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        left.add(button("+ New", navigation.add()));
        left.add(Box.createHorizontalStrut(12));
        left.add(enabled(button("<", navigation.previous()), movable));
        JLabel title = new JLabel(period);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        title.setEnabled(movable);
        left.add(title);
        left.add(enabled(button(">", navigation.next()), movable));
        left.add(enabled(button("Today", navigation.today()), movable));
        bar.add(left, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        String keyword = view.filters.keyword();
        JTextField search = new JTextField(keyword == null ? "" : keyword, 18);
        search.putClientProperty("JTextField.variant", "search");
        right.add(search);
        // R-LT-6 and R-LT-7: Enter and Search look for the text, and an empty text clears the search.
        search.addActionListener(e -> navigation.search().accept(search.getText()));
        right.add(button("Search", () -> navigation.search().accept(search.getText())));
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    /** "Oct 11 - Oct 17, 2026", and a week across New Year names both years. */
    static String weekLabel(LocalDate weekStart, DateTimeFormatter day) {
        LocalDate weekEnd = weekStart.plusDays(6);
        return weekStart.getYear() == weekEnd.getYear()
                ? weekStart.format(day) + " - " + weekEnd.format(day) + ", " + weekEnd.getYear()
                : weekStart.format(day) + ", " + weekStart.getYear() + " - " + weekEnd.format(day) + ", " + weekEnd.getYear();
    }

    private static JButton button(String text, Runnable action) {
        JButton button = new JButton(text);
        button.addActionListener(e -> action.run());
        return button;
    }

    private static JButton enabled(JButton button, boolean enabled) {
        button.setEnabled(enabled);
        return button;
    }

    /**
     * Import banner. An offer appears when the clipboard holds a URL or text with a date.
     * Nothing is sent until the user clicks Create schedules. When the analysis is finished the
     * banner announces the result and waits for Review, so it never interrupts what the user is doing.
     */
    private static JPanel banner(Banner b, BannerActions actions) {
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
                buttons.add(button("Cancel", actions.cancel()));
            }
            case FOUND -> {
                buttons.add(button("Review", actions.review()));
                buttons.add(button("Dismiss", actions.dismiss()));
            }
            case FAILED -> {
                // A failed background import is reported here, never in a window that interrupts.
                for (String label : b.buttons()) {
                    buttons.add(new JButton(label));
                }
                buttons.add(button("Dismiss", actions.dismiss()));
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

    private static JPanel statusBar(boolean empty, View view, String savedTime, Runnable undo) {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0xD0D0D0)),
                BorderFactory.createEmptyBorder(3, 10, 3, 10)));
        // Undo follows every change to the schedules.
        String saved = view.saved ? "<font color='#5F6368'>Saved at " + (savedTime == null ? "10:42" : savedTime)
                + "</font>" : "<font color='#D93025'><b>Not saved</b></font>";
        String message = empty ? "New data file created." : view.statusMessage == null ? "" : view.statusMessage;
        if (undo == null) {
            String link = !view.undoable ? "" : " &nbsp;<font color='#1A73E8'><u>Undo</u></font>";
            bar.add(new JLabel("<html>" + message + link + " &nbsp;" + saved + "</html>"), BorderLayout.WEST);
        } else {
            // The live window has a real button, which the link of the snapshot only imitates.
            JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            left.add(new JLabel(message));
            if (view.undoable) {
                JButton undoButton = button("Undo", undo);
                undoButton.setForeground(new Color(0x1A73E8));
                left.add(undoButton);
            }
            left.add(new JLabel("<html>" + saved + "</html>"));
            bar.add(left, BorderLayout.WEST);
        }
        // D-30: the live window has no key feature in the beta, so it shows no import state.
        if (undo == null) {
            bar.add(new JLabel("Import by URL and text: " + (view.importOn ? "on" : "off (no API key)")),
                    BorderLayout.EAST);
        }
        return bar;
    }
}
