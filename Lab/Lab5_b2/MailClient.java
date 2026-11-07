import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class MailClient extends JFrame {
    private JTextField userField, receiverField;
    private JTextArea contentArea, responseArea;
    private DefaultListModel<String> listModel;
    private JList<String> emailListUI;
    
    private static final int SERVER_PORT = 3456;
    private static final String SERVER_IP = "localhost";

    public MailClient() {
        setTitle("Mail Client (UDP)");
        setSize(550, 650);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        JPanel inputPanel = new JPanel(new GridLayout(2, 2, 5, 5));
        inputPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        inputPanel.add(new JLabel("Tài khoản của bạn:"));
        userField = new JTextField();
        inputPanel.add(userField);

        inputPanel.add(new JLabel("Người nhận (nếu gửi thư):"));
        receiverField = new JTextField();
        inputPanel.add(receiverField);
        
        add(inputPanel, BorderLayout.NORTH);

        JPanel centerPanel = new JPanel(new GridLayout(3, 1, 5, 5));
        
        contentArea = new JTextArea();
        contentArea.setLineWrap(true); 
        contentArea.setWrapStyleWord(true);
        contentArea.setBorder(BorderFactory.createTitledBorder("Soạn nội dung Email mới"));
        centerPanel.add(new JScrollPane(contentArea));

        listModel = new DefaultListModel<>();
        emailListUI = new JList<>(listModel);
        emailListUI.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane listScrollPane = new JScrollPane(emailListUI);
        listScrollPane.setBorder(BorderFactory.createTitledBorder("Danh sách hòm thư (Nhấp đúp vào tên file để đọc)"));
        centerPanel.add(listScrollPane);

        responseArea = new JTextArea();
        responseArea.setLineWrap(true);
        responseArea.setWrapStyleWord(true);
        responseArea.setEditable(false);
        responseArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        responseArea.setBorder(BorderFactory.createTitledBorder("Nội dung thư / Thông báo"));
        centerPanel.add(new JScrollPane(responseArea));

        add(centerPanel, BorderLayout.CENTER);

        // open mail
        emailListUI.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String selectedFile = emailListUI.getSelectedValue();
                    String user = userField.getText().trim();
                    if (selectedFile != null && !user.isEmpty()) {
                        sendCommand("READ|" + user + "|" + selectedFile);
                    }
                }
            }
        });

        // Nút chức năng
        JPanel btnPanel = new JPanel();
        JButton btnCreate = new JButton("Tạo tài khoản");
        JButton btnLogin = new JButton("Xem hòm thư");
        JButton btnSend = new JButton("Gửi Email");

        btnCreate.addActionListener(e -> sendCommand("CREATE|" + userField.getText().trim()));
        btnLogin.addActionListener(e -> sendCommand("LOGIN|" + userField.getText().trim()));
        btnSend.addActionListener(e -> {
            String sender = userField.getText().trim();
            String receiver = receiverField.getText().trim();
            String content = contentArea.getText().trim();
            if (sender.isEmpty() || receiver.isEmpty() || content.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Vui lòng nhập đủ Tài khoản gửi, Tài khoản nhận và Nội dung!");
                return;
            }
            sendCommand("SEND|" + sender + "|" + receiver + "|" + content);
            contentArea.setText("");
        });

        btnPanel.add(btnCreate);
        btnPanel.add(btnLogin);
        btnPanel.add(btnSend);
        add(btnPanel, BorderLayout.SOUTH);
    }

    private void sendCommand(String msg) {
        new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket()) {
                InetAddress serverAddress = InetAddress.getByName(SERVER_IP);
                byte[] sendData = msg.getBytes("UTF-8");
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, SERVER_PORT);
                socket.send(sendPacket);

                byte[] receiveData = new byte[8192];
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                socket.receive(receivePacket);

                String response = new String(receivePacket.getData(), 0, receivePacket.getLength(), "UTF-8");
                
                SwingUtilities.invokeLater(() -> {
                    String[] parts = response.split("\\|", 2);
                    if (parts.length < 2) return;
                    
                    if (parts[0].equals("LIST")) {
                        listModel.clear();
                        if (!parts[1].trim().isEmpty()) {
                            String[] files = parts[1].split(",");
                            for (String file : files) {
                                listModel.addElement(file);
                            }
                            responseArea.setText("Đã tải xong danh sách thư.");
                        } else {
                            responseArea.setText("Hòm thư của bạn trống.");
                        }
                    } else if (parts[0].equals("CONTENT")) {
                        responseArea.setText("--- Nội dung file ---\n" + parts[1]);
                        responseArea.setCaretPosition(0);
                    } else {
                        responseArea.setText("Thông báo: " + parts[1]);
                    }
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> responseArea.setText("Lỗi kết nối: " + e.getMessage()));
            }
        }).start();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MailClient().setVisible(true));
    }
}