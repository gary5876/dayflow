package dayflow.model;

import java.time.LocalDate;

public enum Repeat {
    NONE("None"), DAILY("Daily"), WEEKLY("Weekly"), MONTHLY("Monthly");

    private final String label;

    Repeat(String label) {
        this.label = label;
    }

    /**
     * The date of occurrence number {@code n} of a series that starts on {@code first} (n = 0 is the first).
     * Counted from the first date, so a monthly series that starts on the 31st returns to the 31st
     * after a shorter month.
     */
    LocalDate nth(LocalDate first, int n) {
        return switch (this) {
            case DAILY -> first.plusDays(n);
            case WEEKLY -> first.plusWeeks(n);
            case MONTHLY -> first.plusMonths(n);
            case NONE -> n == 0 ? first : LocalDate.MAX;
        };
    }

    @Override
    public String toString() {
        return label;
    }
}
