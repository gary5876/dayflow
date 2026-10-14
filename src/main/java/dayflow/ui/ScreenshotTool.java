package dayflow.ui;

import dayflow.model.Category;
import dayflow.model.Occurrence;
import dayflow.model.Priority;
import dayflow.model.Schedule;
import dayflow.model.WorkTarget;
import dayflow.plan.Plan;
import dayflow.plan.SimplePlanner;
import dayflow.plan.WorkBlock;
import dayflow.timetable.ClassBlock;
import dayflow.timetable.MacVisionRecognizer;
import dayflow.timetable.TimetableImageParser;
import dayflow.ui.ImportPreviewDialog.Candidate;
import dayflow.ui.ListPanel.Filters;
import dayflow.ui.MainFrame.BannerKind;
import dayflow.ui.MainFrame.View;
import java.awt.Color;
import java.awt.Container;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.RepaintManager;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Draws every screen with sample data and saves it as a PNG for the design report.
 * Usage: ScreenshotTool <output folder> <dayflow-ocr helper> <timetable image>
 */
public final class ScreenshotTool {
    private static final int SCALE = 2;
    /** Height of the title bar drawn above each window, so the window title is part of the picture. */
    private static final int TITLE_BAR = 28;
    private static final LocalDate FROM = LocalDate.of(2026, 9, 1);
    private static final LocalDate TO = LocalDate.of(2026, 12, 31);
    private static final String URL = "https://careers.example.com/jobs/4821";
    private static final LocalDate TODAY = SampleData.TODAY;
    /** The clock shown in the status bar ("Saved at 10:42"); plans start from this moment. */
    private static final LocalDateTime NOW = TODAY.atTime(10, 42);
    /** The List tab looks three months ahead by default. */
    private static final LocalDate LIST_TO = TODAY.plusMonths(3);
    private static final Filters DEFAULTS = new Filters(TODAY, LIST_TO, null, false, false, true, null);

    private static Path out;

