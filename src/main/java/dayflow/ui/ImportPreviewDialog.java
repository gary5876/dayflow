package dayflow.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * Confirmation step shared by every import and by the planner: the proposals are listed with
 * checkboxes, and only the checked ones are stored when the user clicks the confirm button.
 */
final class ImportPreviewDialog extends JDialog {
    /**
     * One proposed schedule. {@code note} carries a duplicate, overlap, or shortage warning, or is empty.
     * A row that is not {@code checkable} only informs, for example a deadline with no free time.
     */
    record Candidate(boolean checked, boolean checkable, List<String> cells, String note) {
        Candidate(boolean checked, List<String> cells, String note) {
            this(checked, true, cells, note);
        }

        Candidate withChecked(boolean value) {
            return new Candidate(value, checkable, cells, note);
        }
    }

    /** Rows shown before the table starts to scroll, so the buttons never leave the screen. */
    private static final int VISIBLE_ROWS = 12;

    private final List<Candidate> rows;
    private final Set<Integer> invalid = new HashSet<>();
    private final JTable table;
    private final JButton confirm;
    private final String confirmLabel;
    private IntFunction<Optional<Candidate>> editHandler = row -> Optional.empty();
    private IntPredicate missingValue = row -> false;
    private boolean confirmed;

    /**
     * @param columns the column names after "Add" and before "Note"
     * @param options extra controls for the whole proposal, placed at the bottom left
     * @param editableColumns how many of the columns after "Add" the user may correct
     * @param withForm whether a row can be opened in the schedule form, since a plan is only corrected in the table
     */
    ImportPreviewDialog(Frame owner, String heading, String source, List<String> columns,
                        List<Candidate> candidates, JComponent options, String confirmLabel, int editableColumns,
                        boolean withForm) {
        super(owner, heading, true);
        this.rows = new ArrayList<>(candidates);
        this.confirmLabel = confirmLabel;

        JLabel sourceLabel = new JLabel(source);
        sourceLabel.setBorder(BorderFactory.createEmptyBorder(10, 12, 6, 12));
        add(sourceLabel, BorderLayout.NORTH);

        this.table = new JTable(new CandidateModel(columns, rows, editableColumns, this::checkedChanged));
        table.setRowHeight(24);
        table.setFillsViewportHeight(true);
        int last = columns.size() + 1;
        table.getColumnModel().getColumn(0).setMaxWidth(44);
        javax.swing.table.TableCellRenderer checkbox = table.getDefaultRenderer(Boolean.class);
        JLabel blank = new JLabel();
        // A row that cannot be added shows no checkbox.
        table.getColumnModel().getColumn(0).setCellRenderer((t, value, selected, focus, row, column) ->
                rows.get(row).checkable()
                        ? checkbox.getTableCellRendererComponent(t, value, selected, focus, row, column) : blank);
        for (int i = 1; i < last; i++) {
            int chars = columns.get(i - 1).length();
            for (Candidate c : candidates) {
                chars = Math.max(chars, c.cells().get(i - 1).length());
            }
            table.getColumnModel().getColumn(i).setPreferredWidth(Math.min(40 + chars * 8, 300));
        }
        int noteChars = candidates.stream().mapToInt(c -> c.note().length()).max().orElse(0);
        table.getColumnModel().getColumn(last).setPreferredWidth(Math.max(200, 30 + noteChars * 8));
        RowRenderer text = new RowRenderer(invalid, last);
        table.setDefaultRenderer(String.class, text);
        JScrollPane scroll = new JScrollPane(table);
        table.setPreferredScrollableViewportSize(new Dimension(936,
                table.getRowHeight() * Math.min(Math.max(candidates.size(), 4), VISIBLE_ROWS)));
        scroll.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(0, 12, 0, 12), scroll.getBorder()));
        add(scroll, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(options, BorderLayout.WEST);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        if (withForm) {
            JButton edit = new JButton("Edit selected...");
            edit.setEnabled(false);
            table.getSelectionModel().addListSelectionListener(e -> edit.setEnabled(table.getSelectedRow() >= 0));
            edit.addActionListener(e -> editSelected());
            buttons.add(edit);
        }
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dispose());
        buttons.add(cancel);
        this.confirm = new JButton();
        refreshConfirm();
        confirm.addActionListener(e -> confirm());
        buttons.add(confirm);
        getRootPane().setDefaultButton(confirm);
        bottom.add(buttons, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(owner);
    }

    /** True when the dialog was closed with the confirm button, so the checked rows are to be stored. */
    boolean confirmed() {
        return confirmed;
    }

    /** The rows as the user left them: the checks and the corrected cells. */
    List<Candidate> rows() {
        return List.copyOf(rows);
    }

    /** What Edit selected does: gives the new row for the row at the index, or nothing when the user cancels. */
    void onEdit(IntFunction<Optional<Candidate>> handler) {
        this.editHandler = handler;
    }

    /** R-IMP-11: tells whether a checked row lacks a value, so that confirm highlights it and stores nothing. */
    void onMissingValue(IntPredicate check) {
        this.missingValue = check;
    }

    /** Puts other rows in the table, for example after the period of a timetable changed. */
    void replaceRows(List<Candidate> with) {
        rows.clear();
        rows.addAll(with);
        invalid.clear();
        ((AbstractTableModel) table.getModel()).fireTableDataChanged();
        refreshConfirm();
    }

    private void editSelected() {
        int row = table.getSelectedRow();
        if (row < 0) {
            return;
        }
        editHandler.apply(row).ifPresent(changed -> {
            rows.set(row, changed);
            invalid.remove(row);
            ((AbstractTableModel) table.getModel()).fireTableRowsUpdated(row, row);
            refreshConfirm();
        });
    }

    /** R-IMP-11: with a value missing in a checked row nothing is stored, and the window stays with the rows marked. */
    private void confirm() {
        invalid.clear();
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).checked() && missingValue.test(i)) {
                invalid.add(i);
            }
        }
        if (!invalid.isEmpty()) {
            table.repaint();
            return;
        }
        confirmed = true;
        dispose();
    }

    private void checkedChanged() {
        invalid.clear();
        refreshConfirm();
        table.repaint();
    }

    /** R-IMP-12: the button counts the checked rows and is off without any. */
    private void refreshConfirm() {
        long checked = rows.stream().filter(Candidate::checked).count();
        confirm.setText(confirmLabel + " (" + checked + ")");
        confirm.setEnabled(checked > 0);
    }

    static JPanel options(Component... parts) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        for (Component part : parts) {
            panel.add(part);
        }
        return panel;
    }

    private static final class CandidateModel extends AbstractTableModel {
        private final List<String> columns;
        private final List<Candidate> rows;
        private final int editableColumns;
        private final Runnable checkChanged;

        CandidateModel(List<String> columns, List<Candidate> rows, int editableColumns, Runnable checkChanged) {
            this.columns = columns;
            this.rows = rows;
            this.editableColumns = editableColumns;
            this.checkChanged = checkChanged;
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            Candidate c = rows.get(row);
            if (column == 0) {
                rows.set(row, c.withChecked((Boolean) value));
                checkChanged.run();
            } else {
                List<String> cells = new ArrayList<>(c.cells());
                cells.set(column - 1, (String) value);
                rows.set(row, new Candidate(c.checked(), c.checkable(), cells, c.note()));
            }
            fireTableRowsUpdated(row, row);
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return columns.size() + 2;
        }

        @Override
        public String getColumnName(int column) {
            return column == 0 ? "Add" : column == columns.size() + 1 ? "Note" : columns.get(column - 1);
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return column == 0 ? Boolean.class : String.class;
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return column == 0 ? rows.get(row).checkable() : column <= editableColumns;
        }

        @Override
        public Object getValueAt(int row, int column) {
            Candidate c = rows.get(row);
            return column == 0 ? c.checked() : column == columns.size() + 1 ? c.note() : c.cells().get(column - 1);
        }
    }

    /** Draws the Note column in amber and the rows with a missing value on a light red background. */
    private static final class RowRenderer extends DefaultTableCellRenderer {
        private final Set<Integer> invalid;
        private final int noteColumn;

        RowRenderer(Set<Integer> invalid, int noteColumn) {
            this.invalid = invalid;
            this.noteColumn = noteColumn;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (!isSelected) {
                setForeground(column == noteColumn ? new Color(0xB06000) : table.getForeground());
                setBackground(invalid.contains(row) ? new Color(0xFCE8E6) : table.getBackground());
            }
            return this;
        }
    }
}
