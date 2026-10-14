package dayflow.timetable;

import java.awt.Rectangle;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Uses the text recognition built into macOS, through the dayflow-ocr helper program. */
public final class MacVisionRecognizer implements TextRecognizer {
    private final Path helper;

    public MacVisionRecognizer(Path helper) {
        this.helper = helper;
    }

    /** The helper is shipped in the same folder as the program. */
    public static Path helperNextToProgram() {
        try {
            Path program = Path.of(MacVisionRecognizer.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return program.resolveSibling("dayflow-ocr");
        } catch (java.net.URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public List<TextLine> recognize(Path image) throws IOException {
        Process process = new ProcessBuilder(helper.toString(), image.toString())
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        try {
            if (process.waitFor() != 0) {
                throw new IOException("Text recognition failed for " + image);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Text recognition was interrupted", e);
        }

        List<TextLine> lines = new ArrayList<>();
        for (String row : output.split("\n")) {
            String[] cols = row.split("\t", 5);
            if (cols.length < 5) {
                continue;
            }
            Rectangle box = new Rectangle(Integer.parseInt(cols[0]), Integer.parseInt(cols[1]),
                    Integer.parseInt(cols[2]), Integer.parseInt(cols[3]));
            lines.add(new TextLine(cols[4].strip(), box));
        }
        return lines;
    }
}
