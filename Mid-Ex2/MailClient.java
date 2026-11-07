import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.text.StyledDocument;

public class MailClient {
    private static final String SERVER_IP = "10.31.216.1"; 

    private JFrame frame;
    
    // UI
    private JList<MailMessage> inboxList;
    private DefaultListModel<MailMessage> inboxModel;
    private JTextPane mailDetailPane;
    private JTextField receiverField, subjectField;
    private JTextArea contentArea;
    private JButton btnSendMail, btnAttachFile;
    private JPanel attachmentPanel;
    private List<File> currentAttachments = new ArrayList<>();

    private ObjectOutputStream out;
    private ObjectInputStream in;
    private final String userEmail;
    private Socket socket;

    public MailClient() {
        String inputName = JOptionPane.showInputDialog(null, 
            "Nhập địa chỉ Email định danh của bạn:", 
            "Đăng nhập Mail", JOptionPane.PLAIN_MESSAGE);
            
        if (inputName == null || inputName.trim().isEmpty()) {
            System.exit(0);
        }
        this.userEmail = inputName.trim();

        setupGUI();
        connectToServer();
    }

    private void setupGUI() {
        frame = new JFrame("Hộp thư - " + userEmail + " (Đang kết nối: " + SERVER_IP + ")");
        frame.setSize(900, 650);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout(5, 5));

