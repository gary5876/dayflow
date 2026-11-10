package dayflow.ui;

import dayflow.model.Category;
import dayflow.model.Kind;
import dayflow.model.Occurrence;
import dayflow.model.Priority;
import dayflow.model.Repeat;
import dayflow.model.Schedule;
import dayflow.model.ScheduleDraft;
import dayflow.model.ScheduleEdits;
import dayflow.plan.PlanStatus;
import dayflow.plan.SimplePlanner;
import dayflow.plan.WorkBlock;
import dayflow.plan.WorkPlanner;
import dayflow.store.Change;
import dayflow.store.ScheduleBook;
import dayflow.ui.Dialogs.Scope;
import dayflow.ui.ScheduleFormDialog.Use;
import dayflow.ui.ViewState.RowKey;
import java.awt.Window;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

/**
 * What the user does to the schedules, from the dialog to {@code ScheduleBook.change} (D-27). The panels
 * only call these methods. The texts of the status bar are made here from the spec (R-UNDO-2).
 */
final class ScheduleActions {
    private final ScheduleBook book;
    private final ViewState view;
    private final Dialogs dialogs;
    private final WorkPlanner planner = new SimplePlanner();
    /** The change that marked a row done, and the row, for R-DONE-5. */
    private Change doneChange;
    private RowKey doneKey;

    /** @param owner the window that dialogs open over */
    ScheduleActions(ScheduleBook book, ViewState view, Supplier<Window> owner) {
        this(book, view, new SwingDialogs(owner));
    }

    ScheduleActions(ScheduleBook book, ViewState view, Dialogs dialogs) {
        this.book = book;
        this.view = view;
        this.dialogs = dialogs;
        book.statusNote(this::outOfDateNote);
    }

    /**
     * R-PLAN-12: a change that makes a plan out of date says so at the end of its status, on any tab. Every week
     * that holds a work block that has not started is looked at, before and after the change, and not only the
     * week on the screen. A plan that was out of date already does not get the sentence again.
     */
    private String outOfDateNote(List<Schedule> before, List<Schedule> after) {
        LocalDateTime now = view.now();
        Set<LocalDate> weeks = new TreeSet<>();
        for (List<Schedule> list : List.of(before, after)) {
            for (Schedule s : list) {
                if (s.plannedFor() != null && !s.date().atTime(s.start()).isBefore(now)) {
                    weeks.add(s.date().minusDays(s.date().getDayOfWeek().getValue() % 7));
                }
            }
        }
        for (LocalDate weekStart : weeks) {
            if (!outOfDate(before, weekStart, now) && outOfDate(after, weekStart, now)) {
                return "Plan is out of date.";
            }
        }
        return "";
    }

    private static boolean outOfDate(List<Schedule> schedules, LocalDate weekStart, LocalDateTime now) {
        List<Occurrence> week = new ArrayList<>();
        for (Schedule s : schedules) {
            week.addAll(s.occurrences(weekStart, weekStart.plusDays(6)));
        }
        return PlanStatus.of(week, weekStart, now).outOfDate();
    }

    /** F-16: goes back to the state before the last change. */
    void undo() {
        view.forgetJustDone();
        book.undo();
    }

    /** F-06 from a day: the form opens with the defaults of R-FORM-1. */
    void add(LocalDate date) {
        add(date, null);
    }

    /** F-06. {@code start} is the half hour clicked in the week, or null for the next full hour. */
    void add(LocalDate date, LocalTime start) {
        ScheduleDraft initial = defaults(date, start, view.now());
        if (dialogs.form(Use.NEW, initial, day -> sameDay(day, null)) instanceof ScheduleFormDialog.Saved saved
                && saved.values().problem().isEmpty()) {
            ScheduleDraft values = saved.values().normalized();
            view.forgetJustDone();
            book.change("Added: " + values.title(), all -> ScheduleEdits.add(all, values));
            view.select(values.date());
        }
    }

    /** R-FORM-1: Timed, Life, Medium, no repeat, one hour from the next full hour and never past 23:59. */
    static ScheduleDraft defaults(LocalDate date, LocalTime start, LocalDateTime now) {
        LocalTime from = start;
        if (from == null) {
            from = now.toLocalTime().truncatedTo(java.time.temporal.ChronoUnit.HOURS).plusHours(1);
            // Past 23:00 the next full hour is midnight, which is the next day.
            if (from.isBefore(LocalTime.of(1, 0))) {
                from = LocalTime.of(23, 0);
            }
        }
        LocalTime to = from.getHour() == 23 ? LocalTime.of(23, 59) : from.plusHours(1);
        if (to.isBefore(from)) {
            to = LocalTime.of(23, 59);
        }
        return new ScheduleDraft("", Kind.TIMED, date, from, to, Category.LIFE, Priority.MEDIUM, "", Repeat.NONE,
                null, -1, null);
    }

