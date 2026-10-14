package dayflow.plan;

import dayflow.model.Occurrence;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Places the work for open deadlines into free time.
 * The placement rule is expected to change, so the rest of the program only knows this interface.
 */
public interface WorkPlanner {
    /**
     * @param schedules everything on the calendar, including work blocks placed earlier
     * @param from      the first moment that may be planned (normally now)
     * @param until     the last day to plan
     */
    Plan plan(List<Occurrence> schedules, LocalDateTime from, LocalDate until);
}
