package dayflow.timetable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Finds text in an image without sending it anywhere.
 * Each operating system gets its own implementation.
 */
public interface TextRecognizer {
    List<TextLine> recognize(Path image) throws IOException;
}
