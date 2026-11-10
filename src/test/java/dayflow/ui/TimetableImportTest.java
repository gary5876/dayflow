package dayflow.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dayflow.timetable.TimetableFormatException;
import dayflow.timetable.TimetableImageParser;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TimetableImportTest {
    @Test
    void aGridThatIsNotFoundMeansTheImageIsNotATimetable() {
        assertEquals("This image does not look like an Everytime timetable.",
                TimetableImport.failureText(new TimetableFormatException("The timetable grid was not found.")));
        assertEquals("This image does not look like an Everytime timetable.",
                TimetableImport.failureText(new TimetableFormatException("No classes were found in the timetable.")));
    }

    @Test
    void hoursThatCannotBeReadHaveTheirOwnReason() {
        assertEquals("The hours of this timetable could not be read.",
                TimetableImport.failureText(new TimetableFormatException(TimetableImageParser.HOURS_UNREADABLE)));
    }

    @Test
    void aHelperThatCannotRunMeansTheTextCannotBeReadHere() {
        assertEquals("The text in this image could not be read on this computer.",
                TimetableImport.failureText(new IOException("Cannot run program")));
        assertEquals("The text in this image could not be read on this computer.",
                TimetableImport.failureText(new NumberFormatException("x")));
    }

    @Test
    void theFoundBannerCountsClassBlocksAndEscapesTheFileName() {
        assertEquals("<html><b>12 class blocks found</b> in everytime.png</html>",
                TimetableImport.foundText(12, "everytime.png"));
        assertEquals("<html><b>1 class block found</b> in a&amp;b.png</html>", TimetableImport.foundText(1, "a&b.png"));
    }

    @Test
    void onlyPngAndJpegFilesStartAnImport() {
        assertTrue(TimetableImport.isImage(Path.of("/x/Timetable.PNG")));
        assertTrue(TimetableImport.isImage(Path.of("a.jpg")));
        assertTrue(TimetableImport.isImage(Path.of("a.jpeg")));
        assertFalse(TimetableImport.isImage(Path.of("a.pdf")));
    }
}
