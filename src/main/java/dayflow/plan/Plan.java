package dayflow.plan;

import dayflow.model.Occurrence;
import java.util.List;
import java.util.Map;

/**
 * The result of planning: the proposed work blocks, and for each deadline that does not fit,
 * how many minutes of work could not be placed before it is due. Deadlines that need no work
 * are not planned and are listed in {@code noWork}.
 *
 * @param blocks the proposed work blocks
 * @param shortMinutes the minutes that did not fit, for each deadline
 * @param noWork the deadlines that need no work
 */
public record Plan(List<WorkBlock> blocks, Map<Occurrence, Integer> shortMinutes, List<Occurrence> noWork) {
}
