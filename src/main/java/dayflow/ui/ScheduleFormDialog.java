package dayflow.ui;

import dayflow.model.Category;
import dayflow.model.Kind;
import dayflow.model.Occurrence;
import dayflow.model.Priority;
import dayflow.model.Repeat;
import dayflow.model.ScheduleDraft;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SpinnerDateModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import javax.swing.text.AbstractDocument;

/**
 * Form for adding and editing one schedule. The same dialog is used for both, and it hands back what the
 * user decided as a {@link Result}. It changes nothing itself (D-27).
 */
final class ScheduleFormDialog extends JDialog {
    /** Where the form was opened from. It decides the title, the rows and the buttons. */
    enum Use {
        /** F-06: a new schedule. */
        NEW,
        /** F-08: a schedule that does not repeat. */
        EDIT,
        /** R-REP-2: one date of a repeating schedule, without the repeat rows. */
        EDIT_ONE,
        /** R-REP-3: a whole repeating schedule. */
        EDIT_ALL,
        /** R-FORM-10: a work block, whose title, type and category follow its deadline. */
        WORK_BLOCK,
        /** A row of a posting or text import: like an edit, but there is nothing to delete yet. */
        PREVIEW_ROW,
        /**
         * A class row of a timetable import. Only the title, date, time and memo can be corrected. The repeat
         * is shown as Weekly and cannot be changed, and its end follows the preview, so it is not shown.
         */
        PREVIEW_CLASS
    }

    /** What the user decided. */
    sealed interface Result permits Saved, DeleteRequested, Cancelled {
    }

    /** Save was pressed. The values passed the check of {@link ScheduleDraft#problem()} and are normalized. */
    record Saved(ScheduleDraft values) implements Result {
    }

    /** Delete was pressed. The caller asks for the confirmation (R-REP-6). */
    record DeleteRequested() implements Result {
    }

    /** Cancel was pressed or the window was closed. */
    record Cancelled() implements Result {
    }

    private static final LocalDate TIME_DAY = LocalDate.of(2026, 1, 1);

    private final Use use;
    private final ScheduleDraft initial;
    private final Function<LocalDate, List<Occurrence>> sameDay;
    private final boolean workBlock;
    private final boolean repeatable;
    /** A work block or a timetable class: the type, category and priority are not the user's to change. */
    private final boolean fixedKind;
    private final JTextField title = new JTextField(30);
    private final Map<Kind, JRadioButton> kinds = new EnumMap<>(Kind.class);
    private final JSpinner date;
    private final JSpinner start;
    private final JSpinner end;
    private final JSpinner due;
    private final JSpinner work;
    private final JComboBox<Category> category = new JComboBox<>(Category.values());
    private final JComboBox<Priority> priority = new JComboBox<>(Priority.values());
    private final JComboBox<Repeat> repeat = new JComboBox<>(Repeat.values());
    private final JSpinner until;
    private final JTextArea memo = new JTextArea(3, 30);
    private final JLabel onThisDay = new JLabel();
    private final JButton save = new JButton("Save");
    private final JButton more = new JButton();
    private final Row timeRow;
    private final Row dueRow;
    private final Row sameDayRow;
    private final Row priorityRow;
    private final Row repeatRow;
    private final Row memoRow;
    private final JPanel timePanel = flow();
    private final JPanel duePanel = flow();
    private final JLabel timeLabel = new JLabel("Time");
    private final JLabel dueLabel = new JLabel("Due");
    private Result result = new Cancelled();
    private LocalTime lastStart;
    private boolean workTouched;
    private boolean adjusting;
    private boolean expanded;

    /** A label with the field that goes with it, shown or hidden together. */
    private record Row(JLabel label, JComponent field) {
        void show(boolean visible) {
            label.setVisible(visible);
            field.setVisible(visible);
        }
    }

    /** R-FORM-8: only a schedule that exists can be deleted, so a new schedule and a preview row cannot. */
    static boolean deletable(Use use) {
        return use != Use.NEW && use != Use.PREVIEW_ROW && use != Use.PREVIEW_CLASS;
    }

    /** The title of the window for each use. */
    static String heading(Use use) {
        return switch (use) {
            case NEW -> "New Schedule";
            case EDIT_ALL -> "Edit Schedule (all occurrences)";
            default -> "Edit Schedule";
        };
    }

