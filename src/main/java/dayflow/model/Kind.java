package dayflow.model;

/** What sort of time a schedule has. */
public enum Kind {
    /** From a start time to an end time. */
    TIMED("Timed"),
    /** Something to finish by a due time; can be marked as done. */
    DEADLINE("Deadline"),
    /** A date without a time, such as a result announcement. */
    ALL_DAY("All day");

    private final String label;

    Kind(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
