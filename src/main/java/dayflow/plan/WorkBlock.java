package dayflow.plan;

import dayflow.model.Occurrence;
import java.time.LocalDate;
import java.time.LocalTime;

/** A proposed block of time for working on one deadline. */
public record WorkBlock(Occurrence deadline, LocalDate date, LocalTime start, LocalTime end) {
}
