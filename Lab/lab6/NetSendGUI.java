import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import javax.swing.*;

public class NetSendGUI extends JFrame {
    private static final int PORT = 50000;
    private static final String MULTICAST_GROUP = "224.1.1.1";
    private static final String BROADCAST_IP = "255.255.255.255";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private JTextArea chatArea;
    private JTextField ipTextField;
    private JTextField messageTextField;
    private JRadioButton ipRadio;
    private JRadioButton groupRadio;
    private JRadioButton broadcastRadio;
    private DatagramSocket sendSocket;

    public NetSendGUI() {
        setTitle("Chương trình Net Send - Java GUI");
        setSize(600, 480);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        initUI();
        initNetwork();
        startReceiverThread();
    }

    private void initUI() {
        setLayout(new BorderLayout(10, 10));

        // 1. Panel phía trên: Chọn đối tượng nhận & Ô nhập IP
        JPanel topPanel = new JPanel(new GridLayout(2, 1, 5, 5));
        topPanel.setBorder(BorderFactory.createTitledBorder("Mục tiêu gửi"));

        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        ipRadio = new JRadioButton("IP cụ thể", true);
        groupRadio = new JRadioButton("Nhóm (Group)");
        broadcastRadio = new JRadioButton("Toàn mạng (*)");

        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(ipRadio);
        modeGroup.add(groupRadio);
        modeGroup.add(broadcastRadio);

        modePanel.add(ipRadio);
        modePanel.add(groupRadio);
        modePanel.add(broadcastRadio);

        JPanel ipPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        ipPanel.add(new JLabel("Địa chỉ IP đích:"));
        ipTextField = new JTextField("192.168.1.10", 18);
        ipPanel.add(ipTextField);

        ipRadio.addActionListener(e -> ipTextField.setEnabled(true));
        groupRadio.addActionListener(e -> ipTextField.setEnabled(false));
        broadcastRadio.addActionListener(e -> ipTextField.setEnabled(false));

        topPanel.add(modePanel);
        topPanel.add(ipPanel);

        // 2. Panel ở giữa: Hiển thị nhật ký tin nhắn
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        JScrollPane scrollPane = new JScrollPane(chatArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Nhật ký tin nhắn"));

        // 3. Panel phía dưới: Nhập tin nhắn và nút Gửi
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));

        messageTextField = new JTextField();
        JButton sendButton = new JButton("Gửi tin");

        bottomPanel.add(messageTextField, BorderLayout.CENTER);
        bottomPanel.add(sendButton, BorderLayout.EAST);

        sendButton.addActionListener(this::sendMessage);
        messageTextField.addActionListener(this::sendMessage);

        add(topPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void initNetwork() {
        try {
            sendSocket = new DatagramSocket();
            sendSocket.setBroadcast(true);
        } catch (SocketException e) {
            JOptionPane.showMessageDialog(this, "Lỗi khởi tạo Socket gửi: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String getTimeStamp() {
        return "[" + LocalTime.now().format(TIME_FORMATTER) + "]";
    }

    private void sendMessage(ActionEvent e) {
        String message = messageTextField.getText().trim();
        if (message.isEmpty()) return;

        String targetIp;
        String displayTarget;

        if (ipRadio.isSelected()) {
            targetIp = ipTextField.getText().trim();
            displayTarget = targetIp;
            if (targetIp.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Vui lòng nhập địa chỉ IP đích!", "Cảnh báo", JOptionPane.WARNING_MESSAGE);
                return;
            }
        } else if (groupRadio.isSelected()) {
            targetIp = MULTICAST_GROUP;
            displayTarget = "Nhóm Group (" + MULTICAST_GROUP + ")";
        } else {
            targetIp = BROADCAST_IP;
            displayTarget = "Toàn mạng (*)";
        }

        try {
            InetAddress address = InetAddress.getByName(targetIp);
            byte[] sendData = (message + "\n").getBytes(StandardCharsets.UTF_8);
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, address, PORT);

            sendSocket.send(sendPacket);
            appendChat(getTimeStamp() + " [Tôi -> " + displayTarget + "]: " + message);
            messageTextField.setText("");
        } catch (IOException ex) {
            appendChat(getTimeStamp() + " [Lỗi gửi]: " + ex.getMessage());
        }
    }

    private void startReceiverThread() {
        Thread receiverThread = new Thread(() -> {
            try (MulticastSocket socket = new MulticastSocket(PORT)) {
                InetAddress group = InetAddress.getByName(MULTICAST_GROUP);
                socket.joinGroup(group);

                byte[] buffer = new byte[1024];

                while (true) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    String message = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                    String cleanMessage = message.replaceAll("[\\r\\n]+$", "");
                    String senderIp = packet.getAddress().getHostAddress();

                    SwingUtilities.invokeLater(() -> 
                        appendChat(getTimeStamp() + " [Nhận từ " + senderIp + "]: " + cleanMessage)
                    );
                }
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> 
                    appendChat(getTimeStamp() + " [Lỗi lắng nghe]: " + e.getMessage())
                );
            }
        });
        receiverThread.setDaemon(true);
        receiverThread.start();
    }

    private void appendChat(String text) {
        chatArea.append(text + "\n");
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
            new NetSendGUI().setVisible(true);
        });
    }
}