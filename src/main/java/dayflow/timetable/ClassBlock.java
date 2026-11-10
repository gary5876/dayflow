package dayflow.timetable;

import java.awt.Color;
import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * One coloured block of a weekly timetable. Blocks of the same course share a colour.
 *
 * @param day the weekday
 * @param start the start time
 * @param end the end time
 * @param name the class name
 * @param professor the professor, or empty
 * @param room the room, or empty
 * @param color the colour of the block in the image
 */
public record ClassBlock(DayOfWeek day, LocalTime start, LocalTime end,
                         String name, String professor, String room, Color color) {
}
