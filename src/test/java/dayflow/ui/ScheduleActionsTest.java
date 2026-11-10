package dayflow.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dayflow.model.Category;
import dayflow.model.Kind;
import dayflow.model.Occurrence;
import dayflow.model.Priority;
import dayflow.model.Repeat;
import dayflow.model.Schedule;
import dayflow.model.ScheduleDraft;
import dayflow.model.WorkTarget;
import dayflow.plan.WorkBlock;
import dayflow.store.ScheduleBook;
import dayflow.ui.Dialogs.Scope;
import dayflow.ui.ScheduleFormDialog.Use;
import dayflow.ui.ViewState.Origin;
import dayflow.ui.ViewState.RowKey;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScheduleActionsTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T10:20:00Z"), ZoneId.of("UTC"));
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    /** Answers the questions from fields, and remembers what it was asked. */
    private static final class FakeDialogs implements Dialogs {
        Scope scope = Scope.CANCEL;
        boolean confirm = true;
        ScheduleFormDialog.Result result = new ScheduleFormDialog.Cancelled();
        Use usedForm;
        ScheduleDraft shownDraft;
        int scopeAsked;
        int confirmAsked;
        String confirmedTitle;
        Function<LocalDate, List<Occurrence>> sameDay;
        int nothingToPlanAsked;
        int reviewAsked;
        WeekPlan.Proposal proposed;
        /** What the user does in the plan preview: the default confirms with every block checked. */
        Function<WeekPlan.Proposal, Optional<List<WorkBlock>>> review = p -> Optional.of(p.plan().blocks());

        @Override
        public Scope chooseScope(String title) {
            scopeAsked++;
            return scope;
        }

        @Override
        public boolean confirmDelete(String title) {
            confirmAsked++;
            confirmedTitle = title;
            return confirm;
        }

        @Override
        public ScheduleFormDialog.Result form(Use use, ScheduleDraft initial,
                                              Function<LocalDate, List<Occurrence>> sameDay) {
            this.usedForm = use;
            this.shownDraft = initial;
            this.sameDay = sameDay;
            return result;
        }

        @Override
        public void nothingToPlan() {
            nothingToPlanAsked++;
        }

        @Override
        public Optional<List<WorkBlock>> reviewPlan(WeekPlan.Proposal proposal) {
            reviewAsked++;
            proposed = proposal;
            return review.apply(proposal);
        }
    }

    private final FakeDialogs dialogs = new FakeDialogs();
    private ScheduleBook book;
    private ViewState view;
    private ScheduleActions actions;
    private Schedule lecture;
    private Schedule report;
    private int saves;

    @BeforeEach
    void setUp() {
        lecture = Schedule.timed("Lecture", LocalDate.of(2026, 11, 11), "10:00", "11:00", Category.STUDY)
                .repeat(Repeat.WEEKLY, LocalDate.of(2026, 12, 2));
        report = Schedule.deadline("Report", LocalDate.of(2026, 11, 13), "18:00", Category.STUDY);
        book = new ScheduleBook(new ArrayList<>(List.of(lecture, report)), list -> saves++, CLOCK);
        view = new ViewState(CLOCK);
        actions = new ScheduleActions(book, view, dialogs);
    }

    private static ScheduleDraft draft(String title, LocalDate date) {
        return new ScheduleDraft(title, Kind.TIMED, date, LocalTime.of(14, 0), LocalTime.of(15, 0),
                Category.LIFE, Priority.MEDIUM, "", Repeat.NONE, null, -1, null);
    }

    private Occurrence occurrence(String title, LocalDate date) {
        return book.occurrences(date, date).stream().filter(o -> o.title().equals(title)).findFirst().orElseThrow();
    }

    private String status() {
        return book.lastChange().orElseThrow().statusText();
    }

    @Test
    void addSavesTheScheduleSelectsItsDateAndSaysAdded() {
        dialogs.result = new ScheduleFormDialog.Saved(draft("  Study group  ", LocalDate.of(2026, 11, 19)));
        actions.add(TODAY);
        assertEquals(Use.NEW, dialogs.usedForm);
        assertEquals("Added: Study group.", status());
        assertTrue(book.lastChange().orElseThrow().canUndo());
        assertEquals(LocalDate.of(2026, 11, 19), view.selected());
        assertEquals(1, book.occurrences(LocalDate.of(2026, 11, 19), LocalDate.of(2026, 11, 19)).size());
        assertTrue(saves > 0);
    }

    @Test
    void addWithCancelOrAnInvalidDraftChangesNothing() {
        actions.add(TODAY);
        dialogs.result = new ScheduleFormDialog.Saved(draft(" ", TODAY));
        actions.add(TODAY);
        assertEquals(2, book.schedules().size());
        assertTrue(book.lastChange().isEmpty());
    }

    @Test
    void newScheduleStartsAtTheNextFullHourOrTheClickedHalfHour() {
        // The fixed clock is in UTC and says 10:20, and the view state reads the time through that clock.
        actions.add(TODAY);
        assertEquals(LocalTime.of(11, 0), dialogs.shownDraft.start());
        actions.add(TODAY, LocalTime.of(9, 30));
        assertEquals(LocalTime.of(9, 30), dialogs.shownDraft.start());
        assertEquals(LocalTime.of(10, 30), dialogs.shownDraft.end());
        assertEquals(TODAY, dialogs.shownDraft.date());
    }

    @Test
    void defaultsFollowR_FORM_1AroundMidnight() {
        LocalDate day = TODAY;
        ScheduleDraft ten = ScheduleActions.defaults(day, null, day.atTime(10, 20));
        assertEquals(LocalTime.of(11, 0), ten.start());
        assertEquals(LocalTime.of(12, 0), ten.end());
        assertEquals(Kind.TIMED, ten.kind());
        assertEquals(Category.LIFE, ten.category());
        assertEquals(Priority.MEDIUM, ten.priority());
        assertEquals(Repeat.NONE, ten.repeat());
        ScheduleDraft late = ScheduleActions.defaults(day, null, day.atTime(22, 10));
        assertEquals(LocalTime.of(23, 0), late.start());
        assertEquals(LocalTime.of(23, 59), late.end());
        ScheduleDraft after = ScheduleActions.defaults(day, null, day.atTime(23, 40));
        assertEquals(LocalTime.of(23, 0), after.start());
        assertEquals(LocalTime.of(23, 59), after.end());
        ScheduleDraft week = ScheduleActions.defaults(day, LocalTime.of(23, 30), day.atTime(8, 0));
        assertEquals(LocalTime.of(23, 59), week.end());
        assertTrue(ten.problem().isPresent(), "an empty title cannot be saved");
    }

    @Test
    void editingAPlainScheduleSaysUpdated() {
        dialogs.result = new ScheduleFormDialog.Saved(new ScheduleDraft("Report v2", Kind.DEADLINE,
                LocalDate.of(2026, 11, 13), LocalTime.of(18, 0), null, Category.STUDY, Priority.HIGH, "", Repeat.NONE,
                null, -1, null));
        actions.edit(occurrence("Report", LocalDate.of(2026, 11, 13)));
        assertEquals(Use.EDIT, dialogs.usedForm);
        assertEquals(0, dialogs.scopeAsked);
        assertEquals("Updated: Report v2.", status());
        assertEquals(Priority.HIGH, book.schedules().stream().filter(x -> x.title().equals("Report v2"))
                .findFirst().orElseThrow().priority());
    }

    @Test
    void editingOneOccurrenceSkipsItAndAddsASingleSchedule() {
        dialogs.scope = Scope.THIS_ONLY;
        LocalDate second = LocalDate.of(2026, 11, 18);
        dialogs.result = new ScheduleFormDialog.Saved(new ScheduleDraft("Lecture (moved)", Kind.TIMED, second,
                LocalTime.of(13, 0), LocalTime.of(14, 0), Category.STUDY, Priority.MEDIUM, "", Repeat.NONE, null, -1,
                null));
        actions.edit(occurrence("Lecture", second));
        assertEquals(Use.EDIT_ONE, dialogs.usedForm);
        assertEquals(second, dialogs.shownDraft.date());
        assertEquals(Repeat.NONE, dialogs.shownDraft.repeat());
        assertEquals("Updated: Lecture (moved).", status());
        assertEquals(3, book.schedules().size());
        assertTrue(book.occurrences(second, second).stream().noneMatch(o -> o.title().equals("Lecture")));
        assertEquals(1, book.occurrences(second, second).stream().filter(o -> o.title().equals("Lecture (moved)")).count());
        // The other weeks of the series are untouched.
        assertEquals(1, book.occurrences(LocalDate.of(2026, 11, 25), LocalDate.of(2026, 11, 25)).size());
    }

    @Test
    void editingOneDoneOccurrenceMovesTheMarkAndCountsNothingRemoved() {
        LocalDate first = LocalDate.of(2026, 11, 20);
        Schedule rent = Schedule.deadline("Rent", first, "18:00", Category.LIFE)
                .repeat(Repeat.MONTHLY, first.plusMonths(3)).setDone(first, true);
        book = new ScheduleBook(new ArrayList<>(List.of(rent)), list -> { }, CLOCK);
        actions = new ScheduleActions(book, view, dialogs);
        dialogs.scope = Scope.THIS_ONLY;
        dialogs.result = new ScheduleFormDialog.Saved(new ScheduleDraft("Rent", Kind.DEADLINE, first,
                LocalTime.of(20, 0), null, Category.LIFE, Priority.MEDIUM, "", Repeat.NONE, null, -1, null));
        actions.edit(occurrence("Rent", first));
        assertEquals("Updated: Rent.", status());
    }

    @Test
    void deletingOneDoneOccurrenceCountsNothingRemoved() {
        LocalDate first = LocalDate.of(2026, 11, 20);
        Schedule rent = Schedule.deadline("Rent", first, "18:00", Category.LIFE)
                .repeat(Repeat.MONTHLY, first.plusMonths(3)).setDone(first, true);
        book = new ScheduleBook(new ArrayList<>(List.of(rent)), list -> { }, CLOCK);
        actions = new ScheduleActions(book, view, dialogs);
        dialogs.scope = Scope.THIS_ONLY;
        actions.delete(occurrence("Rent", first));
        assertEquals("Deleted: Rent.", status());
    }

    @Test
    void editingAllOccurrencesChangesTheSeriesAndShowsTheFirstDate() {
        dialogs.scope = Scope.ALL;
        ScheduleDraft changed = new ScheduleDraft("Lecture B", Kind.TIMED, LocalDate.of(2026, 11, 11),
                LocalTime.of(10, 0), LocalTime.of(11, 30), Category.STUDY, Priority.MEDIUM, "", Repeat.WEEKLY,
                LocalDate.of(2026, 12, 2), -1, null);
        dialogs.result = new ScheduleFormDialog.Saved(changed);
        actions.edit(occurrence("Lecture", LocalDate.of(2026, 11, 18)));
        assertEquals(Use.EDIT_ALL, dialogs.usedForm);
        assertEquals(LocalDate.of(2026, 11, 11), dialogs.shownDraft.date());
        assertEquals(Repeat.WEEKLY, dialogs.shownDraft.repeat());
        assertEquals("Updated: Lecture B.", status());
        assertEquals(2, book.schedules().size());
        assertEquals("Lecture B", occurrence("Lecture B", LocalDate.of(2026, 11, 25)).title());
    }

    @Test
    void cancellingTheScopeOrTheFormChangesNothing() {
        dialogs.scope = Scope.CANCEL;
        actions.edit(occurrence("Lecture", LocalDate.of(2026, 11, 18)));
        assertEquals(0, dialogs.confirmAsked);
        assertTrue(book.lastChange().isEmpty());
        dialogs.scope = Scope.ALL;
        actions.edit(occurrence("Lecture", LocalDate.of(2026, 11, 18)));
        assertTrue(book.lastChange().isEmpty());
        assertEquals(2, book.schedules().size());
    }

    @Test
    void theFormShowsTheOtherSchedulesOfTheDateButNotTheEditedOne() {
        actions.edit(occurrence("Report", LocalDate.of(2026, 11, 13)));
        LocalDate day = LocalDate.of(2026, 11, 11);
        assertEquals(List.of("Lecture"), dialogs.sameDay.apply(day).stream().map(Occurrence::title).toList());
        actions.add(day);
        assertEquals(1, dialogs.sameDay.apply(day).size());
    }

    @Test
    void deleteAsksToConfirmAndSaysDeleted() {
        actions.delete(occurrence("Report", LocalDate.of(2026, 11, 13)));
        assertEquals(1, dialogs.confirmAsked);
        assertEquals("Report", dialogs.confirmedTitle);
        assertEquals(0, dialogs.scopeAsked);
        assertEquals("Deleted: Report.", status());
        assertEquals(1, book.schedules().size());
    }

    @Test
    void declinedConfirmationKeepsTheSchedule() {
        dialogs.confirm = false;
        actions.delete(occurrence("Report", LocalDate.of(2026, 11, 13)));
        assertEquals(1, dialogs.confirmAsked);
        assertEquals(2, book.schedules().size());
        assertTrue(book.lastChange().isEmpty());
    }

    @Test
    void deletingOneOccurrenceOfARepeatAsksTheScopeAndDoesNotConfirmAgain() {
        dialogs.scope = Scope.THIS_ONLY;
        actions.delete(occurrence("Lecture", LocalDate.of(2026, 11, 18)));
        assertEquals(1, dialogs.scopeAsked);
        assertEquals(0, dialogs.confirmAsked);
        assertEquals("Deleted: Lecture.", status());
        assertEquals(2, book.schedules().size());
        assertTrue(book.occurrences(LocalDate.of(2026, 11, 18), LocalDate.of(2026, 11, 18)).isEmpty());
        assertEquals(1, book.occurrences(LocalDate.of(2026, 11, 25), LocalDate.of(2026, 11, 25)).size());
    }

    @Test
    void cancellingTheScopeOfADeleteChangesNothing() {
        dialogs.scope = Scope.CANCEL;
        actions.delete(occurrence("Lecture", LocalDate.of(2026, 11, 18)));
        assertEquals(0, dialogs.confirmAsked);
        assertTrue(book.lastChange().isEmpty());
    }

    @Test
    void deletingAllOccurrencesRemovesTheSeries() {
        dialogs.scope = Scope.ALL;
        actions.delete(occurrence("Lecture", LocalDate.of(2026, 11, 18)));
        assertEquals(1, book.schedules().size());
    }

    @Test
    void deleteFromTheFormDoesNotAskTheScopeAgain() {
        dialogs.scope = Scope.THIS_ONLY;
        dialogs.result = new ScheduleFormDialog.DeleteRequested();
        actions.edit(occurrence("Lecture", LocalDate.of(2026, 11, 18)));
        assertEquals(1, dialogs.scopeAsked);
        assertEquals(1, dialogs.confirmAsked);
        assertEquals("Deleted: Lecture.", status());
        assertTrue(book.occurrences(LocalDate.of(2026, 11, 18), LocalDate.of(2026, 11, 18)).isEmpty());
    }

    @Test
    void deletingADeadlineRemovesItsWorkBlocksAndSaysHowMany() {
        Schedule block = Schedule.workBlock(new WorkTarget(report, report.date()), LocalDate.of(2026, 11, 12),
                LocalTime.of(9, 0), LocalTime.of(10, 0));
        book = new ScheduleBook(new ArrayList<>(List.of(report, block)), list -> { }, CLOCK);
        actions = new ScheduleActions(book, view, dialogs);
        actions.delete(occurrence("Report", report.date()));
        assertEquals("Deleted: Report. 1 work block removed.", status());
        assertTrue(book.schedules().isEmpty());
    }

    @Test
    void markingDoneKeepsTheRowAndSaysMarkedDone() {
        Occurrence o = occurrence("Report", report.date());
        actions.toggleDone(o);
        assertEquals("Marked done: Report.", status());
        assertTrue(book.lastChange().orElseThrow().canUndo());
        assertTrue(occurrence("Report", report.date()).done());
        // R-DONE-3: the row is remembered so the open lists keep it.
        assertTrue(view.justDone().contains(new RowKey(report.id(), report.date())));
    }

    @Test
    void checkingSeveralRowsInARowKeepsAllOfThem() {
        Schedule second = Schedule.deadline("Essay", LocalDate.of(2026, 11, 14), "12:00", Category.STUDY);
        book = new ScheduleBook(new ArrayList<>(List.of(report, second)), list -> { }, CLOCK);
        actions = new ScheduleActions(book, view, dialogs);
        actions.toggleDone(occurrence("Report", report.date()));
        actions.toggleDone(occurrence("Essay", second.date()));
        assertEquals(2, view.justDone().size());
    }

    @Test
    void anotherChangeLetsTheCheckedRowsGo() {
        actions.toggleDone(occurrence("Report", report.date()));
        actions.setPriority(occurrence("Lecture", LocalDate.of(2026, 11, 11)), Priority.HIGH);
        assertTrue(view.justDone().isEmpty());
    }

    @Test
    void tabAndDateChangesLetTheCheckedRowsGo() {
        actions.toggleDone(occurrence("Report", report.date()));
        view.setTab(MainFrame.LIST_TAB);
        assertTrue(view.justDone().isEmpty());
        actions.toggleDone(occurrence("Report", report.date()));
        actions.toggleDone(occurrence("Report", report.date()));
        view.select(LocalDate.of(2026, 11, 20));
        assertTrue(view.justDone().isEmpty());
    }

    @Test
    void uncheckingLongAfterSaysReopenedWithoutACount() {
        actions.toggleDone(occurrence("Report", report.date()));
        actions.setPriority(occurrence("Lecture", LocalDate.of(2026, 11, 11)), Priority.LOW);
        actions.toggleDone(occurrence("Report", report.date()));
        assertEquals("Reopened: Report.", status());
        assertFalse(occurrence("Report", report.date()).done());
    }

    @Test
    void uncheckingRightAfterCheckingIsUndoAndBringsBackWorkBlocks() {
        // The block lies after the clock (2026-11-10 10:20), so checking the deadline removes it.
        Schedule block = Schedule.workBlock(new WorkTarget(report, report.date()), LocalDate.of(2026, 11, 12),
                LocalTime.of(9, 0), LocalTime.of(10, 0));
        book = new ScheduleBook(new ArrayList<>(List.of(report, block)), list -> { }, CLOCK);
        actions = new ScheduleActions(book, view, dialogs);
        actions.toggleDone(occurrence("Report", report.date()));
        assertEquals("Marked done: Report. 1 work block removed.", status());
        assertEquals(1, book.schedules().size());
        actions.toggleDone(occurrence("Report", report.date()));
        assertEquals("Undone.", status());
        assertEquals(2, book.schedules().size());
        assertFalse(occurrence("Report", report.date()).done());
    }

    @Test
    void doneIsPerOccurrenceOfARepeatingDeadline() {
        Schedule weekly = Schedule.deadline("Quiz", LocalDate.of(2026, 11, 11), "09:00", Category.STUDY)
                .repeat(Repeat.WEEKLY, LocalDate.of(2026, 12, 2));
        book = new ScheduleBook(new ArrayList<>(List.of(weekly)), list -> { }, CLOCK);
        actions = new ScheduleActions(book, view, dialogs);
        actions.toggleDone(occurrence("Quiz", LocalDate.of(2026, 11, 18)));
        assertTrue(occurrence("Quiz", LocalDate.of(2026, 11, 18)).done());
        assertFalse(occurrence("Quiz", LocalDate.of(2026, 11, 25)).done());
    }

    @Test
    void checkingASchedulesThatIsNotADeadlineDoesNothing() {
        actions.toggleDone(occurrence("Lecture", LocalDate.of(2026, 11, 11)));
        assertTrue(book.lastChange().isEmpty());
    }

    @Test
    void priorityChangeNamesTheSeriesWhenItRepeats() {
        actions.setPriority(occurrence("Lecture", LocalDate.of(2026, 11, 18)), Priority.HIGH);
        assertEquals("Priority changed: Lecture (all occurrences).", status());
        assertEquals(0, dialogs.scopeAsked);
        assertEquals(Priority.HIGH, occurrence("Lecture", LocalDate.of(2026, 11, 25)).priority());
        actions.setPriority(occurrence("Report", report.date()), Priority.LOW);
        assertEquals("Priority changed: Report.", status());
    }

    @Test
    void sameOrNoPriorityChangeIsNotAChange() {
        actions.setPriority(occurrence("Report", report.date()), Priority.MEDIUM);
        assertTrue(book.lastChange().isEmpty());
    }

    @Test
    void undoGoesBackAndSaysUndoneWithoutUndo() {
        dialogs.confirm = true;
        actions.delete(occurrence("Report", report.date()));
        actions.undo();
        assertEquals("Undone.", status());
        assertFalse(book.lastChange().orElseThrow().canUndo());
        assertEquals(2, book.schedules().size());
    }

    @Test
    void anOccurrenceFromBeforeUndoStillWorks() {
        Occurrence old = occurrence("Report", report.date());
        actions.setPriority(occurrence("Lecture", LocalDate.of(2026, 11, 11)), Priority.HIGH);
        actions.undo();
        // Undo put back copies, so the old occurrence points at an object that is gone.
        actions.toggleDone(old);
        assertEquals("Marked done: Report.", status());
        assertTrue(occurrence("Report", report.date()).done());
    }

    @Test
    void theSelectedRowSurvivesUndoAsLongAsTheRowExists() {
        Occurrence report13 = occurrence("Report", report.date());
        view.selectRow(RowKey.of(report13), Origin.TABLE);
        actions.setPriority(occurrence("Lecture", LocalDate.of(2026, 11, 11)), Priority.HIGH);
        actions.undo();
        RowKey kept = view.selectedRow();
        assertEquals(RowKey.of(report13), kept);
        assertEquals(Origin.TABLE, view.selectedOrigin());
        // The key still finds a row in the book after Undo replaced the objects.
        assertTrue(book.occurrences(kept.date(), kept.date()).stream().anyMatch(o -> RowKey.of(o).equals(kept)));
    }

    @Test
    void deletingTheSelectedRowClearsTheSelection() {
        view.selectRow(RowKey.of(occurrence("Report", report.date())), Origin.DAY_LIST);
        actions.delete(occurrence("Report", report.date()));
        assertEquals(null, view.selectedRow());
    }

    @Test
    void workBlocksOpenAsWorkBlocksAndSaveAsUpdates() {
        Schedule block = Schedule.workBlock(new WorkTarget(report, report.date()), LocalDate.of(2026, 11, 12),
                LocalTime.of(9, 0), LocalTime.of(10, 0));
        book = new ScheduleBook(new ArrayList<>(List.of(report, block)), list -> { }, CLOCK);
        actions = new ScheduleActions(book, view, dialogs);
        ScheduleDraft shown = block.toDraft();
        dialogs.result = new ScheduleFormDialog.Saved(new ScheduleDraft("Work: Report", shown.kind(), shown.date(),
                LocalTime.of(15, 0), LocalTime.of(16, 0), shown.category(), shown.priority(), shown.memo(),
                Repeat.NONE, null, shown.workMinutes(), shown.plannedFor()));
        actions.edit(occurrence(block.title(), block.date()));
        assertEquals(Use.WORK_BLOCK, dialogs.usedForm);
        assertEquals("Work: Report", dialogs.shownDraft.title());
        assertEquals("Updated: Work: Report.", status());
        assertEquals(LocalTime.of(15, 0), book.schedules().stream().filter(s -> s.plannedFor() != null).findFirst()
                .orElseThrow().start());
    }

    @Test
    void headingsFollowTheSpecTable() {
        assertEquals("New Schedule", ScheduleFormDialog.heading(Use.NEW));
        assertEquals("Edit Schedule", ScheduleFormDialog.heading(Use.EDIT));
        assertEquals("Edit Schedule", ScheduleFormDialog.heading(Use.EDIT_ONE));
        assertEquals("Edit Schedule (all occurrences)", ScheduleFormDialog.heading(Use.EDIT_ALL));
    }

    @Test
    void onlyAnExistingScheduleCanBeDeletedFromTheForm() {
        assertFalse(ScheduleFormDialog.deletable(Use.NEW));
        assertFalse(ScheduleFormDialog.deletable(Use.PREVIEW_ROW));
        assertFalse(ScheduleFormDialog.deletable(Use.PREVIEW_CLASS));
        assertTrue(ScheduleFormDialog.deletable(Use.EDIT));
        assertTrue(ScheduleFormDialog.deletable(Use.EDIT_ONE));
        assertTrue(ScheduleFormDialog.deletable(Use.EDIT_ALL));
        assertTrue(ScheduleFormDialog.deletable(Use.WORK_BLOCK));
    }

    @Test
    void importedRowsAreOneChangeThatSaysHowManyWereAddedAndUndoTakesThemAllBack() {
        view.rememberJustDone(RowKey.of(occurrence("Report", LocalDate.of(2026, 11, 13))));
        int before = book.schedules().size();
        actions.addImported(List.of(draft("Algorithms", LocalDate.of(2026, 11, 17)),
                draft("  Databases ", LocalDate.of(2026, 11, 18))));

        assertEquals("2 schedules added.", status());
        assertEquals(before + 2, book.schedules().size());
        assertTrue(book.schedules().stream().anyMatch(s -> s.title().equals("Databases")));
        assertTrue(view.justDone().isEmpty());
        actions.undo();
        assertEquals(before, book.schedules().size());
    }

    @Test
    void oneImportedRowSaysSchedulesInTheSingularAndNoRowsChangeNothing() {
        actions.addImported(List.of());
        assertTrue(book.lastChange().isEmpty());
        actions.addImported(List.of(draft("Algorithms", LocalDate.of(2026, 11, 17))));
        assertEquals("1 schedule added.", status());
    }

    private static int minutes(Schedule block) {
        return (block.end().toSecondOfDay() - block.start().toSecondOfDay()) / 60;
    }

    private List<Schedule> blocks() {
        return book.schedules().stream().filter(s -> s.plannedFor() != null).toList();
    }

    @Test
    void thePlanningPeriodRunsFromNowToTheSaturdayOfTheShownWeek() {
        actions.planWeek();
        assertEquals(1, dialogs.reviewAsked);
        // Tuesday 10:20 to Saturday: R-PLAN-1.
        assertEquals(LocalDate.of(2026, 11, 10).atTime(10, 20), dialogs.proposed.from());
        assertEquals(LocalDate.of(2026, 11, 14), dialogs.proposed.until());
        // A later week is planned from its Sunday.
        Schedule next = Schedule.deadline("Next", LocalDate.of(2026, 11, 20), "18:00", Category.STUDY);
        book = new ScheduleBook(new ArrayList<>(List.of(lecture, report, next)), list -> { }, CLOCK);
        actions = new ScheduleActions(book, view, dialogs);
        view.select(LocalDate.of(2026, 11, 18));
        actions.planWeek();
        assertEquals(LocalDate.of(2026, 11, 15).atStartOfDay(), dialogs.proposed.from());
        assertEquals(LocalDate.of(2026, 11, 21), dialogs.proposed.until());
    }

    @Test
    void aWeekThatIsOverCannotBePlannedAndSaysNothing() {
        view.select(LocalDate.of(2026, 11, 4));
        actions.planWeek();
        assertEquals(0, dialogs.reviewAsked);
        assertEquals(0, dialogs.nothingToPlanAsked);
        assertTrue(book.lastChange().isEmpty());
    }

    @Test
    void aWeekWithoutDeadlinesToPlanOnlyTellsSoAndOpensNoPreview() {
        // The deadline is due in the next week, and a second one needs no work (R-PLAN-2).
        Schedule later = Schedule.deadline("Later", LocalDate.of(2026, 11, 20), "18:00", Category.STUDY);
        Schedule free = Schedule.deadline("Pay fee", LocalDate.of(2026, 11, 12), "18:00", Category.LIFE);
        book = new ScheduleBook(new ArrayList<>(List.of(later, free)), list -> { }, CLOCK);
        actions = new ScheduleActions(book, view, dialogs);
        actions.planWeek();
        assertEquals(1, dialogs.nothingToPlanAsked);
        assertEquals(0, dialogs.reviewAsked);
        assertTrue(book.lastChange().isEmpty());
    }

    @Test
    void confirmingStoresOnlyTheCheckedBlocksAsOneChange() {
        Schedule paper = Schedule.deadline("Paper", LocalDate.of(2026, 11, 14), "18:00", Category.STUDY);
        book = new ScheduleBook(new ArrayList<>(List.of(lecture, report, paper)), list -> saves++, CLOCK);
        actions = new ScheduleActions(book, view, dialogs);
        dialogs.review = p -> Optional.of(List.of(p.plan().blocks().get(0)));
        view.rememberJustDone(RowKey.of(occurrence("Report", report.date())));
        actions.planWeek();
        assertTrue(dialogs.proposed.plan().blocks().size() > 1);
        assertEquals(1, blocks().size());
        WorkBlock first = dialogs.proposed.plan().blocks().get(0);
        Schedule stored = blocks().get(0);
        assertEquals(first.date(), stored.date());
        assertEquals(first.start(), stored.start());
        assertEquals(first.deadline().title(), stored.plannedFor().deadline().title());
        assertEquals("1 work block added.", status());
        assertTrue(book.lastChange().orElseThrow().canUndo());
        assertTrue(view.justDone().isEmpty());
    }

    @Test
    void cancellingThePreviewStoresNothing() {
        dialogs.review = p -> Optional.empty();
        actions.planWeek();
        assertTrue(blocks().isEmpty());
        assertTrue(book.lastChange().isEmpty());
    }

    @Test
    void aPlanSaysHowManyBlocksWereAddedWithoutAnyRemovedCountAndUndoTakesItBack() {
        actions.planWeek();
        // Two hours from the first free half hour after 10:20, joined into one block.
        assertEquals(1, blocks().size());
        assertEquals("1 work block added.", status());
        assertEquals(120, blocks().stream().mapToInt(ScheduleActionsTest::minutes).sum());
        view.select(TODAY);
        // The same deadline in the same week: the unstarted block is replaced, and the text counts nothing removed.
        actions.planWeek();
        assertEquals("1 work block added.", status());
        assertEquals(1, blocks().size());
        actions.undo();
        assertEquals("Undone.", status());
        assertEquals(1, blocks().size());
        assertFalse(book.lastChange().orElseThrow().canUndo());
    }

    @Test
    void severalBlocksUseThePluralAndReplacingPlansLeavesNoRemovedCount() {
        Schedule paper = Schedule.deadline("Paper", LocalDate.of(2026, 11, 14), "18:00", Category.STUDY);
        book = new ScheduleBook(new ArrayList<>(List.of(lecture, report, paper)), list -> { }, CLOCK);
        actions = new ScheduleActions(book, view, dialogs);
        actions.planWeek();
        assertEquals(blocks().size() + " work blocks added.", status());
        assertTrue(blocks().size() > 1);
        int first = blocks().size();
        actions.planWeek();
        assertEquals(first + " work blocks added.", status());
        assertEquals(first, blocks().size());
    }

    @Test
    void workThatAlreadyStartedCountsAsDoneSoThereIsNothingLeftToPlan() {
        actions.planWeek();
        Clock later = Clock.fixed(Instant.parse("2026-11-10T11:00:00Z"), ZoneId.of("UTC"));
        ScheduleBook laterBook = new ScheduleBook(new ArrayList<>(book.schedules()), list -> { }, later);
        ScheduleActions laterActions = new ScheduleActions(laterBook, new ViewState(later), dialogs);
        laterActions.planWeek();
        // The 10:30 block is under way, so all 2 hours are done and the deadline has nothing left (R-PLAN-9).
        assertEquals(1, dialogs.nothingToPlanAsked);
        assertEquals(1, laterBook.schedules().stream().filter(s -> s.plannedFor() != null).count());
    }

    @Test
    void clearPlanRemovesTheUnstartedBlocksOfTheShownWeekAndSaysPlanCleared() {
        Schedule past = Schedule.workBlock(new WorkTarget(report, report.date()), LocalDate.of(2026, 11, 9),
                LocalTime.of(9, 0), LocalTime.of(10, 0));
        Schedule inWeek = Schedule.workBlock(new WorkTarget(report, report.date()), LocalDate.of(2026, 11, 12),
                LocalTime.of(9, 0), LocalTime.of(10, 0));
        Schedule otherWeek = Schedule.workBlock(new WorkTarget(report, report.date()), LocalDate.of(2026, 11, 16),
                LocalTime.of(9, 0), LocalTime.of(10, 0));
        book = new ScheduleBook(new ArrayList<>(List.of(report, past, inWeek, otherWeek)), list -> { }, CLOCK);
        actions = new ScheduleActions(book, view, dialogs);
        view.rememberJustDone(RowKey.of(occurrence("Report", report.date())));
        actions.clearPlan();
        assertEquals("Plan cleared.", status());
        assertTrue(book.lastChange().orElseThrow().canUndo());
        assertEquals(3, book.schedules().size());
        assertTrue(book.schedules().contains(past));
        assertTrue(book.schedules().contains(otherWeek));
        assertTrue(view.justDone().isEmpty());
        actions.undo();
        assertEquals(4, book.schedules().size());
    }

    @Test
    void clearPlanWithNoUnstartedBlockChangesNothing() {
        actions.clearPlan();
        assertTrue(book.lastChange().isEmpty());
    }

    @Test
    void markingADeadlineDoneRemovesItsUnstartedBlocksAfterPlanning() {
        actions.planWeek();
        assertEquals(1, blocks().size());
        actions.toggleDone(occurrence("Report", report.date()));
        assertEquals("Marked done: Report. 1 work block removed.", status());
        assertTrue(blocks().isEmpty());
        actions.undo();
        assertEquals(1, blocks().size());
    }

    @Test
    void aChangeThatMakesThePlanOutOfDateSaysSoAtTheEndOfItsStatus() {
        actions.planWeek();
        Schedule block = blocks().get(0);
        // A timed schedule on top of the work block (R-PLAN-12).
        ScheduleDraft onTop = new ScheduleDraft("Study group", Kind.TIMED, block.date(), block.start(),
                block.start().plusHours(1), Category.STUDY, Priority.MEDIUM, "", Repeat.NONE, null, -1, null);
        dialogs.result = new ScheduleFormDialog.Saved(onTop);
        actions.add(block.date());
        assertEquals("Added: Study group. Plan is out of date.", status());
        // A second change while it is already out of date does not repeat the sentence.
        dialogs.result = new ScheduleFormDialog.Saved(draft("Dinner", block.date()));
        actions.add(block.date());
        assertEquals("Added: Dinner.", status());
        // Undo says only Undone. (R-UNDO-4).
        actions.undo();
        assertEquals("Undone.", status());
    }

    @Test
    void aChangeThatLeavesThePlanFitSaysNothingAboutIt() {
        actions.planWeek();
        dialogs.result = new ScheduleFormDialog.Saved(draft("Dinner", LocalDate.of(2026, 11, 12)));
        actions.add(LocalDate.of(2026, 11, 12));
        assertEquals("Added: Dinner.", status());
    }

    @Test
    void aChangeThatMakesAnotherWeeksPlanOutOfDateStillSaysSo() {
        actions.planWeek();
        Schedule block = blocks().get(0);
        // Another week is on the screen when the schedule is added on top of the block.
        view.select(LocalDate.of(2026, 11, 18));
        ScheduleDraft onTop = new ScheduleDraft("Study group", Kind.TIMED, block.date(), block.start(),
                block.start().plusHours(1), Category.STUDY, Priority.MEDIUM, "", Repeat.NONE, null, -1, null);
        dialogs.result = new ScheduleFormDialog.Saved(onTop);
        actions.add(LocalDate.of(2026, 11, 18));
        assertEquals("Added: Study group. Plan is out of date.", status());
    }

    @Test
    void theRemovedCountComesBeforeThePlanOutOfDateSentence() {
        Schedule paper = Schedule.deadline("Paper", LocalDate.of(2026, 11, 14), "18:00", Category.STUDY);
        book = new ScheduleBook(new ArrayList<>(List.of(lecture, report, paper)), list -> { }, CLOCK);
        actions = new ScheduleActions(book, view, dialogs);
        actions.planWeek();
        Schedule paperBlock = blocks().stream().filter(b -> b.plannedFor().deadline() == paper).findFirst()
                .orElseThrow();
        // The report becomes a timed meeting over the paper's block: its own blocks go and the paper's plan is hit.
        dialogs.result = new ScheduleFormDialog.Saved(new ScheduleDraft("Meeting", Kind.TIMED, paperBlock.date(),
                paperBlock.start(), paperBlock.start().plusHours(1), Category.STUDY, Priority.MEDIUM, "",
                Repeat.NONE, null, -1, null));
        actions.edit(occurrence("Report", report.date()));
        assertEquals("Updated: Meeting. 1 work block removed. Plan is out of date.", status());
    }
}
