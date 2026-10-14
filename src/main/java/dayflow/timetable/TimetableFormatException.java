package dayflow.timetable;

/** The image does not look like an Everytime timetable. */
public class TimetableFormatException extends Exception {
    public TimetableFormatException(String message) {
        super(message);
    }
}
