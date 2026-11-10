package dayflow.ui;

import dayflow.model.Occurrence;
import dayflow.model.ScheduleDraft;
import dayflow.plan.WorkBlock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * The questions {@link ScheduleActions} asks the user. Swing answers them with windows, and a test answers
 * them with fixed values, so the decisions of an action can be tested without a screen.
 */
interface Dialogs {
    /** R-REP-1: which occurrences an edit or a delete applies to. */
    enum Scope { THIS_ONLY, ALL, CANCEL }

    /** R-REP-1 and R-REP-6: asks for the scope of a repeating schedule. */
    Scope chooseScope(String title);

    /** F-09: asks whether to delete. */
    boolean confirmDelete(String title);

    /** The input window. {@code sameDay} gives the schedules of any date the user moves to (R-FORM-5). */
    ScheduleFormDialog.Result form(ScheduleFormDialog.Use use, ScheduleDraft initial,
                                   Function<LocalDate, List<Occurrence>> sameDay);

    /** R-PLAN-9: tells that the week has no deadline to plan. */
    void nothingToPlan();

    /** R-PLAN-5: shows the proposal. Gives the checked blocks when the user confirms, or nothing on Cancel. */
    Optional<List<WorkBlock>> reviewPlan(WeekPlan.Proposal proposal);
}
