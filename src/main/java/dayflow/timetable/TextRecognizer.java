package dayflow.timetable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Finds text in an image without sending it anywhere.
 * Each operating system gets its own implementation.
 */
public interface TextRecognizer {
    /**
     * Reads the text of an image.
     *
     * @param image the image file
     * @return the pieces of text found
     * @throws IOException if the image cannot be read
     */
    List<TextLine> recognize(Path image) throws IOException;
}
