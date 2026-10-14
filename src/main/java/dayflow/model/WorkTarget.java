package dayflow.model;

import java.time.LocalDate;

/**
 * What a work block is for: one occurrence of a deadline.
 * The date is needed because a repeating deadline has a separate plan for each occurrence.
 * In the data file this is stored as the deadline's id and the date.
 */
public record WorkTarget(Schedule deadline, LocalDate date) {
    public boolean is(Occurrence occurrence) {
        return deadline == occurrence.schedule() && date.equals(occurrence.date());
    }
}