    /**
     * Opens the form over the owner and waits until it is closed.
     *
     * @param initial the values to start from. For {@link Use#EDIT_ONE} the date is the one being edited
     * @param sameDay the schedules on that date, shown so overlaps are visible before saving
     */
    static Result open(Window owner, Use use, ScheduleDraft initial, List<Occurrence> sameDay) {
        return open(owner, use, initial, day -> day.equals(initial.date()) ? sameDay : List.of());
    }

    /** Like {@link #open(Window, Use, ScheduleDraft, List)}, with the schedules of any date the user moves to. */
    static Result open(Window owner, Use use, ScheduleDraft initial, Function<LocalDate, List<Occurrence>> sameDay) {
        ScheduleFormDialog dialog = new ScheduleFormDialog(owner, use, initial, sameDay);
        dialog.setVisible(true);
        return dialog.result;
    }

    /** Builds the form without showing it, which is how the figures of the report are drawn. */
    ScheduleFormDialog(Window owner, Use use, ScheduleDraft initial, Function<LocalDate, List<Occurrence>> sameDay) {
        super(owner, heading(use), ModalityType.APPLICATION_MODAL);
        this.use = use;
        this.initial = initial;
        this.sameDay = sameDay;
        this.workBlock = initial.plannedFor() != null;
        this.fixedKind = workBlock || use == Use.PREVIEW_CLASS;
        this.repeatable = !workBlock && (use == Use.NEW || use == Use.EDIT || use == Use.EDIT_ALL
                || use == Use.PREVIEW_ROW);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(12, 14, 4, 14));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        int row = 0;

        // R-FORM-4: a title longer than 200 characters is not typed in.
        ((AbstractDocument) title.getDocument()).setDocumentFilter(new TitleLimit());
        title.setText(initial.title());
        title.setEditable(!workBlock);
        addRow(form, c, row++, "Title", title);
        if (workBlock) {
            Occurrence target = initial.plannedFor().deadline().occurrences(initial.plannedFor().date(),
                    initial.plannedFor().date()).get(0);
            JLabel plannedFor = new JLabel("<html>" + escape(target.title()) + " <font color='#5F6368'>due "
                    + target.date() + " " + target.timeText() + "</font></html>");
            addRow(form, c, row++, "Planned for", plannedFor);
        }

        JPanel kindPanel = flow();
        ButtonGroup group = new ButtonGroup();
        for (Kind kind : Kind.values()) {
            JRadioButton button = new JRadioButton(kind.toString(), kind == initial.kind());
            button.setEnabled(!fixedKind);
            button.addActionListener(e -> kindChanged());
            group.add(button);
            kindPanel.add(button);
            kinds.put(kind, button);
        }
        addRow(form, c, row++, "Type", kindPanel);

        date = ListPanel.dateSpinner(initial.date());
        addRow(form, c, row++, "Date", date);

        // R-FORM-2: the time row follows the type. An all-day schedule has none.
        boolean timed = initial.kind() == Kind.TIMED;
        LocalTime startValue = timed ? initial.start() : LocalTime.of(9, 0);
        LocalTime endValue = timed ? initial.end() : LocalTime.of(10, 0);
        // The start stops at 23:58 so that an end after it always exists.
        start = timeSpinner(startValue.isAfter(LocalTime.of(23, 58)) ? LocalTime.of(23, 58) : startValue, LocalTime.of(23, 58));
        end = timeSpinner(endValue, LocalTime.of(23, 59));
        timePanel.add(start);
        timePanel.add(new JLabel("to"));
        timePanel.add(end);
        timeRow = addRow(form, c, row++, timeLabel, timePanel);
        due = timeSpinner(initial.kind() == Kind.DEADLINE ? initial.start() : LocalTime.of(23, 59), LocalTime.of(23, 59));
        // R-FORM-9: the work estimate follows the category until the user types one.
        workTouched = initial.kind() == Kind.DEADLINE && initial.workMinutes() >= 0;
        work = new JSpinner(new SpinnerNumberModel(hoursFor(initial, initial.category()), 0.0, 40.0, 0.5));
        duePanel.add(due);
        duePanel.add(new JLabel("   Work needed"));
        duePanel.add(work);
        duePanel.add(new JLabel("hours"));
        dueRow = addRow(form, c, row++, dueLabel, duePanel);

