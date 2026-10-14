package dayflow.plan;

import dayflow.model.Occurrence;
import dayflow.model.WorkTarget;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * What the week timetable says about the plan of one week.
 * Computed from the current data every time, never stored, so it cannot stay wrong after the cause is gone.
 *
 * @param plannedMinutes     total length of the work blocks of the week
 * @param unplannedMinutes   work for deadlines due this week that no work block covers
 * @param unplannedDeadlines how many deadlines that work belongs to
 * @param outOfDate          an unstarted work block overlaps a timed schedule or ends after its deadline
 * @param clearable          the week has work blocks that have not started yet
 */
public record PlanStatus(int plannedMinutes, int unplannedMinutes, int unplannedDeadlines,
                         boolean outOfDate, boolean clearable) {

    public static PlanStatus of(List<Occurrence> schedules, LocalDate weekStart, LocalDateTime now) {
        LocalDate weekEnd = weekStart.plusDays(6);
        List<Occurrence> blocks = schedules.stream()
                .filter(o -> o.planned() && !o.date().isBefore(weekStart) && !o.date().isAfter(weekEnd)).toList();
        List<Occurrence> unstarted = blocks.stream().filter(b -> !b.date().atTime(b.start()).isBefore(now)).toList();
        boolean outOfDate = unstarted.stream().anyMatch(b -> endsAfterDeadline(b) || overlapsSchedule(b, schedules));

        int unplanned = 0;
        int deadlines = 0;
        for (Occurrence deadline : schedules) {
            boolean dueThisWeek = deadline.isOpenDeadline() && !deadline.date().isBefore(weekStart)
                    && !deadline.date().isAfter(weekEnd) && deadline.due().isAfter(now);
            if (!dueThisWeek) {
                continue;
            }
            int covered = schedules.stream()
                    .filter(o -> o.planned() && o.schedule().plannedFor().is(deadline))
                    .mapToInt(PlanStatus::minutes).sum();
            int missing = deadline.schedule().effectiveWorkMinutes() - covered;
            if (missing > 0) {
                unplanned += missing;
                deadlines++;
            }
        }
        return new PlanStatus(blocks.stream().mapToInt(PlanStatus::minutes).sum(), unplanned, deadlines,
                outOfDate, !unstarted.isEmpty());
    }

    /** True when the week has any work block, which is when the summary is shown at all. */
    public boolean hasPlan() {
        return plannedMinutes > 0;
    }

    private static boolean endsAfterDeadline(Occurrence block) {
        WorkTarget target = block.schedule().plannedFor();
        return block.date().atTime(block.end()).isAfter(target.date().atTime(target.deadline().start()));
    }

    private static boolean overlapsSchedule(Occurrence block, List<Occurrence> schedules) {
        return schedules.stream().anyMatch(o -> o.timed() && !o.planned() && o.date().equals(block.date())
                && o.start().isBefore(block.end()) && block.start().isBefore(o.end()));
    }

    private static int minutes(Occurrence block) {
        return (block.end().toSecondOfDay() - block.start().toSecondOfDay()) / 60;
    }
}
