package dayflow.timetable;

/** The image does not look like an Everytime timetable. */
public class TimetableFormatException extends Exception {
    /**
     * Creates the exception.
     *
     * @param message the text to show
     */
    public TimetableFormatException(String message) {
        super(message);
    }
}