    public static void main(String[] args) throws Exception {
        out = Path.of(args[0]);
        out.toFile().mkdirs();
        List<ClassBlock> timetable = new TimetableImageParser(new MacVisionRecognizer(Path.of(args[1])))
                .parse(Path.of(args[2]));

        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        SwingUtilities.invokeAndWait(() -> {
            try {
                drawAll(timetable);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        System.exit(0);
    }

    private static View calendarView() {
        return new View(MainFrame.CALENDAR_TAB, TODAY, DEFAULTS);
    }

    private static void drawAll(List<ClassBlock> timetable) throws Exception {
        List<Schedule> schedules = SampleData.schedules();
        List<Occurrence> data = SampleData.occurrences(schedules, FROM, TO);

        // Main window: calendar, with each state of the import banner.
        MainFrame calendar = new MainFrame(data, NOW, calendarView()
                .banner(BannerKind.OFFER, "<html><b>Web address found in clipboard:</b> " + URL + "</html>")
                .selectDayRow(2));
        save(calendar, "ui-main-calendar.png");
        save(new MainFrame(data, NOW, new View(MainFrame.CALENDAR_TAB, LocalDate.of(2026, 10, 23), DEFAULTS)
                .banner(BannerKind.ANALYZING, "<html><b>Analyzing</b> " + URL + " ... &nbsp;<font color='#5F6368'>"
                        + "(2 more waiting)</font></html>")), "ui-main-analyzing.png");
        save(new MainFrame(data, NOW, calendarView()
                .banner(BannerKind.FOUND, "<html><b>4 dates found</b> in " + URL + "</html>")), "ui-main-found.png");
        save(new MainFrame(data, NOW, calendarView()
                .banner(BannerKind.FOUND, "<html><b>12 class blocks found</b> in everytime.png</html>")),
                "ui-main-found-timetable.png");
        save(new MainFrame(data, NOW, calendarView()
                .banner(BannerKind.FAILED, "<html><b>This page could not be read.</b> Copy the text of the posting "
                        + "and paste it instead.</html>", "Add manually")), "ui-main-failed.png");
        save(new MainFrame(data, NOW, calendarView().importOff()
                .banner(BannerKind.NO_KEY, "<html><b>Set a Gemini API key to import from this URL:</b> " + URL
                        + "</html>")), "ui-main-no-key.png");
        // A timetable whose hour labels could not be read, while another import waits.
        save(new MainFrame(data, NOW, calendarView()
                .banner(BannerKind.FAILED, "<html><b>The hours of this timetable could not be read.</b> &nbsp;"
                        + "<font color='#5F6368'>(1 more waiting)</font></html>", "Set first hour...")),
                "ui-main-failed-hours.png");
        save(new MainFrame(List.of(), NOW, calendarView().importOff()), "ui-main-empty.png");
        // Saving failed after a delete: the change is kept in memory and can still be undone.
        List<Occurrence> afterDelete = data.stream().filter(o -> !o.title().equals("Concert ticket opening")).toList();
        save(new MainFrame(afterDelete, NOW, calendarView().status("Deleted: Concert ticket opening.", true)
                .notSaved()), "ui-main-not-saved.png");

        // List tab: filtered to open job deadlines, and a search.
        save(new MainFrame(data, NOW, new View(MainFrame.LIST_TAB, TODAY,
                new Filters(TODAY, LIST_TO, Category.JOB, true, true, true, null)).selectListRow(1)), "ui-main-list.png");
        // The deadline just checked off stays in the open list, struck through, until something else happens.
        Set<Occurrence> justDone = Set.copyOf(data.stream()
                .filter(o -> o.title().startsWith("Nuri Networks application")).toList());
        save(new MainFrame(data, NOW, new View(MainFrame.LIST_TAB, TODAY,
                new Filters(TODAY, LIST_TO, null, false, false, true, "application"))
                .status("Marked done: Nuri Networks application.", true).justDone(justDone)), "ui-main-search.png");

        // Week timetable before and after planning.
        save(new MainFrame(data, NOW, new View(MainFrame.WEEK_TAB, TODAY, DEFAULTS)), "ui-main-week.png");
        LocalDate weekEnd = TODAY.plusDays(6 - TODAY.getDayOfWeek().getValue() % 7);
        Plan plan = new SimplePlanner().plan(data, NOW, weekEnd);
        List<Schedule> withPlan = new ArrayList<>(schedules);
        Schedule firstBlock = null;
        for (WorkBlock b : plan.blocks()) {
            Schedule block = Schedule.workBlock(new WorkTarget(b.deadline().schedule(), b.deadline().date()),
                    b.date(), b.start(), b.end());
            withPlan.add(block);
            firstBlock = firstBlock == null ? block : firstBlock;
        }
        List<Occurrence> planned = SampleData.occurrences(withPlan, FROM, TO);
        save(new MainFrame(planned, NOW, new View(MainFrame.WEEK_TAB, TODAY, DEFAULTS)
                .status(plan.blocks().size() + " work blocks added.", true)), "ui-main-week-planned.png");
        save(planPreview(calendar, plan, NOW, weekEnd), "ui-plan-preview.png");
        // A schedule was added on top of a work block: the plan no longer fits.
        List<Schedule> stale = new ArrayList<>(withPlan);
        stale.add(Schedule.timed("Study group", TODAY.plusDays(1), "19:00", "20:30", Category.STUDY));
        save(new MainFrame(SampleData.occurrences(stale, FROM, TO), NOW, new View(MainFrame.WEEK_TAB, TODAY, DEFAULTS)
                .status("Added: Study group. Plan is out of date.", true)), "ui-main-week-stale.png");
        saveFileMenu();

        // The same week planned late on Thursday: Friday's deadlines no longer fit.
        LocalDateTime thursday = LocalDate.of(2026, 10, 15).atTime(16, 30);
        Plan late = new SimplePlanner().plan(data, thursday, weekEnd);
        save(planPreview(calendar, late, thursday, weekEnd), "ui-plan-preview-short.png");
        // That plan stored: the week summary shows the work that no block covers.
        save(new MainFrame(SampleData.occurrences(withBlocks(schedules, late), FROM, TO), thursday,
                new View(MainFrame.WEEK_TAB, thursday.toLocalDate(), DEFAULTS)
                        .status(late.blocks().size() + " work blocks added.", true)), "ui-main-week-short.png");
        // Planned on Friday afternoon: the deadlines due within the hour have no free time left at all.
        LocalDateTime friday = LocalDate.of(2026, 10, 16).atTime(16, 40);
        save(planPreview(calendar, new SimplePlanner().plan(data, friday, weekEnd), friday, weekEnd),
                "ui-plan-preview-nofree.png");

        // Schedule form: new timed schedule with an overlap, a deadline, a whole series, a work block.
        Schedule test = Schedule.timed("Coding test: Blue Cloud", LocalDate.of(2026, 10, 22), "12:30", "14:30",
                Category.JOB).priority(Priority.HIGH).memo("Online test. Link arrives by email one hour before.\n" + URL);
        save(new ScheduleFormDialog(calendar, "New Schedule", test, test.date(), on(data, test.date()), false, false),
                "ui-schedule-form.png");
        Schedule report = find(schedules, "SSD Checkpoint");
        save(new ScheduleFormDialog(calendar, "Edit Schedule", report, report.date(), on(data, report.date()), true,
                false), "ui-schedule-form-edit.png");
        Schedule lecture = find(schedules, "Software System Design");
        save(new ScheduleFormDialog(calendar, "Edit Schedule (all occurrences)", lecture, lecture.date(), List.of(), true,
                false), "ui-schedule-form-repeat.png");
        // One date of that series: the form shows that date and has no repeat row.
        LocalDate oneClass = LocalDate.of(2026, 10, 20);
        save(new ScheduleFormDialog(calendar, "Edit Schedule", lecture, oneClass, on(data, oneClass), true, true),
                "ui-schedule-form-occurrence.png");
        save(new ScheduleFormDialog(calendar, "Edit Schedule", firstBlock, firstBlock.date(),
                on(planned, firstBlock.date()), true, false), "ui-schedule-form-work.png");

        // Import previews.
        List<String> postingColumns = List.of("Date", "Type", "Time", "Title", "Category");
        List<Candidate> posting = List.of(
                new Candidate(true, List.of("2026-10-27", "Deadline", "17:00", "Hanul Systems application", "Job"), ""),
                new Candidate(false, List.of("2026-11-06", "All day", "", "Hanul Systems document result", "Job"), ""),
                new Candidate(false, List.of("2026-11-14", "Timed", "10:00-12:00", "Hanul Systems coding test", "Job"),
                        "Overlaps Tutoring 10:00-12:00"),
                new Candidate(false, List.of("2026-11-25", "All day", "", "Hanul Systems first interview", "Job"), ""));
        save(new ImportPreviewDialog(calendar, "Add schedules from import",
                "<html>Source: " + URL + " &nbsp; <font color='#5F6368'>(closing date from page data, "
                        + "other dates by Gemini)</font></html>", postingColumns, posting,
                ImportPreviewDialog.options(new JLabel("Double-click a cell to correct it.")), "Add", 5, true),
                "ui-import-posting.png");
        // An email with only a test date: no deadline among the candidates, so everything is checked.
        List<Candidate> email = List.of(new Candidate(true,
                List.of("2026-10-22", "Timed", "12:30-14:30", "Blue Cloud coding test", "Job"),
                "Overlaps Distributed Systems 12:00-13:30"));
        save(new ImportPreviewDialog(calendar, "Add schedules from import",
                "<html>Source: text from clipboard &nbsp; <font color='#5F6368'>(by Gemini)</font></html>",
                postingColumns, email,
                ImportPreviewDialog.options(new JLabel("Double-click a cell to correct it.")), "Add", 5, true),
                "ui-import-email.png");
        List<Candidate> again = List.of(new Candidate(false,
                List.of("2026-10-16", "Deadline", "17:00", "Greenlab AI application", "Job"),
                "Already in your schedules"));
        save(new ImportPreviewDialog(calendar, "Add schedules from import",
                "<html>Source: https://careers.example.com/jobs/3310</html>", postingColumns, again,
                ImportPreviewDialog.options(new JLabel("Nothing new was found. Check a row to add it anyway.")),
                "Add", 5, true), "ui-import-duplicate.png");

        // The report must not show real people: each professor read from the image gets a made-up name.
        List<String> madeUp = List.of("김영수", "이영희", "박민수", "최지영", "정현우", "강민준");
        List<String> professors = timetable.stream().map(ClassBlock::professor).distinct().toList();
        List<Candidate> classes = new ArrayList<>();
        for (ClassBlock b : timetable) {
            classes.add(new Candidate(true, List.of(b.day().getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                    b.start() + "-" + b.end(), b.name(), b.room(),
                    madeUp.get(professors.indexOf(b.professor()) % madeUp.size())), ""));
        }
        // The Monday of the week (Sunday to Saturday) that contains today.
        LocalDate from = TODAY.minusDays(TODAY.getDayOfWeek().getValue() % 7).plusDays(1);
        save(new ImportPreviewDialog(calendar, "Add schedules from import",
                "<html>Source: everytime.png &nbsp; <font color='#5F6368'>(read on this computer: "
                        + timetable.size() + " class blocks, category Study)</font></html>",
                List.of("Day", "Time", "Course", "Room", "Professor"), classes,
                ImportPreviewDialog.options(new JLabel("Repeat weekly from"), ListPanel.dateSpinner(from),
                        new JLabel("until"), ListPanel.dateSpinner(semesterEnd(from))), "Add", 5, true),
                "ui-import-timetable.png");
    }

    /** R-TT-6: the first June 20 or December 20 after the given date, about when a Korean semester ends. */
    private static LocalDate semesterEnd(LocalDate from) {
        LocalDate june = from.withMonth(6).withDayOfMonth(20);
        LocalDate december = from.withMonth(12).withDayOfMonth(20);
        if (from.isBefore(june)) {
            return june;
        }
        return from.isBefore(december) ? december : june.plusYears(1);
    }

    private static int placedMinutes(Plan plan) {
        return plan.blocks().stream()
                .mapToInt(b -> (b.end().toSecondOfDay() - b.start().toSecondOfDay()) / 60).sum();
    }

    private static ImportPreviewDialog planPreview(MainFrame owner, Plan plan, LocalDateTime from, LocalDate until) {
        DateTimeFormatter day = DateTimeFormatter.ofPattern("EEE MMM d", Locale.ENGLISH);
        List<Candidate> rows = new ArrayList<>();
        for (WorkBlock b : plan.blocks()) {
            Integer lacking = plan.shortMinutes().get(b.deadline());
            rows.add(new Candidate(true, List.of(b.date().format(day), b.start() + "-" + b.end(),
                    b.deadline().title(), b.deadline().date().format(day) + " " + b.deadline().timeText()),
                    lacking == null ? "" : "Short by " + hours(lacking)));
        }
        plan.shortMinutes().forEach((deadline, minutes) -> {
            if (plan.blocks().stream().noneMatch(b -> b.deadline().equals(deadline))) {
                rows.add(new Candidate(false, false, List.of("", "", deadline.title(),
                        deadline.date().format(day) + " " + deadline.timeText()), "No free time: short by " + hours(minutes)));
            }
        });
        int missing = plan.shortMinutes().values().stream().mapToInt(Integer::intValue).sum();
        int idle = plan.noWork().size();
        String skipped = idle == 0 ? "" : " " + idle + (idle == 1 ? " deadline needs" : " deadlines need") + " no work.";
        return new ImportPreviewDialog(owner, "Plan my week",
                "<html>Plan from " + from.format(DateTimeFormatter.ofPattern("EEE MMM d HH:mm", Locale.ENGLISH))
                        + " to " + until.format(day) + " &nbsp; <font color='#5F6368'>(" + hours(placedMinutes(plan))
                        + " of work placed in free time)</font></html>",
                List.of("Day", "Time", "Work for", "Due"), rows,
                ImportPreviewDialog.options(new JLabel(missing == 0 ? "Everything fits before its deadline." + skipped
                        : "<html><font color='#D93025'><b>" + hours(missing) + " of work does not fit.</b></font> "
                        + "Reduce the work needed, or drop a deadline." + skipped + "</html>")),
                "Add to timetable", 2, false);
    }

    /** The schedules with the blocks of a plan stored as work blocks. */
    private static List<Schedule> withBlocks(List<Schedule> schedules, Plan plan) {
        List<Schedule> result = new ArrayList<>(schedules);
        for (WorkBlock b : plan.blocks()) {
            result.add(Schedule.workBlock(new WorkTarget(b.deadline().schedule(), b.deadline().date()),
                    b.date(), b.start(), b.end()));
        }
        return result;
    }

    private static Schedule find(List<Schedule> schedules, String titleStart) {
        return schedules.stream().filter(s -> s.title().startsWith(titleStart)).findFirst().orElseThrow();
    }

    private static String hours(int minutes) {
        return minutes % 60 == 0 ? minutes / 60 + " h" : String.format("%.1f h", minutes / 60.0);
    }

    private static List<Occurrence> on(List<Occurrence> data, LocalDate date) {
        return data.stream().filter(o -> o.date().equals(date)).toList();
    }

    /**
     * The items of the File menu, because no other screen shows them.
     * Drawn as a plain list: an open native menu cannot be painted without showing it.
     */
    private static void saveFileMenu() throws Exception {
        javax.swing.JMenu menu = new MainFrame(List.of(), NOW, calendarView()).getJMenuBar().getMenu(0);
        javax.swing.JPanel list = new javax.swing.JPanel();
        list.setLayout(new javax.swing.BoxLayout(list, javax.swing.BoxLayout.Y_AXIS));
        list.setBackground(java.awt.Color.WHITE);
        list.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0xC0C0C0)),
                javax.swing.BorderFactory.createEmptyBorder(4, 0, 4, 0)));
        for (java.awt.Component item : menu.getMenuComponents()) {
            if (item instanceof javax.swing.JMenuItem menuItem) {
                // A check item shows its state in front of its text.
                JLabel label = new JLabel((menuItem instanceof javax.swing.JCheckBoxMenuItem check
                        ? (check.isSelected() ? "\u2713 " : "    ") : "") + menuItem.getText());
                label.setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 14, 4, 18));
                list.add(label);
            } else {
                list.add(new javax.swing.JSeparator());
            }
        }
        javax.swing.JDialog holder = new javax.swing.JDialog((java.awt.Frame) null, "File menu");
        holder.add(list);
        holder.pack();
        save(holder, "ui-menu-file.png");
    }

    /**
     * Lays the window out without showing it and paints it into an image.
     * A window that is not shown has no title bar, so one is drawn above it with the window's title.
     */
    private static void save(Window window, String fileName) throws Exception {
        window.addNotify();
        window.validate();
        Container root = ((RootPaneContainer) window).getRootPane();
        BufferedImage image = new BufferedImage(root.getWidth() * SCALE, (root.getHeight() + TITLE_BAR) * SCALE,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.scale(SCALE, SCALE);
        String title = window instanceof JFrame frame ? frame.getTitle() : ((JDialog) window).getTitle();
        g.setColor(new Color(0xE8E8E8));
        g.fillRect(0, 0, root.getWidth(), TITLE_BAR);
        g.setColor(new Color(0xC8C8C8));
        g.drawLine(0, TITLE_BAR - 1, root.getWidth(), TITLE_BAR - 1);
        g.setColor(new Color(0x3C4043));
        g.setFont(root.getFont().deriveFont(Font.BOLD, 13f));
        g.drawString(title, (root.getWidth() - g.getFontMetrics().stringWidth(title)) / 2, 19);
        g.translate(0, TITLE_BAR);
        // paint, not print: a JTable hides its selection while printing.
        RepaintManager.currentManager(root).setDoubleBufferingEnabled(false);
        root.paint(g);
        g.dispose();
        ImageIO.write(image, "png", new File(out.resolve(fileName).toString()));
        System.out.println(fileName + " " + root.getWidth() + "x" + (root.getHeight() + TITLE_BAR));
        window.setVisible(false);
    }
}
