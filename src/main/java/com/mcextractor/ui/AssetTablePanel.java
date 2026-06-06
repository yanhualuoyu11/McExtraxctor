package com.mcextractor.ui;

import com.mcextractor.model.AssetEntry;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.util.List;

/**
 * Table displaying asset entries: icon, name, type, size, hash.
 */
public class AssetTablePanel extends JPanel {

    private final JTable table;
    private final AssetTableModel tableModel;
    private final JLabel statusLabel;

    public AssetTablePanel() {
        super(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("资源列表"));

        tableModel = new AssetTableModel();
        table = new JTable(tableModel);
        table.setAutoCreateRowSorter(true);
        table.setRowHeight(22);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.getSelectionModel().setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        // column widths
        table.getColumnModel().getColumn(0).setMaxWidth(30);  // icon
        table.getColumnModel().getColumn(1).setPreferredWidth(350); // name
        table.getColumnModel().getColumn(2).setPreferredWidth(60);  // type
        table.getColumnModel().getColumn(3).setPreferredWidth(80);  // size
        table.getColumnModel().getColumn(4).setPreferredWidth(280); // hash

        // center alignment for size and type
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        table.getColumnModel().getColumn(2).setCellRenderer(centerRenderer);
        table.getColumnModel().getColumn(3).setCellRenderer(centerRenderer);

        // monospace for hash
        table.getColumnModel().getColumn(4).setCellRenderer(new DefaultTableCellRenderer() {{
            setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        }});

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setPreferredSize(new Dimension(600, 250));
        add(scrollPane, BorderLayout.CENTER);

        statusLabel = new JLabel("0 个资源");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        add(statusLabel, BorderLayout.SOUTH);
    }

    public void setEntries(List<AssetEntry> entries) {
        tableModel.setEntries(entries);
        statusLabel.setText(entries.size() + " 个资源");
    }

    public JTable getTable() { return table; }

    public List<AssetEntry> getSelectedEntries() {
        int[] rows = table.getSelectedRows();
        java.util.List<AssetEntry> sel = new java.util.ArrayList<>();
        for (int viewRow : rows) {
            int modelRow = table.convertRowIndexToModel(viewRow);
            AssetEntry e = tableModel.getEntryAt(modelRow);
            if (e != null) sel.add(e);
        }
        return sel;
    }

    public AssetEntry getEntryAtRow(int viewRow) {
        if (viewRow < 0 || viewRow >= table.getRowCount()) return null;
        int modelRow = table.convertRowIndexToModel(viewRow);
        return tableModel.getEntryAt(modelRow);
    }

    public List<AssetEntry> getAllEntries() {
        return tableModel.getEntries();
    }

    // ---------- TableModel ----------

    private static class AssetTableModel extends AbstractTableModel {
        private final String[] cols = {"", "名称", "类型", "大小", "Hash"};
        private java.util.List<AssetEntry> entries = java.util.Collections.emptyList();

        void setEntries(List<AssetEntry> entries) {
            this.entries = entries;
            fireTableDataChanged();
        }

        List<AssetEntry> getEntries() { return entries; }

        AssetEntry getEntryAt(int row) {
            return (row >= 0 && row < entries.size()) ? entries.get(row) : null;
        }

        @Override public int getRowCount() { return entries.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int col) { return cols[col]; }

        @Override
        public Object getValueAt(int row, int col) {
            AssetEntry e = entries.get(row);
            return switch (col) {
                case 0 -> getIconName(e);
                case 1 -> e.getPath();
                case 2 -> e.getExtension().toUpperCase();
                case 3 -> e.getFormattedSize();
                case 4 -> e.getHash();
                default -> "";
            };
        }

        @Override
        public Class<?> getColumnClass(int col) {
            return String.class;
        }

        private String getIconName(AssetEntry e) {
            if (e.isImage()) return "\uD83D\uDDBC";  // 🖼
            if (e.isAudio()) return "\uD83D\uDD0A";  // 🔊
            if (e.isText())  return "\uD83D\uDCC4";  // 📄
            return "\uD83D\uDCE6";                    // 📦
        }
    }
}
