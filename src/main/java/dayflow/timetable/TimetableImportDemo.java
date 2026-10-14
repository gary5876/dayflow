package dayflow.timetable;

import java.nio.file.Path;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

/** Prints what the parser reads from a timetable image. */
public final class TimetableImportDemo {
    public static void main(String[] args) throws Exception {
        if (args.length != 1 && args.length != 2) {
            System.err.println("usage: TimetableImportDemo [dayflow-ocr helper] <timetable image>");
            System.exit(2);
        }
        // Without a helper path, the helper shipped next to the program is used, as the installed app does.
        Path helper = args.length == 2 ? Path.of(args[0]) : MacVisionRecognizer.helperNextToProgram();
        TimetableImageParser parser = new TimetableImageParser(new MacVisionRecognizer(helper));
        List<ClassBlock> blocks = parser.parse(Path.of(args[args.length - 1]));
        for (ClassBlock b : blocks) {
            System.out.printf("%s %s-%s  %-22s %-10s %-10s #%06X%n",
                    b.day().getDisplayName(TextStyle.SHORT, Locale.ENGLISH), b.start(), b.end(),
                    b.name(), b.professor(), b.room(), b.color().getRGB() & 0xFFFFFF);
        }
        System.out.println(blocks.size() + " blocks, "
                + blocks.stream().map(ClassBlock::name).distinct().count() + " courses");
    }
}
