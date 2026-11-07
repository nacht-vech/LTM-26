package client.view;

import client.model.ClientModel;
import client.service.RmiClientManager;
import shared.model.Record;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

/**
 * Giao diện chính của ứng dụng Client (Java Swing) chạy trên Máy ảo (VM).
 * Hiển thị danh sách bản ghi, trạng thái kết nối Server Active-Active, và các nút thao tác CRUD.
 */
public class MainFrame extends JFrame {
    private static final long serialVersionUID = 1L;

    private final ClientModel clientModel;
    private final JTable table;
    private final JLabel lblStatus = new JLabel("Đang khởi tạo kết nối...");
    private final JComboBox<RmiClientManager.ServerEndpoint> cboServerSelector = new JComboBox<>();

    private final JButton btnAdd = new JButton("Thêm mới");
    private final JButton btnEdit = new JButton("Sửa bản ghi");
    private final JButton btnDelete = new JButton("Xóa bản ghi");
    private final JButton btnRefresh = new JButton("Tải lại (Refresh)");
    private final JCheckBox chkAutoRefresh = new JCheckBox("Tự động làm mới (3s)", true);

    public MainFrame(ClientModel clientModel, List<RmiClientManager.ServerEndpoint> endpoints) {
        super("Java RMI Active-Active Data Sync Client (LWW)");
        this.clientModel = clientModel;
        this.table = new JTable(clientModel);

        for (RmiClientManager.ServerEndpoint ep : endpoints) {
            cboServerSelector.addItem(ep);
        }

        initUI();
    }

    private void initUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(920, 560);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(new EmptyBorder(12, 12, 12, 12));

        // 1. Thanh công cụ phía trên (Header & Chọn Server kết nối)
        JPanel topPanel = new JPanel(new BorderLayout(10, 5));
        JLabel lblHeader = new JLabel("HỆ THỐNG ĐỒNG BỘ DỮ LIỆU ACTIVE-ACTIVE QUA JAVA RMI");
        lblHeader.setFont(new Font("SansSerif", Font.BOLD, 16));
        topPanel.add(lblHeader, BorderLayout.WEST);

        JPanel serverSwitchPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        serverSwitchPanel.add(new JLabel("Server ưu tiên:"));
        serverSwitchPanel.add(cboServerSelector);
        serverSwitchPanel.add(chkAutoRefresh);
        topPanel.add(serverSwitchPanel, BorderLayout.EAST);

        root.add(topPanel, BorderLayout.NORTH);

        // 2. Bảng dữ liệu trung tâm
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(26);
        table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 13));
        table.getColumnModel().getColumn(0).setPreferredWidth(100);
        table.getColumnModel().getColumn(1).setPreferredWidth(180);
        table.getColumnModel().getColumn(2).setPreferredWidth(300);
        table.getColumnModel().getColumn(3).setPreferredWidth(210);
        table.getColumnModel().getColumn(4).setPreferredWidth(110);

        JScrollPane scrollPane = new JScrollPane(table);
        root.add(scrollPane, BorderLayout.CENTER);

        // 3. Thanh nút điều khiển và trạng thái phía dưới
        JPanel bottomPanel = new JPanel(new BorderLayout(10, 5));

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actionPanel.add(btnAdd);
        actionPanel.add(btnEdit);
        actionPanel.add(btnDelete);
        actionPanel.add(btnRefresh);
        bottomPanel.add(actionPanel, BorderLayout.WEST);

        lblStatus.setFont(new Font("SansSerif", Font.BOLD, 12));
        lblStatus.setForeground(new Color(0, 102, 51));
        bottomPanel.add(lblStatus, BorderLayout.EAST);

        root.add(bottomPanel, BorderLayout.SOUTH);

        setContentPane(root);
    }

    public Record getSelectedRecord() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow < 0) {
            return null;
        }
        int modelRow = table.convertRowIndexToModel(selectedRow);
        return clientModel.getRecordAt(modelRow);
    }

    public void setStatusText(String text, boolean isError) {
        lblStatus.setText(text);
        lblStatus.setForeground(isError ? new Color(180, 0, 0) : new Color(0, 102, 51));
    }

    public JButton getBtnAdd() {
        return btnAdd;
    }

    public JButton getBtnEdit() {
        return btnEdit;
    }

    public JButton getBtnDelete() {
        return btnDelete;
    }

    public JButton getBtnRefresh() {
        return btnRefresh;
    }

    public JCheckBox getChkAutoRefresh() {
        return chkAutoRefresh;
    }

    public JComboBox<RmiClientManager.ServerEndpoint> getCboServerSelector() {
        return cboServerSelector;
    }

    public JTable getTable() {
        return table;
    }
}
