package client.view;

import shared.model.Record;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Hộp thoại (Dialog) Thêm mới hoặc Chỉnh sửa bản ghi trên giao diện Java Swing.
 */
public class RecordDialog extends JDialog {
    private static final long serialVersionUID = 1L;

    private final JTextField txtId = new JTextField(25);
    private final JTextField txtTitle = new JTextField(25);
    private final JTextArea txtContent = new JTextArea(6, 25);
    private final JSpinner spnTimestampOffset = new JSpinner(new SpinnerNumberModel(0, -3600000, 3600000, 100));

    private boolean confirmed = false;
    private final Record existingRecord;

    public RecordDialog(Frame owner, Record existingRecord) {
        super(owner, existingRecord == null ? "Thêm Bản Ghi Mới" : "Chỉnh Sửa Bản Ghi", true);
        this.existingRecord = existingRecord;
        initComponents();
        populateData();
        pack();
        setLocationRelativeTo(owner);
    }

    private void initComponents() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Dòng 1: ID (Để trống nếu muốn tự sinh UUID, hoặc nhập tay để test xung đột LWW cùng ID)
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Mã bản ghi (ID):"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        txtId.setToolTipText("Để trống để tự động sinh mã, hoặc nhập mã cụ thể để test xung đột LWW");
        formPanel.add(txtId, gbc);

        // Dòng 2: Tiêu đề
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Tiêu đề (*):"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        formPanel.add(txtTitle, gbc);

        // Dòng 3: Nội dung
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        formPanel.add(new JLabel("Nội dung:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        txtContent.setLineWrap(true);
        txtContent.setWrapStyleWord(true);
        formPanel.add(new JScrollPane(txtContent), gbc);

        // Dòng 4: Độ lệch thời gian (tùy chọn hỗ trợ mô phỏng test LWW)
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 0;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        formPanel.add(new JLabel("Độ lệch Timestamp (ms):"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        spnTimestampOffset.setToolTipText("Mặc định = 0 (lấy giờ hệ thống hiện tại). Có thể chỉnh +/- ms để mô phỏng xung đột LWW.");
        formPanel.add(spnTimestampOffset, gbc);

        mainPanel.add(formPanel, BorderLayout.CENTER);

        // Nút Lưu / Hủy
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnSave = new JButton("Lưu bản ghi");
        JButton btnCancel = new JButton("Hủy");

        btnSave.addActionListener(e -> onSave());
        btnCancel.addActionListener(e -> dispose());

        buttonPanel.add(btnSave);
        buttonPanel.add(btnCancel);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);
        getRootPane().setDefaultButton(btnSave);
    }

    private void populateData() {
        if (existingRecord != null) {
            txtId.setText(existingRecord.getId());
            txtId.setEditable(false);
            txtTitle.setText(existingRecord.getTitle());
            txtContent.setText(existingRecord.getContent());
        }
    }

    private void onSave() {
        String title = txtTitle.getText().trim();
        if (title.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập tiêu đề bản ghi!", "Cảnh báo", JOptionPane.WARNING_MESSAGE);
            txtTitle.requestFocus();
            return;
        }
        confirmed = true;
        dispose();
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public Record BuildRecord() {
        String id = txtId.getText().trim();
        String title = txtTitle.getText().trim();
        String content = txtContent.getText().trim();
        int offsetMs = (Integer) spnTimestampOffset.getValue();
        long timestamp = System.currentTimeMillis() + offsetMs;

        Record rec = new Record();
        rec.setId(id.isEmpty() ? null : id);
        rec.setTitle(title);
        rec.setContent(content);
        rec.setTimestamp(timestamp);
        rec.setDeleted(false);
        return rec;
    }
}
