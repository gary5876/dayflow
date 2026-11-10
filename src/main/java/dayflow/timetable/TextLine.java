package dayflow.timetable;

import java.awt.Rectangle;

/**
 * One piece of text found in an image, with its position in image pixels.
 *
 * @param text the text
 * @param box the position in image pixels
 */
public record TextLine(String text, Rectangle box) {
    /**
     * Returns the horizontal centre of the box.
     *
     * @return the horizontal centre of the box
     */
    public int centerX() {
        return box.x + box.width / 2;
    }

    /**
     * Returns the vertical centre of the box.
     *
     * @return the vertical centre of the box
     */
    public int centerY() {
        return box.y + box.height / 2;
    }
}
