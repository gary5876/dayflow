package dayflow.model;

import java.awt.Color;

/** Schedule categories of the target user, each with its display colour. */
public enum Category {
    /** Reading, coursework, and exam preparation. */
    STUDY("Study", new Color(0x3B78E7), 120),
    /** Job applications and work tasks. */
    JOB("Job", new Color(0xD9730D), 180),
    /** Certificate exams. */
    CERTIFICATE("Certificate", new Color(0x8E44AD), 0),
    /** Hobbies and leisure. */
    HOBBY("Hobby", new Color(0x1E8E3E), 0),
    /** Everyday life, and the default category. */
    LIFE("Life", new Color(0x5F6368), 0);

    /** The category of a schedule when the user chose none. */
    public static final Category DEFAULT = LIFE;

    private final String label;
    private final Color color;
    private final int defaultWorkMinutes;

    Category(String label, Color color, int defaultWorkMinutes) {
        this.label = label;
        this.color = color;
        this.defaultWorkMinutes = defaultWorkMinutes;
    }

    /**
     * How long the work for a deadline of this category takes when the user gave no estimate.
     *
     * @return the default work estimate in minutes
     */
    public int defaultWorkMinutes() {
        return defaultWorkMinutes;
    }

    /**
     * Returns the display colour of the category.
     *
     * @return the display colour of the category
     */
    public Color color() {
        return color;
    }

    @Override
    public String toString() {
        return label;
    }
}
