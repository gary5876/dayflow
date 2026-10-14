package dayflow.model;

public enum Priority {
    HIGH("High"), MEDIUM("Medium"), LOW("Low");

    private final String label;

    Priority(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
