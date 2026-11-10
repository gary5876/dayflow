package dayflow.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dayflow.model.Category;
import dayflow.model.Occurrence;
import dayflow.model.Schedule;
import dayflow.plan.Plan;
import dayflow.plan.SimplePlanner;
import dayflow.ui.ImportPreviewDialog.Candidate;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlanPreviewTest {
    private static final LocalDate SATURDAY = LocalDate.of(2026, 11, 14);

    private static Plan plan(LocalDateTime from, Schedule... schedules) {
        List<Occurrence> all = new ArrayList<>();
        for (Schedule s : schedules) {
            all.addAll(s.occurrences(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)));
        }
        return new SimplePlanner().plan(all, from, SATURDAY);
    }

    @Test
    void everyBlockIsACheckedRowAndOnlyTheCheckedOnesAreChosen() {
        Plan plan = plan(LocalDate.of(2026, 11, 10).atTime(10, 20),
                Schedule.deadline("Paper", SATURDAY, "18:00", Category.JOB));
        List<Candidate> rows = new ArrayList<>(PlanPreview.rows(plan));
        assertEquals(plan.blocks().size(), rows.size());
        assertTrue(rows.stream().allMatch(Candidate::checked));
        assertEquals(plan.blocks(), PlanPreview.chosen(plan, rows));
        rows.set(0, rows.get(0).withChecked(false));
        assertEquals(plan.blocks().subList(1, plan.blocks().size()), PlanPreview.chosen(plan, rows));
    }

    @Test
    void aDeadlineWithNoFreeTimeIsAnUncheckableRowWithItsShortage() {
        // Fifteen minutes before the deadline there is not one free half hour.
        Plan plan = plan(LocalDate.of(2026, 11, 13).atTime(17, 45),
                Schedule.deadline("Report", LocalDate.of(2026, 11, 13), "18:00", Category.STUDY));
        List<Candidate> rows = PlanPreview.rows(plan);
        assertEquals(1, rows.size());
        assertFalse(rows.get(0).checked());
        assertFalse(rows.get(0).checkable());
        assertEquals("No free time: short by 2 h", rows.get(0).note());
        assertTrue(PlanPreview.chosen(plan, rows).isEmpty());
        assertEquals("<html><font color='#D93025'><b>2 h of work does not fit.</b></font> "
                + "Reduce the work needed, or drop a deadline.</html>", PlanPreview.summary(plan));
    }

    @Test
    void theSummaryCountsDeadlinesThatNeedNoWork() {
        Plan plan = plan(LocalDate.of(2026, 11, 10).atTime(10, 20),
                Schedule.deadline("Paper", SATURDAY, "18:00", Category.JOB),
                Schedule.deadline("Pay fee", SATURDAY, "12:00", Category.LIFE));
        assertEquals("Everything fits before its deadline. 1 deadline needs no work.", PlanPreview.summary(plan));
    }

    @Test
    void aDeadlinePartlyPlacedKeepsItsCheckedRowWithShortByAndTheSummaryCountsTheRest() {
        // Free half hours before 11:30 on Friday after 10:20 are 10:30 and 11:00, so one hour of two fits.
        Plan plan = plan(LocalDate.of(2026, 11, 13).atTime(10, 20),
                Schedule.deadline("Report", LocalDate.of(2026, 11, 13), "11:30", Category.STUDY));
        List<Candidate> rows = PlanPreview.rows(plan);
        assertEquals(1, rows.size());
        assertTrue(rows.get(0).checked());
        assertEquals(List.of("Fri Nov 13", "10:30-11:30", "Report", "Fri Nov 13 ~11:30"), rows.get(0).cells());
        assertEquals("Short by 1 h", rows.get(0).note());
        assertEquals("<html><font color='#D93025'><b>1 h of work does not fit.</b></font> "
                + "Reduce the work needed, or drop a deadline.</html>", PlanPreview.summary(plan));
    }
}