    /** F-08 and F-09 from the form: repeating schedules ask for the scope first (R-REP-1). */
    void edit(Occurrence o) {
        Schedule s = live(o).orElse(null);
        if (s == null) {
            return;
        }
        Scope scope = Scope.ALL;
        Use use = s.plannedFor() != null ? Use.WORK_BLOCK : Use.EDIT;
        ScheduleDraft initial = s.toDraft();
        if (s.plannedFor() != null) {
            // A work block stores no title and shows the one of its deadline, but a draft without a title
            // cannot be saved. The form gets the shown title, and the block keeps storing none.
            initial = withTitle(initial, s.title());
        }
        if (s.repeat() != Repeat.NONE) {
            scope = dialogs.chooseScope(s.title());
            if (scope == Scope.CANCEL) {
                return;
            }
            if (scope == Scope.THIS_ONLY) {
                use = Use.EDIT_ONE;
                initial = new ScheduleDraft(initial.title(), initial.kind(), o.date(), initial.start(),
                        initial.end(), initial.category(), initial.priority(), initial.memo(), Repeat.NONE, null,
                        initial.workMinutes(), initial.plannedFor());
            } else {
                use = Use.EDIT_ALL;
            }
        }
        String id = s.id();
        ScheduleFormDialog.Result result = dialogs.form(use, initial, day -> sameDay(day, id));
        if (result instanceof ScheduleFormDialog.Saved saved && saved.values().problem().isEmpty()) {
            ScheduleDraft values = saved.values().normalized();
            if (s.plannedFor() != null) {
                values = withTitle(values, s.toDraft().title());
            }
            ScheduleDraft changed = values;
            Scope chosen = scope;
            view.forgetJustDone();
            book.change("Updated: " + (s.plannedFor() != null ? s.title() : values.title()), all -> {
                Schedule target = find(all, id);
                if (chosen == Scope.THIS_ONLY) {
                    ScheduleEdits.updateOne(all, new Occurrence(target, o.date()), changed);
                } else {
                    ScheduleEdits.updateAll(all, target, changed);
                }
            });
        } else if (result instanceof ScheduleFormDialog.DeleteRequested) {
            // The scope was already chosen, so only the confirmation is left (R-REP-6).
            deleteWith(s, o.date(), scope, true);
        }
    }

    private static ScheduleDraft withTitle(ScheduleDraft d, String title) {
        return new ScheduleDraft(title, d.kind(), d.date(), d.start(), d.end(), d.category(), d.priority(), d.memo(),
                d.repeat(), d.repeatUntil(), d.workMinutes(), d.plannedFor());
    }

    /** F-09: a repeating schedule asks for the scope, any other asks to confirm. Nothing goes without a yes. */
    void delete(Occurrence o) {
        Schedule s = live(o).orElse(null);
        if (s == null) {
            return;
        }
        Scope scope = Scope.ALL;
        boolean repeating = s.repeat() != Repeat.NONE;
        if (repeating) {
            scope = dialogs.chooseScope(s.title());
            if (scope == Scope.CANCEL) {
                return;
            }
        }
        // F-09: for a repeating schedule the scope pop-up, which has its own Cancel, takes the place of the question.
        deleteWith(s, o.date(), scope, !repeating);
    }

    private void deleteWith(Schedule s, LocalDate date, Scope scope, boolean confirm) {
        if (confirm && !dialogs.confirmDelete(s.title())) {
            return;
        }
        String id = s.id();
        String title = s.title();
        view.forgetJustDone();
        view.clearSelection();
        book.change("Deleted: " + title, all -> {
            Schedule target = find(all, id);
            if (scope == Scope.THIS_ONLY) {
                ScheduleEdits.deleteOne(all, new Occurrence(target, date));
            } else {
                ScheduleEdits.deleteAll(all, target);
            }
        });
    }

    /**
     * R-LIST-1, R-DONE-3 and R-DONE-5. Checking a row keeps it in the open lists. Unchecking right after
     * checking is the same as Undo, so the work blocks that the check removed come back.
     */
    void toggleDone(Occurrence o) {
        Schedule s = live(o).orElse(null);
        if (s == null || s.kind() != Kind.DEADLINE) {
            return;
        }
        RowKey key = new RowKey(s.id(), o.date());
        LocalDate date = o.date();
        String id = s.id();
        if (s.isDone(date)) {
            boolean stillLast = doneChange != null && key.equals(doneKey)
                    && book.lastChange().orElse(null) == doneChange && doneChange.canUndo();
            if (stillLast) {
                doneChange = null;
                book.undo();
            } else {
                book.changeWithoutCount("Reopened: " + s.title(),
                        all -> ScheduleEdits.setDone(all, new Occurrence(find(all, id), date), false));
            }
        } else {
            view.rememberJustDone(key);
            doneChange = book.change("Marked done: " + s.title(),
                    all -> ScheduleEdits.setDone(all, new Occurrence(find(all, id), date), true));
            doneKey = key;
        }
    }