        category.setSelectedItem(initial.category());
        category.setEnabled(!fixedKind);
        addRow(form, c, row++, "Category", category);

        // R-FORM-5: the whole series has no single date, so the list is left out when all of it is edited.
        onThisDay.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xDADCE0)),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        sameDayRow = addRow(form, c, row++, "On this day", onThisDay);

        // Opens by itself when one of the options is already in use, so nothing set is hidden (R-FORM-6).
        expanded = initial.priority() != Priority.MEDIUM || (repeatable && initial.repeat() != Repeat.NONE)
                || !initial.memo().isEmpty();
        more.setBorderPainted(false);
        more.setContentAreaFilled(false);
        more.setForeground(new Color(0x1A73E8));
        more.addActionListener(e -> {
            expanded = !expanded;
            optionsChanged();
            pack();
        });
        c.gridx = 1;
        c.gridy = row++;
        c.anchor = GridBagConstraints.WEST;
        form.add(more, c);

        priority.setSelectedItem(initial.priority());
        priority.setEnabled(use != Use.PREVIEW_CLASS);
        priorityRow = addRow(form, c, row++, "Priority", priority);
        repeat.setSelectedItem(initial.repeat());
        repeat.setEnabled(use != Use.PREVIEW_CLASS);
        until = ListPanel.dateSpinner(initial.repeatUntil() == null ? initial.date().plusMonths(3)
                : initial.repeatUntil());
        until.setEnabled(initial.repeat() != Repeat.NONE);
        JPanel repeatPanel = flow();
        repeatPanel.add(repeat);
        if (use != Use.PREVIEW_CLASS) {
            repeatPanel.add(new JLabel("until"));
            repeatPanel.add(until);
        }
        repeatRow = addRow(form, c, row++, "Repeat", repeatPanel);
        memo.setText(initial.memo());
        memo.setLineWrap(true);
        memo.setWrapStyleWord(true);
        memoRow = addRow(form, c, row++, "Memo", new JScrollPane(memo));

        add(form, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        // R-FORM-8: only a schedule that exists can be deleted.
        if (deletable(use)) {
            JPanel danger = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
            JButton delete = new JButton("Delete");
            delete.addActionListener(e -> finish(new DeleteRequested()));
            danger.add(delete);
            bottom.add(danger, BorderLayout.WEST);
        }
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> finish(new Cancelled()));
        buttons.add(cancel);
        save.addActionListener(e -> {
            ScheduleDraft values = collect();
            if (values.problem().isEmpty()) {
                finish(new Saved(values.normalized()));
            }
        });
        buttons.add(save);
        getRootPane().setDefaultButton(save);
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("ESCAPE"), "dayflow.cancel");
        getRootPane().getActionMap().put("dayflow.cancel", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                finish(new Cancelled());
            }
        });
        bottom.add(buttons, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);

        wire();
        lastStart = timeOf(start);
        kindChanged();
        optionsChanged();
        pack();
        setLocationRelativeTo(owner);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                title.requestFocusInWindow();
            }
        });
    }

    private void wire() {
        title.getDocument().addDocumentListener(new SimpleListener(this::refresh));
        memo.getDocument().addDocumentListener(new SimpleListener(this::refresh));
        date.addChangeListener(e -> {
            keepUntilAfterDate();
            refresh();
        });
        // R-FORM-3: the end moves with the start and keeps the length.
        start.addChangeListener(e -> {
            if (adjusting) {
                return;
            }
            LocalTime newStart = timeOf(start);
            int length = Math.max(timeOf(end).toSecondOfDay() / 60 - lastStart.toSecondOfDay() / 60, 0);
            int endMinute = Math.min(newStart.toSecondOfDay() / 60 + length, 23 * 60 + 59);
            adjusting = true;
            end.setValue(atTime(LocalTime.of(endMinute / 60, endMinute % 60)));
            adjusting = false;
            lastStart = newStart;
            refresh();
        });
        end.addChangeListener(e -> {
            // R-FORM-3: the end cannot be the start or earlier, so such a value is pulled to one minute after.
            LocalTime from = timeOf(start);
            if (!adjusting && !timeOf(end).isAfter(from)) {
                adjusting = true;
                end.setValue(atTime(from.plusMinutes(1)));
                adjusting = false;
            }
            refresh();
        });
        due.addChangeListener(e -> refresh());
        work.addChangeListener(e -> {
            if (!adjusting) {
                workTouched = true;
            }
        });
        category.addActionListener(e -> {
            if (!workTouched && category.getSelectedItem() instanceof Category chosen) {
                adjusting = true;
                work.setValue(chosen.defaultWorkMinutes() / 60.0);
                adjusting = false;
            }
        });
        // R-FORM-7: a repeat rule turns the end date on, three months after the date of the form.
        repeat.addActionListener(e -> {
            boolean repeats = repeat.getSelectedItem() != Repeat.NONE;
            if (repeats && !until.isEnabled()) {
                until.setValue(atStartOfDay(ListPanel.dateOf(date).plusMonths(3)));
            }
            until.setEnabled(repeats);
            refresh();
        });
        until.addChangeListener(e -> {
            keepUntilAfterDate();
            refresh();
        });
        priority.addActionListener(e -> refresh());
    }

    /** R-FORM-7: an end date before the date is set to the date. */
    private void keepUntilAfterDate() {
        if (ListPanel.dateOf(until).isBefore(ListPanel.dateOf(date))) {
            until.setValue(atStartOfDay(ListPanel.dateOf(date)));
        }
    }

    private Kind kind() {
        for (Map.Entry<Kind, JRadioButton> entry : kinds.entrySet()) {
            if (entry.getValue().isSelected()) {
                return entry.getKey();
            }
        }
        return initial.kind();
    }

    /** R-FORM-2: shows the time row of the chosen type. */
    private void kindChanged() {
        Kind kind = kind();
        timeRow.show(kind == Kind.TIMED);
        dueRow.show(kind == Kind.DEADLINE);
        refresh();
        pack();
    }

    /** Whether the repeat row exists in this form. A timetable class shows it fixed. */
    private boolean showsRepeat() {
        return repeatable || use == Use.PREVIEW_CLASS;
    }

    /** R-FORM-6: shows or hides the rows of the folding area and words the link that folds them. */
    private void optionsChanged() {
        priorityRow.show(expanded);
        repeatRow.show(expanded && showsRepeat());
        memoRow.show(expanded);
        more.setText(expanded ? "Fewer options"
                : showsRepeat() ? "More options (priority, repeat, memo)" : "More options (priority, memo)");
    }

    /** The values the form holds now, whether or not they can be saved. */
    private ScheduleDraft collect() {
        Kind kind = kind();
        LocalTime from = switch (kind) {
            case TIMED -> timeOf(start);
            case DEADLINE -> timeOf(due);
            case ALL_DAY -> null;
        };
        LocalTime to = kind == Kind.TIMED ? timeOf(end) : null;
        // A timetable class keeps its Weekly repeat, which the form does not show.
        boolean kept = use == Use.PREVIEW_CLASS;
        Repeat rule = repeatable ? (Repeat) repeat.getSelectedItem() : kept ? initial.repeat() : Repeat.NONE;
        LocalDate date = ListPanel.dateOf(this.date);
        LocalDate ends = kept ? max(initial.repeatUntil(), date) : ListPanel.dateOf(until);
        Category chosen = (Category) category.getSelectedItem();
        int minutes = kind == Kind.DEADLINE && workTouched ? (int) Math.round(((Number) work.getValue()).doubleValue() * 60)
                : -1;
        return new ScheduleDraft(title.getText(), kind, date, from, to, chosen,
                (Priority) priority.getSelectedItem(), memo.getText(), rule,
                rule == Repeat.NONE ? null : ends, minutes, initial.plannedFor());
    }

    /**
     * Updates the overlap list and whether Save can be pressed. The spec words no message for an invalid form,
     * because the form cannot reach one: the end follows the start and stays after it (R-FORM-3), and the
     * end date never falls before the date (R-FORM-7). Only an empty title disables Save (R-FORM-4).
     */
    private void refresh() {
        if (save == null || sameDayRow == null) {
            return;
        }
        ScheduleDraft values = collect();
        save.setEnabled(values.problem().isEmpty());

        List<Occurrence> others = use == Use.EDIT_ALL ? List.of() : sameDay.apply(values.date()).stream()
                .filter(Occurrence::timed).sorted(Comparator.comparing(Occurrence::sortTime)).toList();
        sameDayRow.show(!others.isEmpty());
        StringBuilder text = new StringBuilder("<html>");
        for (Occurrence o : others) {
            boolean overlaps = overlaps(o, values);
            text.append(overlaps ? "<font color='#D93025'><b>" : "<font color='#5F6368'>")
                    .append(o.timeText()).append("&nbsp; ").append(escape(o.title()))
                    .append(overlaps ? " (overlaps)</b>" : "").append("</font><br>");
        }
        onThisDay.setText(text.append("</html>").toString());
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void finish(Result decided) {
        result = decided;
        dispose();
    }

    private static boolean overlaps(Occurrence other, ScheduleDraft values) {
        return values.kind() == Kind.TIMED && values.start() != null && values.end() != null
                && other.start().isBefore(values.end()) && values.start().isBefore(other.end());
    }

    /** The hours shown in Work needed: the user's own estimate, or the default of the category. */
    private static double hoursFor(ScheduleDraft values, Category category) {
        int minutes = values.workMinutes() >= 0 ? values.workMinutes() : category.defaultWorkMinutes();
        return minutes / 60.0;
    }

    private Row addRow(JPanel form, GridBagConstraints c, int row, String label, JComponent field) {
        return addRow(form, c, row, new JLabel(label), field);
    }

    private Row addRow(JPanel form, GridBagConstraints c, int row, JLabel label, JComponent field) {
        c.gridx = 0;
        c.gridy = row;
        c.anchor = GridBagConstraints.NORTHWEST;
        form.add(label, c);
        c.gridx = 1;
        c.anchor = GridBagConstraints.WEST;
        form.add(field, c);
        return new Row(label, field);
    }

    private static JPanel flow() {
        return new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    }

    private static Date atTime(LocalTime time) {
        return Date.from(TIME_DAY.atTime(time).atZone(ZoneId.systemDefault()).toInstant());
    }

    private static Date atStartOfDay(LocalDate day) {
        return Date.from(day.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private static LocalTime timeOf(JSpinner spinner) {
        return ((Date) spinner.getValue()).toInstant().atZone(ZoneId.systemDefault()).toLocalTime().withSecond(0)
                .withNano(0);
    }

    /** A spinner of the time of day. It stops at 00:00 and 23:59, because nothing may pass midnight (R-FORM-3). */
    private static JSpinner timeSpinner(LocalTime time, LocalTime latest) {
        JSpinner spinner = new JSpinner(new SpinnerDateModel(atTime(time), atTime(LocalTime.MIN),
                atTime(latest), java.util.Calendar.MINUTE));
        spinner.setEditor(new JSpinner.DateEditor(spinner, "HH:mm"));
        return spinner;
    }

    /** Cuts a title that would grow past {@link ScheduleDraft#MAX_TITLE}, as typed or pasted (R-FORM-4). */
    private static final class TitleLimit extends DocumentFilter {
        @Override
        public void insertString(FilterBypass fb, int offset, String text, AttributeSet attrs)
                throws BadLocationException {
            replace(fb, offset, 0, text, attrs);
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                throws BadLocationException {
            int room = ScheduleDraft.MAX_TITLE - (fb.getDocument().getLength() - length);
            if (text != null && room < text.length()) {
                text = text.substring(0, Math.max(room, 0));
            }
            super.replace(fb, offset, length, text, attrs);
        }
    }

    /** One callback for every kind of document change. */
    private static final class SimpleListener implements javax.swing.event.DocumentListener {
        private final Runnable action;

        SimpleListener(Runnable action) {
            this.action = action;
        }

        @Override
        public void insertUpdate(javax.swing.event.DocumentEvent e) {
            action.run();
        }

        @Override
        public void removeUpdate(javax.swing.event.DocumentEvent e) {
            action.run();
        }

        @Override
        public void changedUpdate(javax.swing.event.DocumentEvent e) {
            action.run();
        }
    }
}
