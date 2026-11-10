package dayflow.plan;

import dayflow.model.Occurrence;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * A proposed block of time for working on one deadline.
 *
 * @param deadline the deadline occurrence the block is for
 * @param date the date of the block
 * @param start the start time
 * @param end the end time
 */
public record WorkBlock(Occurrence deadline, LocalDate date, LocalTime start, LocalTime end) {
}
