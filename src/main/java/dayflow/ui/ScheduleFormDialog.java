package dayflow.ui;

import dayflow.model.Category;
import dayflow.model.Kind;
import dayflow.model.Occurrence;
import dayflow.model.Priority;
import dayflow.model.Repeat;
import dayflow.model.Schedule;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
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
import javax.swing.SpinnerDateModel;
import javax.swing.SpinnerNumberModel;

/** Form for adding and editing one schedule. The same dialog is used for both. */
final class ScheduleFormDialog extends JDialog {
    /**
     * @param date    the occurrence being edited, or the date of the new schedule
     * @param sameDay the other schedules on that date, shown so overlaps are visible before saving
     * @param oneOccurrence true when one date of a repeating schedule is edited; it then has no repeat row
     */
    ScheduleFormDialog(Frame owner, String heading, Schedule value, LocalDate date,
                       List<Occurrence> sameDay, boolean editing, boolean oneOccurrence) {
        super(owner, heading, true);
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(12, 14, 4, 14));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        int row = 0;

        // A work block follows its deadline: its title, type and category cannot be changed, and it does not repeat.
        boolean workBlock = value.plannedFor() != null;
        boolean repeatable = !workBlock && !oneOccurrence;
        JTextField title = new JTextField(value.title(), 30);
        title.setEditable(!workBlock);
        addRow(form, c, row++, "Title", title);
        if (workBlock) {
            Occurrence target = value.plannedFor().deadline().occurrences(value.plannedFor().date(),
                    value.plannedFor().date()).get(0);
            JLabel plannedFor = new JLabel("<html>" + target.title() + " <font color='#5F6368'>due "
                    + target.date() + " " + target.timeText() + "</font></html>");
            addRow(form, c, row++, "Planned for", plannedFor);
        }

        JPanel kinds = flow();
        ButtonGroup group = new ButtonGroup();
        for (Kind kind : Kind.values()) {
            JRadioButton button = new JRadioButton(kind.toString(), kind == value.kind());
            button.setEnabled(!workBlock);
            group.add(button);
            kinds.add(button);
        }
        addRow(form, c, row++, "Type", kinds);

        addRow(form, c, row++, "Date", ListPanel.dateSpinner(date));

        // The time row changes with the type; an all-day schedule has none.
        if (value.kind() == Kind.TIMED) {
            JPanel time = flow();
            time.add(timeSpinner(value.start()));
            time.add(new JLabel("to"));
            time.add(timeSpinner(value.end()));
            addRow(form, c, row++, "Time", time);
        } else if (value.kind() == Kind.DEADLINE) {
            JPanel due = flow();
            due.add(timeSpinner(value.start()));
            due.add(new JLabel("   Work needed"));
            double hours = value.effectiveWorkMinutes() / 60.0;
            due.add(new JSpinner(new SpinnerNumberModel(hours, 0.0, 40.0, 0.5)));
            due.add(new JLabel("hours"));
            addRow(form, c, row++, "Due", due);
        }

        JComboBox<Category> category = new JComboBox<>(Category.values());
        category.setSelectedItem(value.category());
        category.setEnabled(!workBlock);
        addRow(form, c, row++, "Category", category);

        if (!sameDay.isEmpty()) {
            StringBuilder text = new StringBuilder("<html>");
            sameDay.stream().filter(o -> o.timed() && o.schedule() != value)
                    .sorted(Comparator.comparing(Occurrence::sortTime))
                    .forEach(o -> text.append(overlaps(o, value) ? "<font color='#D93025'><b>" : "<font color='#5F6368'>")
                            .append(o.timeText()).append("&nbsp; ").append(o.title())
                            .append(overlaps(o, value) ? " (overlaps)</b>" : "").append("</font><br>"));
            JLabel onThisDay = new JLabel(text.append("</html>").toString());
            onThisDay.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(0xDADCE0)),
                    BorderFactory.createEmptyBorder(4, 8, 4, 8)));
            addRow(form, c, row++, "On this day", onThisDay);
        }

        // Opens by itself when one of the options is already in use, so nothing set is hidden.
        boolean showMore = value.priority() != Priority.MEDIUM || (repeatable && value.repeat() != Repeat.NONE)
                || !value.memo().isEmpty();
        JButton more = new JButton(showMore ? "Fewer options"
                : repeatable ? "More options (priority, repeat, memo)" : "More options (priority, memo)");
        more.setBorderPainted(false);
        more.setContentAreaFilled(false);
        more.setForeground(new Color(0x1A73E8));
        c.gridx = 1;
        c.gridy = row++;
        form.add(more, c);

        if (showMore) {
            JComboBox<Priority> priority = new JComboBox<>(Priority.values());
            priority.setSelectedItem(value.priority());
            addRow(form, c, row++, "Priority", priority);

            if (repeatable) {
                JPanel repeat = flow();
                JComboBox<Repeat> rule = new JComboBox<>(Repeat.values());
                rule.setSelectedItem(value.repeat());
                repeat.add(rule);
                repeat.add(new JLabel("until"));
                JSpinner until = ListPanel.dateSpinner(value.repeatUntil() == null ? date.plusMonths(3) : value.repeatUntil());
                until.setEnabled(value.repeat() != Repeat.NONE);
                repeat.add(until);
                addRow(form, c, row++, "Repeat", repeat);
            }

            JTextArea memo = new JTextArea(value.memo(), 3, 30);
            memo.setLineWrap(true);
            memo.setWrapStyleWord(true);
            addRow(form, c, row++, "Memo", new JScrollPane(memo));
        }
        add(form, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        if (editing) {
            // Delete is reachable from wherever a schedule can be opened.
            JPanel danger = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
            danger.add(new JButton("Delete"));
            bottom.add(danger, BorderLayout.WEST);
        }
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        buttons.add(new JButton("Cancel"));
        JButton save = new JButton("Save");
        buttons.add(save);
        getRootPane().setDefaultButton(save);
        bottom.add(buttons, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(owner);
    }

    private static boolean overlaps(Occurrence other, Schedule value) {
        return value.kind() == Kind.TIMED && other.schedule() != value
                && other.start().isBefore(value.end()) && value.start().isBefore(other.end());
    }

    private static void addRow(JPanel form, GridBagConstraints c, int row, String label, JComponent field) {
        c.gridx = 0;
        c.gridy = row;
        c.anchor = GridBagConstraints.NORTHWEST;
        form.add(new JLabel(label), c);
        c.gridx = 1;
        c.anchor = GridBagConstraints.WEST;
        form.add(field, c);
    }

    private static JPanel flow() {
        return new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    }

    private static JSpinner timeSpinner(LocalTime time) {
        Date value = Date.from(LocalDate.of(2026, 1, 1).atTime(time).atZone(ZoneId.systemDefault()).toInstant());
        JSpinner spinner = new JSpinner(new SpinnerDateModel(value, null, null, java.util.Calendar.MINUTE));
        spinner.setEditor(new JSpinner.DateEditor(spinner, "HH:mm"));
        return spinner;
    }
}
