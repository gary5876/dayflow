package dayflow.ui;

import dayflow.model.Occurrence;
import dayflow.model.Priority;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * What the panels report when the user does something in them. The panels only call this, and the main
 * window decides what happens (D-27). The snapshot windows of the report figures use {@link #NONE}.
 */
interface PanelEvents {
    /** The events of a panel that nobody listens to. */
    PanelEvents NONE = new PanelEvents() { };

    /** R-CAL-6: a day was clicked. */
    default void selectDate(LocalDate date) { }

    /** F-06: a new schedule on the date. {@code start} is the 30 minutes clicked in the week, or null. */
    default void add(LocalDate date, LocalTime start) { }

    /** R-LIST-2: the text of a row was clicked in the given list. */
    default void select(Occurrence occurrence, ViewState.Origin origin) { }

    /** R-LIST-3: the text of a row was double-clicked, or Edit was pressed. */
    default void edit(Occurrence occurrence) { }

    /** R-LIST-5: Delete was pressed on the selected row. */
    default void delete(Occurrence occurrence) { }

    /** R-LIST-1: the checkbox of a deadline was clicked. */
    default void toggleDone(Occurrence occurrence) { }

    /** R-LT-10: the priority was chosen in the table. */
    default void setPriority(Occurrence occurrence, Priority priority) { }

    /** R-LT-1: a filter of the List tab changed. */
    default void setFilters(ListPanel.Filters filters) { }

    /** R-LT-7: Reset was pressed. */
    default void resetFilters() { }

    /** R-DL-5: the period N of the open deadlines was changed in the sidebar. */
    default void setOpenDeadlineDays(int days) { }

    /** R-LT-12: Show all in List was clicked. */
    default void showAllInList() { }

    /** D-30: whether a new import can start, which the start button of the sidebar follows. */
    default boolean canImport() {
        return true;
    }

    /** D-30: whether the paste import is left for the next version, so its button is disabled. */
    default boolean pasteDeferred() {
        return false;
    }

    /** F-03: the start button of the sidebar was pressed. */
    default void importTimetable() { }

    /** The List table scrolled to this position. */
    default void listScrolled(int value) { }

    /** F-15: Plan my week was pressed. */
    default void planWeek() { }

    /** R-PLAN-8: Clear plan was pressed. */
    default void clearPlan() { }
}
