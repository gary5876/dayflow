package dayflow.timetable;

import java.awt.Color;
import java.time.DayOfWeek;
import java.time.LocalTime;

/** One coloured block of a weekly timetable. Blocks of the same course share a colour. */
public record ClassBlock(DayOfWeek day, LocalTime start, LocalTime end,
                         String name, String professor, String room, Color color) {
}