    /** R-IMP-9, R-IMP-10 and R-UNDO-5: stores the checked rows of a preview as one change, which Undo takes back whole. */
    void addImported(List<ScheduleDraft> drafts) {
        if (drafts.isEmpty()) {
            return;
        }
        List<ScheduleDraft> values = drafts.stream().map(ScheduleDraft::normalized).toList();
        String text = values.size() == 1 ? "1 schedule added" : values.size() + " schedules added";
        view.forgetJustDone();
        book.change(text, all -> values.forEach(v -> ScheduleEdits.add(all, v)));
    }

    /**
     * F-15: plans the shown week, shows the proposal, and stores the checked blocks as one change (R-PLAN-5,
     * R-PLAN-6). A week with nothing to plan only says so (R-PLAN-9).
     */
    void planWeek() {
        LocalDateTime now = view.now();
        LocalDate weekStart = view.weekStart();
        if (!WeekPlan.canPlan(weekStart, now)) {
            return;
        }
        // Blocks kept from other weeks count as work done or already placed, so the planner needs a wide view.
        List<Occurrence> all = book.occurrences(weekStart.minusYears(1), weekStart.plusYears(1));
        WeekPlan.Proposal proposal = WeekPlan.propose(all, weekStart, now, planner).orElse(null);
        if (proposal == null) {
            dialogs.nothingToPlan();
            return;
        }
        List<WorkBlock> chosen = dialogs.reviewPlan(proposal).orElse(List.of());
        if (chosen.isEmpty()) {
            return;
        }
        List<PlannedBlock> blocks = chosen.stream().map(PlannedBlock::of).toList();
        String text = chosen.size() == 1 ? "1 work block added" : chosen.size() + " work blocks added";
        LocalDateTime savedAt = view.now();
        view.forgetJustDone();
        book.changeWithoutCount(text, list -> ScheduleEdits.replacePlan(list, savedAt, proposal.from().toLocalDate(),
                proposal.until(), blocks.stream().map(b -> b.in(list)).toList()));
    }

    /** A proposed block by the id of its deadline, because the objects of the book can be replaced by an Undo. */
    private record PlannedBlock(String deadlineId, LocalDate deadlineDate, WorkBlock block) {
        static PlannedBlock of(WorkBlock block) {
            return new PlannedBlock(block.deadline().schedule().id(), block.deadline().date(), block);
        }

        WorkBlock in(List<Schedule> list) {
            return new WorkBlock(new Occurrence(find(list, deadlineId), deadlineDate), block.date(), block.start(),
                    block.end());
        }
    }

    /** R-PLAN-8: removes the work blocks of the shown week that have not started. */
    void clearPlan() {
        LocalDateTime now = view.now();
        LocalDate weekStart = view.weekStart();
        List<Occurrence> week = book.occurrences(weekStart, weekStart.plusDays(6));
        if (!PlanStatus.of(week, weekStart, now).clearable()) {
            return;
        }
        view.forgetJustDone();
        book.changeWithoutCount("Plan cleared", list -> ScheduleEdits.clearPlan(list, now, weekStart));
    }

    /** R-LT-10: the priority belongs to the schedule, so a repeating one changes as a whole without asking. */
    void setPriority(Occurrence o, Priority priority) {
        Schedule s = live(o).orElse(null);
        if (s == null || s.priority() == priority) {
            return;
        }
        String id = s.id();
        String suffix = s.repeat() != Repeat.NONE ? " (all occurrences)" : "";
        view.forgetJustDone();
        book.change("Priority changed: " + s.title() + suffix, all -> ScheduleEdits.setPriority(find(all, id), priority));
    }

    /** The schedule now in the book that the occurrence belongs to. Undo replaces the objects, not the ids. */
    private Optional<Schedule> live(Occurrence o) {
        return book.schedules().stream().filter(s -> s.id().equals(o.schedule().id())).findFirst();
    }

    private static Schedule find(List<Schedule> all, String id) {
        return all.stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow();
    }

    /** The occurrences on a date except those of the schedule being edited (R-FORM-5). */
    private List<Occurrence> sameDay(LocalDate date, String exceptId) {
        return book.occurrences(date, date).stream()
                .filter(o -> !o.schedule().id().equals(exceptId)).toList();
    }
}