        // --- KHU VỰC ĐỌC THƯ ---
        inboxModel = new DefaultListModel<>();
        inboxList = new JList<>(inboxModel);
        inboxList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        
        inboxList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof MailMessage) {
                    MailMessage msg = (MailMessage) value;
                    String shortTime = msg.timestamp != null ? msg.timestamp.split(" ")[0] : "";
                    String prefix = msg.hasAttachments() ? "[📎] " : "";
                    setText(prefix + "[" + shortTime + "] Từ: " + msg.sender + " | CĐ: " + msg.subject);
                }
                return this;
            }
        });

        JScrollPane listScrollPane = new JScrollPane(inboxList);
        listScrollPane.setBorder(BorderFactory.createTitledBorder("Danh sách thư (Inbox)"));

        mailDetailPane = new JTextPane();
        mailDetailPane.setEditable(false);
        mailDetailPane.setBackground(new Color(245, 248, 250));
        JScrollPane detailScrollPane = new JScrollPane(mailDetailPane);
        detailScrollPane.setBorder(BorderFactory.createTitledBorder("Nội dung chi tiết"));

        inboxList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                MailMessage selected = inboxList.getSelectedValue();
                if (selected != null) {
                    displayMailContent(selected);
                }
            }
        });

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listScrollPane, detailScrollPane);
        splitPane.setDividerLocation(300);
        frame.add(splitPane, BorderLayout.CENTER);

        JPanel composePanel = new JPanel(new BorderLayout(5, 5));
        composePanel.setBorder(BorderFactory.createTitledBorder("Soạn thư mới"));
        
        JPanel headerPanel = new JPanel(new GridLayout(2, 1, 5, 5));
        
        JPanel toPanel = new JPanel(new BorderLayout(5, 5));
        toPanel.add(new JLabel("Đến (To): "), BorderLayout.WEST);
        receiverField = new JTextField();
        toPanel.add(receiverField, BorderLayout.CENTER);
        
        JPanel subjectPanel = new JPanel(new BorderLayout(5, 5));
        subjectPanel.add(new JLabel("Tiêu đề:  "), BorderLayout.WEST);
        subjectField = new JTextField();
        subjectPanel.add(subjectField, BorderLayout.CENTER);
        
        headerPanel.add(toPanel);
        headerPanel.add(subjectPanel);
        composePanel.add(headerPanel, BorderLayout.NORTH);
        contentArea = new JTextArea(4, 20);
        
        attachmentPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        JScrollPane attachmentScroll = new JScrollPane(attachmentPanel);
        attachmentScroll.setPreferredSize(new Dimension(0, 60)); 
        attachmentScroll.setBorder(BorderFactory.createTitledBorder("Tệp đính kèm"));
        JPanel centerComposePanel = new JPanel(new BorderLayout());
        centerComposePanel.add(new JScrollPane(contentArea), BorderLayout.CENTER);
        centerComposePanel.add(attachmentScroll, BorderLayout.SOUTH);
        composePanel.add(centerComposePanel, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        btnSendMail = new JButton("Gửi Email");
        btnSendMail.setBackground(new Color(0, 120, 215));
        btnSendMail.setForeground(Color.WHITE);
        btnAttachFile = new JButton("📁 Thêm file");
        btnAttachFile.setBackground(new Color(40, 167, 69));
        btnAttachFile.setForeground(Color.WHITE);
        btnPanel.add(btnAttachFile);
        btnPanel.add(btnSendMail);
        composePanel.add(btnPanel, BorderLayout.SOUTH);
        frame.add(composePanel, BorderLayout.SOUTH);
        btnSendMail.addActionListener(e -> sendMail());
        btnAttachFile.addActionListener(e -> openFileChooserAndAddFiles());

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent windowEvent) {
                closeConnection();
            }
        });

        frame.setVisible(true);
    }

    private void connectToServer() {
        try {
            socket = new Socket(SERVER_IP, 8081);
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());

            out.writeObject(new MailMessage(userEmail, "SERVER", "LOGIN", ""));
            out.flush();

            new Thread(() -> {
                try {
                    while (true) {
                        MailMessage msg = (MailMessage) in.readObject();
                        SwingUtilities.invokeLater(() -> inboxModel.addElement(msg));
                    }
                } catch (IOException | ClassNotFoundException e) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(frame, "Mất kết nối tới máy chủ Mail."));
                }
            }).start();

        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame, "Không thể kết nối tới Mail Server tại " + SERVER_IP + ":8081!\nVui lòng cấu hình lại SERVER_IP trong code.", "Lỗi kết nối", JOptionPane.ERROR_MESSAGE);
            System.exit(0);
        }
    }

    private void openFileChooserAndAddFiles() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setMultiSelectionEnabled(true);
        int result = fileChooser.showOpenDialog(frame);
        
        if (result == JFileChooser.APPROVE_OPTION) {
            File[] selectedFiles = fileChooser.getSelectedFiles();
            for (File f : selectedFiles) {
                if (!currentAttachments.contains(f)) {
                    currentAttachments.add(f);
                }
            }
            updateAttachmentUI();
        }
    }

    private void updateAttachmentUI() {
        attachmentPanel.removeAll();
        for (File f : currentAttachments) {
            JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
            p.setOpaque(false);
            p.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1, true));
            p.add(new JLabel(f.getName()));
            JButton btnRemove = new JButton("x");
            btnRemove.setMargin(new Insets(0, 2, 0, 2));
            btnRemove.setForeground(Color.RED);
            btnRemove.setFocusPainted(false);
            btnRemove.addActionListener(e -> {
                currentAttachments.remove(f);
                updateAttachmentUI();
            });
            
            p.add(btnRemove);
            attachmentPanel.add(p);
        }
        attachmentPanel.revalidate();
        attachmentPanel.repaint();
    }

    private void sendMail() {
        String receiver = receiverField.getText().trim();
        String subject = subjectField.getText().trim();
        String content = contentArea.getText().trim();

        if (receiver.isEmpty() || subject.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Vui lòng nhập người nhận và tiêu đề!");
            return;
        }

        try {
            MailMessage msg = new MailMessage(userEmail, receiver, subject, content);

            for (File f : currentAttachments) {
                byte[] fileData = Files.readAllBytes(f.toPath());
                msg.addAttachment(f.getName(), fileData);
            }
            
            out.writeObject(msg);
            out.flush();
            contentArea.setText("");
            subjectField.setText("");
            currentAttachments.clear();
            updateAttachmentUI();
            
            JOptionPane.showMessageDialog(frame, "Đã gửi thư đi thành công!");
        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame, "Lỗi khi đọc file hoặc gửi thư: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void displayMailContent(MailMessage msg) {
        mailDetailPane.setText(""); 
        StyledDocument doc = mailDetailPane.getStyledDocument();
        
        try {
            doc.insertString(doc.getLength(), "Từ: " + msg.sender + "\n", null);
            doc.insertString(doc.getLength(), "Thời gian: " + (msg.timestamp != null ? msg.timestamp : "Không rõ") + "\n", null);
            doc.insertString(doc.getLength(), "Tiêu đề: " + msg.subject + "\n", null);
            doc.insertString(doc.getLength(), "--------------------------------------------------\n\n", null);
            
            // In nội dung Text
            if (msg.textContent != null && !msg.textContent.isEmpty()) {
                doc.insertString(doc.getLength(), msg.textContent + "\n\n", null);
            }
            
            // Xử lý danh sách File
            if (msg.hasAttachments()) {
                doc.insertString(doc.getLength(), "--- Danh sách tệp đính kèm (" + msg.fileNames.size() + ") ---\n", null);
                
                File dir = new File("DownloadedFiles");
                if (!dir.exists()) dir.mkdir();
                
                for (int i = 0; i < msg.fileNames.size(); i++) {
                    String fName = msg.fileNames.get(i);
                    byte[] fData = msg.fileDatas.get(i);
                    
                    File savePath = new File(dir, fName);
                    Files.write(savePath.toPath(), fData);
                    
                    doc.insertString(doc.getLength(), "📎 " + fName + " (Lưu tại: " + savePath.getAbsolutePath() + ")\n", null);
                    
                    // Nếu là ảnh thì render trực tiếp xuống dưới
                    String lowerName = fName.toLowerCase();
                    if (lowerName.endsWith(".png") || lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || lowerName.endsWith(".gif")) {
                        ImageIcon imageIcon = new ImageIcon(savePath.getAbsolutePath());
                        Image img = imageIcon.getImage();
                        
                        int maxWidth = 450;
                        int width = img.getWidth(null);
                        int height = img.getHeight(null);
                        
                        if (width > maxWidth) {
                            int newHeight = (int) (((double) height / width) * maxWidth);
                            Image scaledImg = img.getScaledInstance(maxWidth, newHeight, Image.SCALE_SMOOTH);
                            imageIcon = new ImageIcon(scaledImg);
                        }
                        
                        mailDetailPane.setCaretPosition(doc.getLength());
                        mailDetailPane.insertIcon(imageIcon);
                        doc.insertString(doc.getLength(), "\n", null);
                    }
                    doc.insertString(doc.getLength(), "\n", null);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void closeConnection() {
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {}
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(MailClient::new);
    }
}