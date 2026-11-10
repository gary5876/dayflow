package dayflow.ui;

import dayflow.model.Occurrence;
import dayflow.store.Settings;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * What the main window shows: today, the selected date, the tab, the selected row, the filters of the List
 * tab and the deadlines checked off a moment ago (D-27). Every redraw rebuilds the panels, so whatever the
 * user set must live here and not in a panel. Whoever changes it does so here, and the listeners redraw.
 */
public final class ViewState {
    /** The list a selected row was clicked in. Only that list draws the row as selected (R-LIST-4). */
    enum Origin { OPEN_LIST, DAY_LIST, TABLE }

    /**
     * An occurrence by schedule id and date. Undo puts back copies of the schedules, so an {@code Occurrence}
     * from before it points at an object that is gone. The id and the date still find the row.
     */
    record RowKey(String scheduleId, LocalDate date) {
        static RowKey of(Occurrence o) {
            return new RowKey(o.schedule().id(), o.date());
        }
    }

    private final Clock clock;
    private final Settings settings;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final List<Runnable> selectionListeners = new CopyOnWriteArrayList<>();
    private final Set<RowKey> justDone = new LinkedHashSet<>();
    private RowKey selectedRow;
    private Origin selectedOrigin;
    private ListPanel.Filters filters;
    private int listScroll;
    private LocalDate today;
    private LocalDate selected;
    private int tab = MainFrame.CALENDAR_TAB;

    /**
     * Creates the state.
     *
     * @param clock the source of the current time
     */
    public ViewState(Clock clock) {
        this(clock, Settings.inMemory());
    }

    /**
     * Creates the state with the settings of the data folder.
     *
     * @param clock the source of the current time
     * @param settings where the open deadline period N is kept (R-DL-5)
     */
    public ViewState(Clock clock, Settings settings) {
        this.clock = clock;
        this.settings = settings;
        this.today = LocalDate.now(clock);
        this.selected = today;
    }

    /**
     * Returns today.
     *
     * @return today
     */
    public LocalDate today() {
        return today;
    }

    /**
     * Returns the selected date.
     *
     * @return the selected date
     */
    public LocalDate selected() {
        return selected;
    }

    /** The current time from the same clock as {@link #today()}, for the parts of the screen that need the hour. */
    LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    /**
     * Returns the index of the current tab.
     *
     * @return the index of the current tab
     */
    public int tab() {
        return tab;
    }

    /**
     * R-NAV-4: the selected date stays when the tab changes.
     *
     * @param tab the index of the tab
     */
    public void setTab(int tab) {
        if (tab != this.tab) {
            this.tab = tab;
            justDone.clear();
            changed();
        }
    }

    /**
     * Selects a date.
     *
     * @param date the date to select
     */
    public void select(LocalDate date) {
        if (!date.equals(selected)) {
            selected = date;
            justDone.clear();
            changed();
        }
    }

    /** R-NAV-2: one month back in the calendar, 7 days in the week. The list has no period. */
    public void previous() {
        move(-1);
    }

    /** R-NAV-2: one month ahead in the calendar, 7 days in the week. The list has no period. */
    public void next() {
        move(1);
    }

    /** R-NAV-3. */
    public void goToday() {
        select(today);
    }

    /**
     * The Sunday on or before the selected date.
     *
     * @return the first day of the week
     */
    public LocalDate weekStart() {
        return selected.minusDays(selected.getDayOfWeek().getValue() % 7);
    }

    /**
     * R-TODAY-1 and R-TODAY-3: looks at the clock. A selection that sat on the old today moves to the new
     * one, and any other selection stays.
     */
    public void refreshToday() {
        LocalDate now = LocalDate.now(clock);
        if (now.equals(today)) {
            return;
        }
        boolean followToday = selected.equals(today);
        today = now;
        if (followToday) {
            selected = now;
        }
        justDone.clear();
        changed();
    }

    /** R-LIST-4: the one selected row of the window, or null before anything is selected. */
    RowKey selectedRow() {
        return selectedRow;
    }

    /** The list the selected row was clicked in, or null when no row is selected. */
    Origin selectedOrigin() {
        return selectedOrigin;
    }

