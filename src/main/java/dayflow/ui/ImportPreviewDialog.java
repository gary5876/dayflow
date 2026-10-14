package dayflow.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.util.List;
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
    }

    /** Rows shown before the table starts to scroll, so the buttons never leave the screen. */
    private static final int VISIBLE_ROWS = 12;

    /**
     * @param columns the column names after "Add" and before "Note"
     * @param options extra controls for the whole proposal, placed at the bottom left
     * @param editableColumns how many of the columns after "Add" the user may correct
     * @param withForm whether a row can be opened in the schedule form; a plan is only corrected in the table
     */
    ImportPreviewDialog(Frame owner, String heading, String source, List<String> columns,
                        List<Candidate> candidates, JComponent options, String confirmLabel, int editableColumns,
                        boolean withForm) {
        super(owner, heading, true);

        JLabel sourceLabel = new JLabel(source);
        sourceLabel.setBorder(BorderFactory.createEmptyBorder(10, 12, 6, 12));
        add(sourceLabel, BorderLayout.NORTH);

        JTable table = new JTable(new CandidateModel(columns, candidates, editableColumns));
        table.setRowHeight(24);
        table.setFillsViewportHeight(true);
        int last = columns.size() + 1;
        table.getColumnModel().getColumn(0).setMaxWidth(44);
        javax.swing.table.TableCellRenderer checkbox = table.getDefaultRenderer(Boolean.class);
        JLabel blank = new JLabel();
        // A row that cannot be added shows no checkbox.
        table.getColumnModel().getColumn(0).setCellRenderer((t, value, selected, focus, row, column) ->
                candidates.get(row).checkable()
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
        table.getColumnModel().getColumn(last).setCellRenderer(new NoteRenderer());
        JScrollPane scroll = new JScrollPane(table);
        table.setPreferredScrollableViewportSize(new Dimension(936,
                table.getRowHeight() * Math.min(Math.max(candidates.size(), 4), VISIBLE_ROWS)));
        scroll.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(0, 12, 0, 12), scroll.getBorder()));
        add(scroll, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(options, BorderLayout.WEST);
        long checked = candidates.stream().filter(Candidate::checked).count();
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        if (withForm) {
            JButton edit = new JButton("Edit selected...");
            edit.setEnabled(false);
            table.getSelectionModel().addListSelectionListener(e -> edit.setEnabled(table.getSelectedRow() >= 0));
            buttons.add(edit);
        }
        buttons.add(new JButton("Cancel"));
        JButton confirm = new JButton(confirmLabel + " (" + checked + ")");
        confirm.setEnabled(checked > 0);
        buttons.add(confirm);
        getRootPane().setDefaultButton(confirm);
        bottom.add(buttons, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(owner);
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

        CandidateModel(List<String> columns, List<Candidate> rows, int editableColumns) {
            this.columns = columns;
            this.rows = rows;
            this.editableColumns = editableColumns;
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

    private static final class NoteRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (!isSelected) {
                setForeground(new Color(0xB06000));
            }
            return this;
        }
    }
}
