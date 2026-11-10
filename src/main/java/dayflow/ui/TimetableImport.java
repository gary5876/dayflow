package dayflow.ui;

import dayflow.imports.ImportReview;
import dayflow.model.ScheduleDraft;
import dayflow.store.ScheduleBook;
import dayflow.timetable.ClassBlock;
import dayflow.timetable.MacVisionRecognizer;
import dayflow.timetable.TextRecognizer;
import dayflow.timetable.TimetableFormatException;
import dayflow.timetable.TimetableImageParser;
import dayflow.ui.ImportPreviewDialog.Candidate;
import dayflow.ui.MainFrame.Banner;
import dayflow.ui.MainFrame.BannerKind;
import java.awt.Window;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JSpinner;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * F-03 and F-04 for a timetable image: picks the file, reads it in the background while the banner says
 * Analyzing, and opens the preview when the user presses Review. Only the checked rows are stored, through
 * {@link ScheduleActions#addImported}. The beta takes one image at a time (the waiting line of R-IMP-5 is
 * not built, D-30), so a new import is refused while any banner is up.
 */
final class TimetableImport {
    static final String NOT_A_TIMETABLE = "This image does not look like an Everytime timetable.";
    static final String HOURS_UNREADABLE = "The hours of this timetable could not be read.";
    static final String CANNOT_READ_TEXT = "The text in this image could not be read on this computer.";

    private static final FileNameExtensionFilter IMAGES =
            new FileNameExtensionFilter("Images (png, jpg, jpeg)", "png", "jpg", "jpeg");

    private final ScheduleBook book;
    private final ScheduleActions actions;
    private final ViewState view;
    private final Supplier<Window> owner;
    private final Supplier<TextRecognizer> recognizer;
    private final Runnable changed;
    private Banner banner;
    private SwingWorker<List<ClassBlock>, Void> worker;
    private String fileName;
    private List<ClassBlock> found = List.of();

    /** @param changed called when the banner changes, so the window draws it */
    TimetableImport(ScheduleBook book, ScheduleActions actions, ViewState view, Supplier<Window> owner,
                    Supplier<TextRecognizer> recognizer, Runnable changed) {
        this.book = book;
        this.actions = actions;
        this.view = view;
        this.owner = owner;
        this.recognizer = recognizer;
        this.changed = changed;
    }

    /** The recognizer of the installed program: the helper in the folder of the program (R-RUN-3). */
    static TextRecognizer installedRecognizer() {
        return new MacVisionRecognizer(MacVisionRecognizer.helperNextToProgram());
    }

    /** The banner to show, or null for none. */
    Banner banner() {
        return banner;
    }

    /** True while an image is being read. */
    boolean analyzing() {
        return banner != null && banner.kind() == BannerKind.ANALYZING;
    }

    /**
     * D-30: without the waiting line, a new import is only taken when no banner is up. The menu item, the
     * sidebar button and the drop all ask this.
     */
    boolean canStart() {
        return banner == null;
    }

    /** True when a result waits for Review (R-IMP-19). */
    boolean unreviewed() {
        return banner != null && banner.kind() == BannerKind.FOUND;
    }

    /** True for a file name of an image type that the import takes. */
    static boolean isImage(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg");
    }

