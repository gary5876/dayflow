package dayflow.ui;

import dayflow.plan.Plan;
import dayflow.plan.WorkBlock;
import dayflow.ui.ImportPreviewDialog.Candidate;
import java.awt.Frame;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.JLabel;

/**
 * The plan mode of the preview window (R-PLAN-5, R-PLAN-7). The rows and the texts are made here without a
 * screen. Row editing (R-PLAN-13) is not built in the beta: the rows can be checked and nothing else.
 */
final class PlanPreview {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE MMM d", Locale.ENGLISH);

    private PlanPreview() {
    }

    /** One row per work block, then one row for each deadline with no free time, which cannot be checked. */
    static List<Candidate> rows(Plan plan) {
        List<Candidate> rows = new ArrayList<>();
        for (WorkBlock b : plan.blocks()) {
            Integer lacking = plan.shortMinutes().get(b.deadline());
            rows.add(new Candidate(true, List.of(b.date().format(DAY), b.start() + "-" + b.end(),
                    b.deadline().title(), b.deadline().date().format(DAY) + " " + b.deadline().timeText()),
                    lacking == null ? "" : "Short by " + WeekPanel.hours(lacking)));
        }
        plan.shortMinutes().forEach((deadline, minutes) -> {
            if (plan.blocks().stream().noneMatch(b -> b.deadline().equals(deadline))) {
                rows.add(new Candidate(false, false, List.of("", "", deadline.title(),
                        deadline.date().format(DAY) + " " + deadline.timeText()),
                        "No free time: short by " + WeekPanel.hours(minutes)));
            }
        });
        return rows;
    }

    /** R-PLAN-5: the blocks of the rows that are checked, in the order of the plan. */
    static List<WorkBlock> chosen(Plan plan, List<Candidate> shown) {
        List<WorkBlock> chosen = new ArrayList<>();
        for (int i = 0; i < plan.blocks().size() && i < shown.size(); i++) {
            if (shown.get(i).checked()) {
                chosen.add(plan.blocks().get(i));
            }
        }
        return chosen;
    }

    static int placedMinutes(Plan plan) {
        return plan.blocks().stream()
                .mapToInt(b -> (b.end().toSecondOfDay() - b.start().toSecondOfDay()) / 60).sum();
    }

    /** The text of {@code preview.source}. */
    static String source(WeekPlan.Proposal p) {
        return "<html>Plan from " + p.from().format(DateTimeFormatter.ofPattern("EEE MMM d HH:mm", Locale.ENGLISH))
                + " to " + p.until().format(DAY) + " &nbsp; <font color='#5F6368'>("
                + WeekPanel.hours(placedMinutes(p.plan())) + " of work placed in free time)</font></html>";
    }

    /** The text of {@code preview.options}: whether everything fits, and how many deadlines need no work (R-PLAN-2). */
    static String summary(Plan plan) {
        int missing = plan.shortMinutes().values().stream().mapToInt(Integer::intValue).sum();
        int idle = plan.noWork().size();
        String skipped = idle == 0 ? "" : " " + idle + (idle == 1 ? " deadline needs" : " deadlines need") + " no work.";
        return missing == 0 ? "Everything fits before its deadline." + skipped
                : "<html><font color='#D93025'><b>" + WeekPanel.hours(missing) + " of work does not fit.</b></font> "
                + "Reduce the work needed, or drop a deadline." + skipped + "</html>";
    }

    static ImportPreviewDialog dialog(Frame owner, WeekPlan.Proposal p) {
        return new ImportPreviewDialog(owner, "Plan my week", source(p), List.of("Day", "Time", "Work for", "Due"),
                rows(p.plan()), ImportPreviewDialog.options(new JLabel(summary(p.plan()))), "Add to timetable", 0, false);
    }
}
