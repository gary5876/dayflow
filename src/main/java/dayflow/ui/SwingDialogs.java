package dayflow.ui;

import dayflow.model.Occurrence;
import dayflow.model.ScheduleDraft;
import dayflow.plan.WorkBlock;
import java.awt.Window;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.JOptionPane;

/** The {@link Dialogs} of the running program: standard pop-ups and the input window. */
final class SwingDialogs implements Dialogs {
    private final Supplier<Window> owner;

    SwingDialogs(Supplier<Window> owner) {
        this.owner = owner;
    }

    @Override
    public Scope chooseScope(String title) {
        String[] options = {"This occurrence only", "All occurrences", "Cancel"};
        int answer = JOptionPane.showOptionDialog(owner.get(), "This schedule repeats: " + title, "DayFlow",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        return switch (answer) {
            case 0 -> Scope.THIS_ONLY;
            case 1 -> Scope.ALL;
            default -> Scope.CANCEL;
        };
    }

    @Override
    public boolean confirmDelete(String title) {
        return JOptionPane.showConfirmDialog(owner.get(), "Delete this schedule?\n" + title, "DayFlow",
                JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
    }

    @Override
    public ScheduleFormDialog.Result form(ScheduleFormDialog.Use use, ScheduleDraft initial,
                                          Function<LocalDate, List<Occurrence>> sameDay) {
        return ScheduleFormDialog.open(owner.get(), use, initial, sameDay);
    }

    @Override
    public void nothingToPlan() {
        JOptionPane.showMessageDialog(owner.get(), "Nothing to plan this week.", "DayFlow",
                JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    public Optional<List<WorkBlock>> reviewPlan(WeekPlan.Proposal proposal) {
        ImportPreviewDialog dialog = PlanPreview.dialog(JOptionPane.getFrameForComponent(owner.get()), proposal);
        dialog.setVisible(true);
        return dialog.confirmed() ? Optional.of(PlanPreview.chosen(proposal.plan(), dialog.rows())) : Optional.empty();
    }
}
