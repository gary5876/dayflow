package dayflow.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dayflow.store.Settings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ViewStateTest {
    private static final LocalDate THURSDAY = LocalDate.of(2026, 10, 29);

    private static Clock at(LocalDate date) {
        return Clock.fixed(date.atTime(9, 30).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    }

    /** A clock that tests can move, to look at what happens when the day changes. */
    private static final class MovableClock extends Clock {
        private Instant now;

        MovableClock(LocalDate date) {
            this.now = date.atTime(23, 59).toInstant(ZoneOffset.UTC);
        }

        void moveTo(LocalDate date) {
            this.now = date.atTime(0, 1).toInstant(ZoneOffset.UTC);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void startsOnTodayInTheCalendarTab() {
        ViewState view = new ViewState(at(THURSDAY));
        assertEquals(THURSDAY, view.today());
        assertEquals(THURSDAY, view.selected());
        assertEquals(MainFrame.CALENDAR_TAB, view.tab());
    }

    @Test
    void calendarTabMovesByMonth() {
        ViewState view = new ViewState(at(THURSDAY));
        view.next();
        assertEquals(LocalDate.of(2026, 11, 29), view.selected());
        view.previous();
        view.previous();
        assertEquals(LocalDate.of(2026, 9, 29), view.selected());
    }

    @Test
    void calendarTabUsesTheLastDayWhenTheMonthIsShorter() {
        ViewState view = new ViewState(at(LocalDate.of(2026, 1, 31)));
        view.next();
        assertEquals(LocalDate.of(2026, 2, 28), view.selected());
    }

    @Test
    void weekTabMovesBySevenDays() {
        ViewState view = new ViewState(at(THURSDAY));
        view.setTab(MainFrame.WEEK_TAB);
        view.next();
        assertEquals(LocalDate.of(2026, 11, 5), view.selected());
        view.previous();
        view.previous();
        assertEquals(LocalDate.of(2026, 10, 22), view.selected());
    }

    @Test
    void listTabDoesNotMove() {
        ViewState view = new ViewState(at(THURSDAY));
        view.setTab(MainFrame.LIST_TAB);
        view.next();
        view.previous();
        assertEquals(THURSDAY, view.selected());
    }

    @Test
    void changingTheTabKeepsTheSelectedDate() {
        ViewState view = new ViewState(at(THURSDAY));
        view.select(LocalDate.of(2026, 12, 3));
        view.setTab(MainFrame.WEEK_TAB);
        view.setTab(MainFrame.LIST_TAB);
        assertEquals(LocalDate.of(2026, 12, 3), view.selected());
    }

    @Test
    void goTodaySelectsToday() {
        ViewState view = new ViewState(at(THURSDAY));
        view.select(LocalDate.of(2027, 3, 1));
        view.goToday();
        assertEquals(THURSDAY, view.selected());
    }

    @Test
    void weekStartIsTheSundayOfTheSelectedDate() {
        ViewState view = new ViewState(at(THURSDAY));
        assertEquals(LocalDate.of(2026, 10, 25), view.weekStart());
        view.select(LocalDate.of(2026, 11, 1));
        assertEquals(LocalDate.of(2026, 11, 1), view.weekStart());
        view.select(LocalDate.of(2026, 10, 31));
        assertEquals(LocalDate.of(2026, 10, 25), view.weekStart());
    }

    @Test
    void listenersHearEveryChangeButNotARepeatOfTheSameValue() {
        ViewState view = new ViewState(at(THURSDAY));
        List<String> heard = new ArrayList<>();
        view.addListener(() -> heard.add("changed"));
        view.next();
        view.setTab(MainFrame.WEEK_TAB);
        view.setTab(MainFrame.WEEK_TAB);
        view.select(view.selected());
        assertEquals(2, heard.size());
    }

    @Test
    void whenTheDayChangesTheSelectionFollowsOnlyIfItWasOnToday() {
        MovableClock clock = new MovableClock(THURSDAY);
        ViewState onToday = new ViewState(clock);
        ViewState elsewhere = new ViewState(clock);
        elsewhere.select(LocalDate.of(2026, 11, 20));
        List<String> heard = new ArrayList<>();
        onToday.addListener(() -> heard.add("changed"));

        onToday.refreshToday();
        assertEquals(0, heard.size());

        clock.moveTo(LocalDate.of(2026, 10, 30));
        onToday.refreshToday();
        elsewhere.refreshToday();
        assertEquals(LocalDate.of(2026, 10, 30), onToday.today());
        assertEquals(LocalDate.of(2026, 10, 30), onToday.selected());
        assertEquals(1, heard.size());
        assertEquals(LocalDate.of(2026, 10, 30), elsewhere.today());
        assertEquals(LocalDate.of(2026, 11, 20), elsewhere.selected());
    }

    @Test
    void theSelectedRowIsOneForTheWholeWindowAndOnlyTheSelectionListenersHearIt() {
        ViewState view = new ViewState(at(THURSDAY));
        List<String> heard = new ArrayList<>();
        view.addListener(() -> heard.add("redraw"));
        view.addSelectionListener(() -> heard.add("selection"));
        assertNull(view.selectedRow());
        ViewState.RowKey a = new ViewState.RowKey("a", THURSDAY);
        view.selectRow(a, ViewState.Origin.OPEN_LIST);
        view.selectRow(new ViewState.RowKey("b", THURSDAY), ViewState.Origin.TABLE);
        assertEquals(List.of("selection", "selection"), heard);
        assertEquals(ViewState.Origin.TABLE, view.selectedOrigin());
        view.selectRow(new ViewState.RowKey("b", THURSDAY), ViewState.Origin.TABLE);
        assertEquals(2, heard.size());
    }

    @Test
    void filtersAndSearchStayUntilResetAndSearchGoesToTheListTab() {
        ViewState view = new ViewState(at(THURSDAY));
        assertEquals(ListPanel.Filters.initial(THURSDAY), view.filters());
        view.search("  Report ");
        assertEquals("Report", view.filters().keyword());
        assertEquals(MainFrame.LIST_TAB, view.tab());
        view.setTab(MainFrame.WEEK_TAB);
        assertEquals("Report", view.filters().keyword());
        view.search("");
        assertNull(view.filters().keyword());
        view.setFilters(new ListPanel.Filters(THURSDAY, THURSDAY, null, true, true, false, null));
        assertEquals(true, view.filters().openOnly());
        view.resetFilters();
        assertEquals(ListPanel.Filters.initial(THURSDAY), view.filters());
    }

    @Test
    void filterAndSearchChangesLetTheCheckedRowsGo() {
        ViewState view = new ViewState(at(THURSDAY));
        view.rememberJustDone(new ViewState.RowKey("a", THURSDAY));
        view.setFilters(ListPanel.Filters.initial(THURSDAY));
        assertEquals(0, view.justDone().size());
        view.rememberJustDone(new ViewState.RowKey("a", THURSDAY));
        view.search("x");
        assertEquals(0, view.justDone().size());
    }

    @Test
    void changingTheOpenDeadlinePeriodLetsTheCheckedRowsGo() {
        ViewState view = new ViewState(at(THURSDAY));
        view.rememberJustDone(new ViewState.RowKey("a", THURSDAY));
        view.setOpenDeadlineDays(view.openDeadlineDays() + 1);
        assertEquals(0, view.justDone().size());
    }

    @Test
    void theListScrollPositionIsKept() {
        ViewState view = new ViewState(at(THURSDAY));
        view.listScroll(240);
        view.setTab(MainFrame.LIST_TAB);
        assertEquals(240, view.listScroll());
    }

    @Test
    void theListScrollGoesBackToTheTopWhenTheRowsChange() {
        ViewState view = new ViewState(at(THURSDAY));
        view.listScroll(240);
        view.setFilters(ListPanel.Filters.initial(THURSDAY));
        assertEquals(0, view.listScroll());
        view.listScroll(240);
        view.search("report");
        assertEquals(0, view.listScroll());
        view.listScroll(240);
        view.resetFilters();
        assertEquals(0, view.listScroll());
    }

    @Test
    void showAllInListOpensTheListWithOnlyTheDeadlineFiltersOn() {
        ViewState view = new ViewState(at(THURSDAY));
        view.search("Report");
        view.setFilters(view.filters().withKeyword("Report"));
        view.listScroll(40);
        view.setTab(MainFrame.CALENDAR_TAB);

        view.showAllInList();

        ListPanel.Filters initial = ListPanel.Filters.initial(THURSDAY);
        ListPanel.Filters expected = new ListPanel.Filters(initial.from(), initial.to(), initial.category(), true,
                true, initial.hideRepeating(), null);
        assertEquals(expected, view.filters());
        assertEquals(MainFrame.LIST_TAB, view.tab());
        assertEquals(0, view.listScroll());
    }

    @Test
    void showAllInListDropsFiltersSetEarlierAndTellsTheListeners() {
        ViewState view = new ViewState(at(THURSDAY));
        view.setFilters(new ListPanel.Filters(THURSDAY.minusDays(9), THURSDAY.plusDays(1), dayflow.model.Category.JOB,
                false, false, false, "x"));
        List<String> heard = new ArrayList<>();
        view.addListener(() -> heard.add("changed"));

        view.showAllInList();

        assertEquals(1, heard.size());
        assertEquals(THURSDAY, view.filters().from());
        assertEquals(null, view.filters().category());
        assertEquals(true, view.filters().hideRepeating());
        assertEquals(true, view.filters().deadlinesOnly());
        assertEquals(true, view.filters().openOnly());
    }

    @Test
    void openDeadlineDaysStartAtThreeAndAreSavedWhenChanged(@TempDir Path folder) {
        ViewState view = new ViewState(at(THURSDAY), Settings.load(folder));
        List<String> heard = new ArrayList<>();
        view.addListener(() -> heard.add("changed"));
        assertEquals(3, view.openDeadlineDays());

        view.setOpenDeadlineDays(3);
        assertEquals(0, heard.size());
        view.setOpenDeadlineDays(10);

        assertEquals(10, view.openDeadlineDays());
        assertEquals(1, heard.size());
        assertEquals(10, new ViewState(at(THURSDAY), Settings.load(folder)).openDeadlineDays());
    }

    @Test
    void openDeadlineDaysHoldWhenTheFileCannotBeWritten(@TempDir Path folder) throws IOException {
        Files.createDirectories(folder.resolve("settings").resolve("blocker"));
        ViewState view = new ViewState(at(THURSDAY), Settings.load(folder));

        view.setOpenDeadlineDays(7);

        assertEquals(7, view.openDeadlineDays());
    }
}
