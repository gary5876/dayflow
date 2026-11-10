package dayflow.model;

import java.time.LocalDate;

/**
 * What a work block is for: one occurrence of a deadline.
 * The date is needed because a repeating deadline has a separate plan for each occurrence.
 * In the data file this is stored as the deadline's id and the date.
 *
 * @param deadline the deadline schedule
 * @param date the date of the occurrence
 */
public record WorkTarget(Schedule deadline, LocalDate date) {
    /**
     * Returns whether this target is the given occurrence.
     *
     * @param occurrence the occurrence to compare
     * @return true when it is the same occurrence
     */
    public boolean is(Occurrence occurrence) {
        return deadline == occurrence.schedule() && date.equals(occurrence.date());
    }
}
