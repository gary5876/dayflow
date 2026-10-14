package dayflow.ui;

import dayflow.model.Category;
import dayflow.model.Occurrence;
import dayflow.model.Priority;
import dayflow.model.Schedule;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerDateModel;
import javax.swing.SwingConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;

/** List view: every schedule in one sortable table with filters. */
final class ListPanel extends JPanel {
    private static final String[] COLUMNS = {"Done", "D-day", "Date", "Time", "Title", "Category", "Priority"};

    /** {@code category} null means all categories; {@code keyword} null means no search. */
    record Filters(LocalDate from, LocalDate to, Category category, boolean deadlinesOnly,
                   boolean openOnly, boolean hideRepeating, String keyword) {
        boolean accepts(Occurrence o, LocalDate today) {
            // Work blocks are never listed, not even in search results.
            if (o.planned()) {
                return false;
            }
            // A search looks at everything; the other filters would hide what the user is looking for.
            if (keyword != null) {
                return contains(o.title(), keyword) || contains(o.memo(), keyword);
            }
            // An open deadline that is already overdue always stays visible so it can be cleaned up.
            boolean overdueOpen = o.isOpenDeadline() && o.date().isBefore(today);
            return (overdueOpen || (!o.date().isBefore(from) && !o.date().isAfter(to)))
                    && (category == null || o.category() == category)
                    && (!deadlinesOnly || o.deadline())
                    && (!openOnly || !o.done())
                    && (!hideRepeating || !o.recurring() || o.deadline());
        }

        private static boolean contains(String text, String keyword) {
            return text.toLowerCase().contains(keyword.toLowerCase());
        }
    }

    private final List<Occurrence> rows;
    private final LocalDate today;

    /** @param selectedRow the table row drawn as selected, or -1 for none */
    ListPanel(List<Occurrence> all, LocalDate today, Filters f, int selectedRow) {
        super(new BorderLayout());
        this.today = today;
        List<Occurrence> matching = all.stream().filter(o -> f.accepts(o, today)).toList();
        this.rows = (f.keyword() == null ? matching : onePerSeries(matching, today)).stream()
                .sorted(Comparator.comparing(Occurrence::date).thenComparing(Occurrence::sortTime))
                .toList();

        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        filters.add(new JLabel("From"));
        filters.add(dateSpinner(f.from()));
        filters.add(new JLabel("To"));
        filters.add(dateSpinner(f.to()));
        JComboBox<Object> categories = new JComboBox<>(new Object[] {"All categories", Category.STUDY, Category.JOB,
                Category.CERTIFICATE, Category.HOBBY, Category.LIFE});
        categories.setSelectedItem(f.category() == null ? "All categories" : f.category());
        filters.add(categories);
        filters.add(new JCheckBox("Deadlines only", f.deadlinesOnly()));
        filters.add(new JCheckBox("Open only", f.openOnly()));
        filters.add(new JCheckBox("Hide repeating", f.hideRepeating()));
        if (f.keyword() != null) {
            for (Component c : filters.getComponents()) {
                c.setEnabled(false);
            }
        }
        add(filters, BorderLayout.NORTH);

        if (rows.isEmpty()) {
            JLabel none = new JLabel("No matching schedules", SwingConstants.CENTER);
            none.setForeground(new Color(0x80868B));
            add(none, BorderLayout.CENTER);
        } else {
            add(new JScrollPane(table(selectedRow)), BorderLayout.CENTER);
        }

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        footer.add(new JLabel(rows.size() + (rows.size() == 1 ? " schedule" : " schedules")));
        if (f.keyword() != null) {
            footer.add(new JLabel("<html>matching <b>\"" + f.keyword()
                    + "\"</b> in all dates. Filters are off while searching.</html>"));
        }
        // Reset sits here, not in the filter row, where it would be pushed out of a narrow window.
        footer.add(new JButton("Reset"));
        JButton edit = new JButton("Edit");
        JButton delete = new JButton("Delete");
        edit.setEnabled(selectedRow >= 0 && selectedRow < rows.size());
        delete.setEnabled(edit.isEnabled());
        footer.add(edit);
        footer.add(delete);
        footer.setBorder(BorderFactory.createEmptyBorder(0, 0, 2, 0));
        add(footer, BorderLayout.SOUTH);
    }

    /**
     * A search covers all dates, so a weekly class would fill the result with one row per week.
     * A series is shown once, by its next occurrence, or by its last one when it is over.
     * Overdue open occurrences of a repeating deadline keep their own rows, because each still needs the user.
     */
    private static List<Occurrence> onePerSeries(List<Occurrence> matching, LocalDate today) {
        List<Occurrence> result = new ArrayList<>();
        Map<Schedule, Occurrence> representative = new LinkedHashMap<>();
        for (Occurrence o : matching) {
            if (!o.recurring()) {
                result.add(o);
            } else if (o.isOpenDeadline() && o.date().isBefore(today)) {
                result.add(o);
            } else {
                Occurrence chosen = representative.get(o.schedule());
                // Occurrences arrive in date order: keep the first one from today on, else the latest.
                if (chosen == null || chosen.date().isBefore(today)) {
                    representative.put(o.schedule(), o);
                }
            }
        }
        result.addAll(representative.values());
        return result;
    }

