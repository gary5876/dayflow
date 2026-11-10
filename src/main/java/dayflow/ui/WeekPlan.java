package dayflow.ui;

import dayflow.model.Occurrence;
import dayflow.plan.Plan;
import dayflow.plan.WorkPlanner;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

/**
 * The decisions of F-15 that do not need a screen: which period is planned (R-PLAN-1) and whether there is
 * anything to plan (R-PLAN-9).
 */
final class WeekPlan {
    /** What the planner proposed for the period from {@code from} to the end of {@code until}. */
    record Proposal(LocalDateTime from, LocalDate until, Plan plan) {
        /** R-PLAN-9: the deadlines that got work, with or without free time for all of it. */
        boolean hasTargets() {
            return !plan.blocks().isEmpty() || !plan.shortMinutes().isEmpty();
        }
    }

    private WeekPlan() {
    }

    /** R-PLAN-1: a week that is already over cannot be planned. */
    static boolean canPlan(LocalDate weekStart, LocalDateTime now) {
        return !weekStart.plusDays(6).isBefore(now.toLocalDate());
    }

    /** R-PLAN-1: from now, or from the Sunday of a week that has not begun, to the Saturday of the shown week. */
    static LocalDateTime from(LocalDate weekStart, LocalDateTime now) {
        LocalDateTime sunday = weekStart.atStartOfDay();
        return sunday.isAfter(now) ? sunday : now;
    }

    /**
     * Runs the planner over the shown week.
     *
     * @param all every occurrence around the week, including the work blocks that already exist
     * @return the proposal, or nothing when the week cannot be planned or has no deadline to plan
     */
    static Optional<Proposal> propose(List<Occurrence> all, LocalDate weekStart, LocalDateTime now,
                                      WorkPlanner planner) {
        if (!canPlan(weekStart, now)) {
            return Optional.empty();
        }
        LocalDateTime from = from(weekStart, now);
        LocalDate until = weekStart.plusDays(6);
        Proposal proposal = new Proposal(from, until, planner.plan(all, from, until));
        return proposal.hasTargets() ? Optional.of(proposal) : Optional.empty();
    }
}
