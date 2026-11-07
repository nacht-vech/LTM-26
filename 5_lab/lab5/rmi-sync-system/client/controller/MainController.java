package client.controller;

import client.model.ClientModel;
import client.service.RmiClientManager;
import client.view.MainFrame;
import client.view.RecordDialog;
import shared.model.Record;

import javax.swing.*;
import java.util.List;

/**
 * Tầng Controller phía Client (MVC):
 * - Bắt sự kiện từ `MainFrame`.
 * - Thực thi các lời gọi RMI thông qua `SwingWorker` để giữ giao diện mượt mà (không treo EDT).
 * - Tự động cập nhật nhãn trạng thái khi `RmiClientManager` thực hiện Failover sang Server dự phòng.
 */
public class MainController {
    private final ClientModel model;
    private final MainFrame view;
    private final RmiClientManager rmiManager;
    private final Timer autoRefreshTimer;
    private boolean suppressComboEvent = false;

    public MainController(ClientModel model, MainFrame view, RmiClientManager rmiManager) {
        this.model = model;
        this.view = view;
        this.rmiManager = rmiManager;

        bindEvents();

        // Tự động làm mới dữ liệu mỗi 3 giây nếu bật checkbox
        this.autoRefreshTimer = new Timer(3000, e -> {
            if (view.getChkAutoRefresh().isSelected()) {
                loadRecordsAsync(true);
            }
        });
        this.autoRefreshTimer.start();

        // Tải dữ liệu lần đầu
        loadRecordsAsync(false);
    }

    private void bindEvents() {
        view.getBtnRefresh().addActionListener(e -> loadRecordsAsync(false));
        view.getBtnAdd().addActionListener(e -> onAddRecord());
        view.getBtnEdit().addActionListener(e -> onEditRecord());
        view.getBtnDelete().addActionListener(e -> onDeleteRecord());

        view.getCboServerSelector().addActionListener(e -> {
            if (suppressComboEvent) {
                return;
            }
            int idx = view.getCboServerSelector().getSelectedIndex();
            if (idx >= 0) {
                rmiManager.selectServer(idx);
                loadRecordsAsync(false);
            }
        });
    }

    public void loadRecordsAsync(boolean silent) {
        new SwingWorker<List<Record>, Void>() {
            private String serverInfo;

            @Override
            protected List<Record> doInBackground() throws Exception {
                return rmiManager.executeWithFailover(service -> {
                    serverInfo = service.getServerInfo();
                    return service.getAllRecords();
                });
            }

            @Override
            protected void done() {
                syncComboSelectionWithManager();
                try {
                    List<Record> records = get();
                    String selectedId = view.getSelectedRecord() != null ? view.getSelectedRecord().getId() : null;
                    model.setRecords(records);
                    restoreSelection(selectedId);
                    view.setStatusText("Đang kết nối: " + serverInfo, false);
                } catch (Exception ex) {
                    view.setStatusText("Lỗi kết nối cả 2 Server!", true);
                    if (!silent) {
                        JOptionPane.showMessageDialog(view,
                                "Không thể tải dữ liệu từ cả 2 Server:\n" + ex.getMessage(),
                                "Lỗi Kết Nối RMI", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        }.execute();
    }

    private void onAddRecord() {
        RecordDialog dialog = new RecordDialog(view, null);
        dialog.setVisible(true);
        if (dialog.isConfirmed()) {
            Record newRecord = dialog.BuildRecord();
            saveRecordAsync(newRecord);
        }
    }

    private void onEditRecord() {
        Record selected = view.getSelectedRecord();
        if (selected == null) {
            JOptionPane.showMessageDialog(view, "Vui lòng chọn một bản ghi trên bảng để sửa!",
                    "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        RecordDialog dialog = new RecordDialog(view, selected);
        dialog.setVisible(true);
        if (dialog.isConfirmed()) {
            Record updatedRecord = dialog.BuildRecord();
            saveRecordAsync(updatedRecord);
        }
    }

    private void onDeleteRecord() {
        Record selected = view.getSelectedRecord();
        if (selected == null) {
            JOptionPane.showMessageDialog(view, "Vui lòng chọn một bản ghi trên bảng để xóa!",
                    "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(view,
                "Bạn có chắc chắn muốn xóa bản ghi '" + selected.getTitle() + "' (ID=" + selected.getId() + ")?",
                "Xác nhận xóa", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        long clientTimestamp = System.currentTimeMillis();
        new SwingWorker<Boolean, Void>() {
            private String serverInfo;

            @Override
            protected Boolean doInBackground() throws Exception {
                return rmiManager.executeWithFailover(service -> {
                    boolean ok = service.deleteRecord(selected.getId(), clientTimestamp);
                    serverInfo = service.getServerInfo();
                    return ok;
                });
            }

            @Override
            protected void done() {
                syncComboSelectionWithManager();
                try {
                    get();
                    view.setStatusText("Đã xóa bản ghi | " + serverInfo, false);
                    loadRecordsAsync(true);
                } catch (Exception ex) {
                    view.setStatusText("Lỗi khi xóa bản ghi!", true);
                    JOptionPane.showMessageDialog(view,
                            "Không thể xóa bản ghi:\n" + ex.getMessage(),
                            "Lỗi RMI", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void saveRecordAsync(Record record) {
        new SwingWorker<Record, Void>() {
            private String serverInfo;

            @Override
            protected Record doInBackground() throws Exception {
                return rmiManager.executeWithFailover(service -> {
                    Record saved = service.saveRecord(record);
                    serverInfo = service.getServerInfo();
                    return saved;
                });
            }

            @Override
            protected void done() {
                syncComboSelectionWithManager();
                try {
                    Record saved = get();
                    view.setStatusText("Đã lưu bản ghi ID=" + (saved != null ? saved.getId() : "") + " | " + serverInfo, false);
                    loadRecordsAsync(true);
                } catch (Exception ex) {
                    view.setStatusText("Lỗi khi lưu bản ghi!", true);
                    JOptionPane.showMessageDialog(view,
                            "Không thể lưu bản ghi:\n" + ex.getMessage(),
                            "Lỗi RMI", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void syncComboSelectionWithManager() {
        int currentIdx = rmiManager.getCurrentIndex();
        if (view.getCboServerSelector().getSelectedIndex() != currentIdx) {
            suppressComboEvent = true;
            view.getCboServerSelector().setSelectedIndex(currentIdx);
            suppressComboEvent = false;
        }
    }

    private void restoreSelection(String recordId) {
        if (recordId == null) {
            return;
        }
        for (int i = 0; i < model.getRowCount(); i++) {
            Record r = model.getRecordAt(i);
            if (r != null && recordId.equals(r.getId())) {
                int viewRow = view.getTable().convertRowIndexToView(i);
                view.getTable().setRowSelectionInterval(viewRow, viewRow);
                break;
            }
        }
    }
}
