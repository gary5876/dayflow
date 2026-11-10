package dayflow.model;

/** How important a schedule is to the user. */
public enum Priority {
    /** Needs attention first. */
    HIGH("High"),
    /** The default importance. */
    MEDIUM("Medium"),
    /** Can wait. */
    LOW("Low");

    private final String label;

    Priority(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