    /**
     * R-LIST-2 and R-LIST-4: selects the row and drops the selection of every other list. Only the
     * selection listeners hear it, because a selection must not rebuild the panel that is being clicked.
     */
    void selectRow(RowKey key, Origin origin) {
        if (key.equals(selectedRow) && origin == selectedOrigin) {
            return;
        }
        selectedRow = key;
        selectedOrigin = origin;
        for (Runnable listener : selectionListeners) {
            listener.run();
        }
    }

    /** Called with the redraw of a change, so the redraw it causes is the only one. */
    void clearSelection() {
        selectedRow = null;
        selectedOrigin = null;
    }

    /** Heard when the selected row changes, so the lists can move their highlight without a redraw. */
    void addSelectionListener(Runnable listener) {
        selectionListeners.add(listener);
    }

    /** R-DONE-3: the occurrences completed just now. They stay in the open lists until something else happens. */
    Set<RowKey> justDone() {
        return Set.copyOf(justDone);
    }

    /** Remembers a row checked off. Silent, because the change that marks it done redraws. */
    void rememberJustDone(RowKey key) {
        justDone.add(key);
    }

    /** Lets the rows checked off earlier go. Silent, because the change that follows redraws. */
    void forgetJustDone() {
        justDone.clear();
    }

    /** The filters and search of the List tab. Until the user changes them, the state of R-LT-1. */
    ListPanel.Filters filters() {
        return filters != null ? filters : ListPanel.Filters.initial(today);
    }

    /** R-LT-6 and R-DONE-3: a change of filter or search lets the rows checked off earlier go. */
    void setFilters(ListPanel.Filters value) {
        filters = value;
        listScroll = 0;
        justDone.clear();
        changed();
    }

    /** R-LT-7: back to the state of R-LT-1, which also clears the search. */
    void resetFilters() {
        setFilters(ListPanel.Filters.initial(today));
    }

    /**
     * R-LT-6 and R-LT-7: a search goes to the List tab and looks at all dates. An empty text clears the
     * search and returns to R-LT-1.
     */
    void search(String text) {
        String keyword = text == null || text.isBlank() ? null : text.strip();
        filters = keyword == null ? ListPanel.Filters.initial(today) : filters().withKeyword(keyword);
        tab = MainFrame.LIST_TAB;
        listScroll = 0;
        justDone.clear();
        changed();
    }

    /**
     * R-LT-12 and R-DL-7: opens the List tab with only {@code Deadlines only} and {@code Open only} on, from
     * the state of R-LT-1, and clears the search.
     */
    void showAllInList() {
        ListPanel.Filters base = ListPanel.Filters.initial(today);
        filters = new ListPanel.Filters(base.from(), base.to(), base.category(), true, true, base.hideRepeating(),
                null);
        tab = MainFrame.LIST_TAB;
        listScroll = 0;
        justDone.clear();
        changed();
    }

    /** R-DL-5: the period N of the open deadline list, from 1 to 30. */
    int openDeadlineDays() {
        return settings.openDeadlineDays();
    }

    /**
     * R-DL-5 and D-31: sets the period N and saves it. A value that cannot be saved still holds until the
     * program ends.
     */
    void setOpenDeadlineDays(int days) {
        if (days == settings.openDeadlineDays()) {
            return;
        }
        settings.setOpenDeadlineDays(days);
        // R-DONE-3: a change of N lets the rows checked off earlier go.
        justDone.clear();
        changed();
    }

    /** The scroll position of the List table, kept so a redraw does not send it back to the top. */
    int listScroll() {
        return listScroll;
    }

    void listScroll(int value) {
        listScroll = value;
    }

    /**
     * Adds a listener that is called when the view state changes.
     *
     * @param listener the listener to add
     */
    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    private void move(int direction) {
        switch (tab) {
            case MainFrame.CALENDAR_TAB -> select(selected.plusMonths(direction));
            case MainFrame.WEEK_TAB -> select(selected.plusDays(7L * direction));
            default -> { }
        }
    }

    private void changed() {
        for (Runnable listener : listeners) {
            listener.run();
        }
    }
}