    /** F-03: asks for the file. Cancelling the chooser does nothing. */
    void chooseAndStart() {
        if (!canStart()) {
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Import Timetable Image");
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setFileFilter(IMAGES);
        if (chooser.showOpenDialog(owner.get()) == JFileChooser.APPROVE_OPTION) {
            start(chooser.getSelectedFile().toPath());
        }
    }

    /** F-03 steps 1 to 5: reads the image off the event thread. The result or the reason goes to the banner. */
    void start(Path image) {
        if (!canStart()) {
            return;
        }
        fileName = image.getFileName().toString();
        banner = new Banner(BannerKind.ANALYZING, "<html><b>Analyzing</b> " + escape(fileName) + " ...</html>");
        SwingWorker<List<ClassBlock>, Void> job = new SwingWorker<>() {
            @Override
            protected List<ClassBlock> doInBackground() throws Exception {
                return new TimetableImageParser(recognizer.get()).parse(image);
            }

            @Override
            protected void done() {
                if (isCancelled() || worker != this) {
                    return;
                }
                try {
                    found = get();
                    banner = new Banner(BannerKind.FOUND, foundText(found.size(), fileName));
                } catch (ExecutionException e) {
                    banner = new Banner(BannerKind.FAILED, "<html><b>" + failureText(e.getCause()) + "</b></html>");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                changed.run();
            }
        };
        worker = job;
        job.execute();
        changed.run();
    }

    /** R-IMP-3: Cancel throws the request away and closes the banner. */
    void cancel() {
        if (worker != null) {
            worker.cancel(true);
        }
        dismiss();
    }

    /** R-IMP-17: Dismiss always closes the banner. */
    void dismiss() {
        worker = null;
        banner = null;
        found = List.of();
        changed.run();
    }

    /** The banner text of a result, such as {@code 12 class blocks found in everytime.png}. */
    static String foundText(int count, String file) {
        return "<html><b>" + count + (count == 1 ? " class block found" : " class blocks found") + "</b> in "
                + escape(file) + "</html>";
    }

    /**
     * R-IMP-15, R-TT-2, R-TT-5 and R-TT-7: the reason shown for a failure. A grid that is not found means
     * the image is not a timetable, and a helper that cannot run means the text cannot be read here.
     * The reason of R-TT-5 has no Set first hour button yet, so Dismiss is all that follows it.
     */
    static String failureText(Throwable cause) {
        if (cause instanceof TimetableFormatException format) {
            return TimetableImageParser.HOURS_UNREADABLE.equals(format.getMessage()) ? HOURS_UNREADABLE
                    : NOT_A_TIMETABLE;
        }
        return CANNOT_READ_TEXT;
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** F-04 for a timetable: Review opens the preview. Adding closes the banner and Cancel leaves it (R-IMP-17). */
    void review() {
        if (!unreviewed()) {
            return;
        }
        LocalDate from = ImportReview.defaultFrom(view.today());
        LocalDate until = ImportReview.defaultUntil(from);
        List<ClassBlock> blocks = found;
        List<ImportReview.Row> rows = new ArrayList<>(
                ImportReview.fromTimetable(blocks, from, until, firstWeek(from)));

        JSpinner fromSpinner = ListPanel.dateSpinner(from);
        JSpinner untilSpinner = ListPanel.dateSpinner(until);
        ImportPreviewDialog dialog = new ImportPreviewDialog(JOptionPane.getFrameForComponent(owner.get()),
                "Add schedules from import",
                "<html>Source: " + escape(fileName) + " &nbsp; <font color='#5F6368'>(read on this computer: "
                        + blocks.size() + (blocks.size() == 1 ? " class block" : " class blocks") + ", category Study)</font></html>",
                List.of("Day", "Time", "Course", "Room", "Professor"), candidates(rows, blocks),
                ImportPreviewDialog.options(new JLabel("Repeat weekly from"), fromSpinner, new JLabel("until"),
                        untilSpinner),
                "Add", 0, true);

        boolean[] adjusting = {false};
        javax.swing.event.ChangeListener period = e -> {
            if (adjusting[0]) {
                return;
            }
            LocalDate newFrom = ListPanel.dateOf(fromSpinner);
            // The spec lets until follow from when it would fall before it.
            if (ListPanel.dateOf(untilSpinner).isBefore(newFrom)) {
                adjusting[0] = true;
                untilSpinner.setValue(fromSpinner.getValue());
                adjusting[0] = false;
            }
            keepChecks(rows, dialog.rows());
            List<ImportReview.Row> moved = ImportReview.rebase(rows, newFrom, ListPanel.dateOf(untilSpinner),
                    firstWeek(newFrom));
            rows.clear();
            rows.addAll(moved);
            dialog.replaceRows(candidates(rows, blocks));
        };
        fromSpinner.addChangeListener(period);
        untilSpinner.addChangeListener(period);

        dialog.onEdit(i -> {
            ImportReview.Row row = rows.get(i);
            ScheduleFormDialog.Result result = ScheduleFormDialog.open(dialog, ScheduleFormDialog.Use.PREVIEW_CLASS,
                    row.draft(), day -> book.occurrences(day, day));
            if (result instanceof ScheduleFormDialog.Saved saved && saved.values().problem().isEmpty()) {
                keepChecks(rows, dialog.rows());
                // R-IMP-8 again, because the edit may have made the row a duplicate or no longer one.
                ImportReview.Row changedRow = ImportReview.recheck(
                        new ImportReview.Row(saved.values(), rows.get(i).checked(), row.note()),
                        book.occurrences(saved.values().date(), saved.values().date()));
                rows.set(i, changedRow);
                return Optional.of(candidate(changedRow, blocks.get(i)));
            }
            return Optional.empty();
        });
        // R-IMP-11: a checked class without a title cannot be stored.
        dialog.onMissingValue(i -> rows.get(i).draft().problem().isPresent());

        dialog.setVisible(true);
        if (dialog.confirmed()) {
            keepChecks(rows, dialog.rows());
            List<ScheduleDraft> chosen = rows.stream().filter(ImportReview.Row::checked)
                    .map(ImportReview.Row::draft).toList();
            dismiss();
            actions.addImported(chosen);
        }
    }

    /** The occurrences of the first week from the date, where the first class of every day falls. */
    private List<dayflow.model.Occurrence> firstWeek(LocalDate from) {
        return book.occurrences(from, from.plusDays(6));
    }

    /** Copies the checks the user made in the table into the rows. */
    private static void keepChecks(List<ImportReview.Row> rows, List<Candidate> shown) {
        for (int i = 0; i < rows.size(); i++) {
            ImportReview.Row row = rows.get(i);
            rows.set(i, new ImportReview.Row(row.draft(), shown.get(i).checked(), row.note()));
        }
    }

    private static List<Candidate> candidates(List<ImportReview.Row> rows, List<ClassBlock> blocks) {
        List<Candidate> result = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            result.add(candidate(rows.get(i), blocks.get(i)));
        }
        return result;
    }

    private static Candidate candidate(ImportReview.Row row, ClassBlock block) {
        ScheduleDraft d = row.draft();
        return new Candidate(row.checked(), List.of(d.date().getDayOfWeek().getDisplayName(TextStyle.FULL,
                Locale.ENGLISH), d.start() + "-" + d.end(), d.title(), block.room(), block.professor()), row.note());
    }
}
