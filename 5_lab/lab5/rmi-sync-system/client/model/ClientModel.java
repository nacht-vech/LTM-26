package client.model;

import shared.model.Record;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/**
 * Tầng Model phía Client (MVC):
 * Quản lý danh sách bản ghi hiện hành và cung cấp TableModel cho JTable trên giao diện Swing.
 */
public class ClientModel extends AbstractTableModel {
    private static final long serialVersionUID = 1L;

    private final String[] columnNames = {
            "ID", "Tiêu đề", "Nội dung", "Cập nhật lúc (Timestamp)", "Server nguồn"
    };

    private final List<Record> records = new ArrayList<>();
    private String connectionStatus = "Chưa kết nối";

    public void setRecords(List<Record> newRecords) {
        records.clear();
        if (newRecords != null) {
            records.addAll(newRecords);
        }
        fireTableDataChanged();
    }

    public Record getRecordAt(int rowIndex) {
        if (rowIndex >= 0 && rowIndex < records.size()) {
            return records.get(rowIndex);
        }
        return null;
    }

    public List<Record> getRecords() {
        return new ArrayList<>(records);
    }

    public String getConnectionStatus() {
        return connectionStatus;
    }

    public void setConnectionStatus(String connectionStatus) {
        this.connectionStatus = connectionStatus;
    }

    @Override
    public int getRowCount() {
        return records.size();
    }

    @Override
    public int getColumnCount() {
        return columnNames.length;
    }

    @Override
    public String getColumnName(int column) {
        return columnNames[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Record rec = records.get(rowIndex);
        switch (columnIndex) {
            case 0:
                return rec.getId();
            case 1:
                return rec.getTitle();
            case 2:
                return rec.getContent();
            case 3:
                return rec.getFormattedTimestamp() + " (" + rec.getTimestamp() + ")";
            case 4:
                return rec.getSourceServerId();
            default:
                return "";
        }
    }
}