    private JTable table(int selectedRow) {
        ListModel model = new ListModel();
        JTable table = new JTable(model);
        // D-day sorts by the days left and Time by the clock; as text, "D-10" would come before "D-2".
        TableRowSorter<ListModel> sorter = new TableRowSorter<>(model);
        sorter.setComparator(1, Comparator.comparingLong(ListPanel::daysLeft));
        sorter.setComparator(3, Comparator.comparing(ListPanel::clock));
        table.setRowSorter(sorter);
        // The priority is changed in place with one click; a double-click on this cell does not open the form.
        DefaultCellEditor priority = new DefaultCellEditor(new JComboBox<>(Priority.values()));
        priority.setClickCountToStart(1);
        table.getColumnModel().getColumn(6).setCellEditor(priority);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(24);
        table.setFillsViewportHeight(true);
        table.getColumnModel().getColumn(0).setMaxWidth(50);
        table.getColumnModel().getColumn(1).setMaxWidth(70);
        table.getColumnModel().getColumn(2).setMinWidth(100);
        table.getColumnModel().getColumn(2).setMaxWidth(110);
        table.getColumnModel().getColumn(3).setMinWidth(90);
        table.getColumnModel().getColumn(3).setMaxWidth(110);
        table.getColumnModel().getColumn(4).setPreferredWidth(320);
        TableCellRenderer checkbox = table.getDefaultRenderer(Boolean.class);
        JLabel blank = new JLabel();
        // Only a deadline can be done; other rows show no checkbox.
        table.getColumnModel().getColumn(0).setCellRenderer((t, value, selected, focus, row, column) ->
                value == null ? blank : checkbox.getTableCellRendererComponent(t, value, selected, focus, row, column));
        table.getColumnModel().getColumn(1).setCellRenderer(new DDayRenderer());
        for (int column : new int[] {2, 3, 4, 6}) {
            table.getColumnModel().getColumn(column).setCellRenderer(new TextRenderer());
        }
        table.getColumnModel().getColumn(5).setCellRenderer(new CategoryRenderer());
        if (selectedRow >= 0 && selectedRow < table.getRowCount()) {
            table.setRowSelectionInterval(selectedRow, selectedRow);
        }
        return table;
    }

    /** "D-day" is 0, "D-3" is 3, "D+2" is -2; rows without a deadline sort last. */
    private static long daysLeft(Object label) {
        String text = String.valueOf(label);
        if (text.isEmpty()) {
            return Long.MAX_VALUE;
        }
        return text.equals("D-day") ? 0 : text.startsWith("D-") ? Long.parseLong(text.substring(2))
                : -Long.parseLong(text.substring(2));
    }

    /** "~18:00" and "14:00-16:00" sort by their first time; "All day" comes first. */
    private static String clock(Object time) {
        String text = String.valueOf(time);
        return text.equals("All day") ? "" : text.startsWith("~") ? text.substring(1) : text;
    }

    static JSpinner dateSpinner(LocalDate date) {
        Date value = Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
        JSpinner spinner = new JSpinner(new SpinnerDateModel(value, null, null, java.util.Calendar.DAY_OF_MONTH));
        spinner.setEditor(new JSpinner.DateEditor(spinner, "yyyy-MM-dd"));
        return spinner;
    }

    private final class ListModel extends AbstractTableModel {
        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return column == 0 ? Boolean.class : column == 5 ? Category.class : column == 6 ? Priority.class
                    : String.class;
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return (column == 0 && rows.get(row).deadline()) || column == 6;
        }

        @Override
        public Object getValueAt(int row, int column) {
            Occurrence s = rows.get(row);
            return switch (column) {
                case 0 -> s.deadline() ? s.done() : null;
                case 1 -> s.deadline() ? s.dDayLabel(today) : "";
                case 2 -> s.date().toString();
                case 3 -> s.timeText();
                case 4 -> s.title();
                case 5 -> s.category();
                default -> s.priority();
            };
        }
    }

    private boolean doneAt(JTable table, int viewRow) {
        return rows.get(table.convertRowIndexToModel(viewRow)).done();
    }

    /** A done deadline is grey and struck through in every cell, and is never emphasised. */
    private class TextRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            boolean done = doneAt(table, row);
            setFont(table.getFont());
            setText(done && value != null && !value.toString().isEmpty()
                    ? "<html><strike>" + value + "</strike></html>" : value == null ? "" : value.toString());
            if (!isSelected) {
                setForeground(done ? new Color(0x9AA0A6) : normalColor(value));
            }
            return this;
        }

        Color normalColor(Object value) {
            return new Color(0x202124);
        }
    }

    /** Emphasises only D-day to D-2; overdue open deadlines are grey. */
    private final class DDayRenderer extends TextRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (urgent(String.valueOf(value)) && !doneAt(table, row)) {
                setFont(getFont().deriveFont(Font.BOLD));
            }
            return this;
        }

        @Override
        Color normalColor(Object value) {
            String label = String.valueOf(value);
            return urgent(label) ? new Color(0xD93025) : label.startsWith("D+") ? new Color(0x9AA0A6) : Color.DARK_GRAY;
        }

        private boolean urgent(String label) {
            return label.equals("D-day") || label.equals("D-1") || label.equals("D-2");
        }
    }

    private final class CategoryRenderer extends TextRenderer {
        @Override
        Color normalColor(Object value) {
            return value instanceof Category c ? c.color() : super.normalColor(value);
        }
    }
}
