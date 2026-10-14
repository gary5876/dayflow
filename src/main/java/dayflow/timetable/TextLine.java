package dayflow.timetable;

import java.awt.Rectangle;

/** One piece of text found in an image, with its position in image pixels. */
public record TextLine(String text, Rectangle box) {
    public int centerX() {
        return box.x + box.width / 2;
    }

    public int centerY() {
        return box.y + box.height / 2;
    }
}
