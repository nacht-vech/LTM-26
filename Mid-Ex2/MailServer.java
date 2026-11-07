import java.awt.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.*;

public class MailServer {
    private static final int PORT = 8081;
    private static final String BIND_IP = "10.31.216.1"; 
    private static final Map<String, ObjectOutputStream> clientWriters = new ConcurrentHashMap<>();
    private static JTextArea logArea;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(MailServer::setupGUI);
        new Thread(MailServer::startServer).start();
    }

    private static void setupGUI() {
        JFrame frame = new JFrame("Mail Server Manager");
        frame.setSize(650, 450);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Nhật ký hoạt động của Server (Kèm thời gian thực)"));
        frame.add(scrollPane, BorderLayout.CENTER);

        frame.setVisible(true);
    }

    private static void log(String message) {
        String time = new SimpleDateFormat("HH:mm:ss dd/MM").format(new Date());
        String formattedMessage = "[" + time + "] " + message;
        
        SwingUtilities.invokeLater(() -> {
            if (logArea != null) {
                logArea.append(formattedMessage + "\n");
                logArea.setCaretPosition(logArea.getDocument().getLength());
            }
        });
    }

    private static void startServer() {
        try {
            ServerSocket serverSocket;
            if (BIND_IP.equals("0.0.0.0") || BIND_IP.isEmpty()) {
                serverSocket = new ServerSocket(PORT);
                log("Máy chủ khởi động, chấp nhận kết nối trên mọi IP (Port: " + PORT + ").");
            } else {
                serverSocket = new ServerSocket(PORT, 50, InetAddress.getByName(BIND_IP));
                log("Máy chủ khởi động tại IP: " + BIND_IP + " (Port: " + PORT + ").");
            }

            while (true) {
                Socket clientSocket = serverSocket.accept();
                log("=> [KẾT NỐI MỚI] Từ IP: " + clientSocket.getInetAddress());
                new ClientHandler(clientSocket).start();
            }
        } catch (IOException e) {
            log("[LỖI SERVER KHỞI ĐỘNG]: " + e.getMessage()); 
        }
    }

    private static class ClientHandler extends Thread {
        private final Socket socket;
        private ObjectInputStream in;
        private ObjectOutputStream out;
        private String clientEmail = "Unknown";

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                out = new ObjectOutputStream(socket.getOutputStream());
                in = new ObjectInputStream(socket.getInputStream());

                MailMessage initialMsg = (MailMessage) in.readObject();
                clientEmail = initialMsg.sender;
                clientWriters.put(clientEmail, out);
                log("[ĐĂNG NHẬP] Client [" + clientEmail + "] đã tham gia (JOIN).");

                while (true) {
                    MailMessage message = (MailMessage) in.readObject();
                    
                    // In thêm số lượng file đi kèm (nếu có) vào log cho dễ kiểm soát
                    String attachLog = message.hasAttachments() ? " (+ " + message.fileNames.size() + " tệp)" : "";
                    log("[GỬI THƯ] " + message.sender + " -> " + message.receiver + " (Chủ đề: '" + message.subject + "')" + attachLog);
                    
                    routeMail(message);
                }
            } catch (IOException | ClassNotFoundException e) { 
                log("[NGẮT KẾT NỐI] Client [" + clientEmail + "] đã thoát (OUT).");
            } finally {
                if (out != null) {
                    clientWriters.remove(clientEmail);
                }
                try {
                    if (!socket.isClosed()) socket.close();
                } catch (IOException e) {
                    log("[LỖI ĐÓNG KẾT NỐI]: " + e.getMessage());
                }
            }
        }

        private void routeMail(MailMessage message) {
            ObjectOutputStream receiverOut = clientWriters.get(message.receiver);
            if (receiverOut != null) {
                try {
                    receiverOut.writeObject(message);
                    receiverOut.flush();
                } catch (IOException e) {
                    log("[LỖI] Không thể gửi thư tới " + message.receiver + ": " + e.getMessage());
                }
            } else {
                log("[CẢNH BÁO] Người nhận " + message.receiver + " không online, thư bị hủy.");
            }
        }
    }
}