import java.awt.BorderLayout;
import java.io.File;
import java.io.FileWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

public class MailServer extends JFrame {
    private JTextArea logArea;
    private static final int PORT = 3456;
    private static final String BASE_DIR = "email";
    private static Map<String, String[]> fileBuffers = new HashMap<>();

    public MailServer() {
        setTitle("Mail Server (UDP)");
        setSize(600, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        logArea = new JTextArea();
        logArea.setEditable(false);
        add(new JScrollPane(logArea), BorderLayout.CENTER);

        File baseFolder = new File(BASE_DIR);
        if (!baseFolder.exists()) {
            baseFolder.mkdir();
        }
        startServer();
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void startServer() {
        new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket(PORT)) {
                String serverIP = InetAddress.getLocalHost().getHostAddress();
                log("Mail Server đang chạy tại IP: " + serverIP + " - Cổng: " + PORT + "...");
                byte[] buffer = new byte[8192];

                while (true) {
                    DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length);
                    socket.receive(receivePacket);

                    String clientMsg = new String(receivePacket.getData(), 0, receivePacket.getLength(), "UTF-8");
                    String clientIP = receivePacket.getAddress().getHostAddress();
                    
                    String responseMsg = processCommand(clientMsg, clientIP, socket, receivePacket);

                    if (!responseMsg.equals("IGNORE")) {
                        byte[] sendData = responseMsg.getBytes("UTF-8");
                        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length,
                                receivePacket.getAddress(), receivePacket.getPort());
                        socket.send(sendPacket);
                    }
                }
            } catch (Exception e) {
                log("Lỗi Server: " + e.getMessage());
            }
        }).start();
    }

    private String processCommand(String msg, String clientIP, DatagramSocket socket, DatagramPacket receivePacket) {
        try {
            String command = msg.split("\\|", 2)[0];

            switch (command) {
                case "CREATE":
                    String[] createParts = msg.split("\\|", 3);
                    String newUser = createParts[1];
                    String newPass = (createParts.length == 3) ? createParts[2] : "";
                    
                    File userDir = new File(BASE_DIR + File.separator + newUser);
                    if (!userDir.exists()) {
                        userDir.mkdirs();
                        File passFile = new File(userDir, "pass.txt");
                        FileWriter pfw = new FileWriter(passFile);
                        pfw.write(newPass);
                        pfw.close();
                        
                        File welcomeFile = new File(userDir, "email_" + System.currentTimeMillis() + ".txt");
                        FileWriter fw = new FileWriter(welcomeFile);
                        fw.write("From: System\nSubject: Welcome to Mail Server\n\nThank you for using our service.");
                        fw.close();
                        
                        log("[" + clientIP + "] Đã tạo tài khoản: " + newUser);
                        return "SUCCESS_CREATE|Tạo tài khoản thành công! Vui lòng đăng nhập.";
                    }
                    return "ERROR|Tài khoản đã tồn tại!";

                case "LOGIN":
                    String[] loginParts = msg.split("\\|", 3);
                    String loginUser = loginParts[1];
                    String loginPass = (loginParts.length == 3) ? loginParts[2] : "";
                    
                    File loginDir = new File(BASE_DIR + File.separator + loginUser);
                    if (loginDir.exists()) {
                        File checkPassFile = new File(loginDir, "pass.txt");
                        if (checkPassFile.exists()) {
                            String savedPass = new String(Files.readAllBytes(checkPassFile.toPath()), "UTF-8").trim();
                            if (!savedPass.equals(loginPass)) {
                                return "ERROR|Sai mật khẩu!";
                            }
                        }
                        
                        String[] files = loginDir.list((dir, name) -> name.startsWith("email_"));
                        String fileList = (files != null && files.length > 0) ? String.join(",", files) : "";
                        log("[" + clientIP + "] Tài khoản " + loginUser + " đăng nhập thành công.");
                        return "LIST|" + fileList; 
                    }
                    return "ERROR|Tài khoản không tồn tại!";
                    
                case "LIST_SENT": 
                    String sentUser = msg.split("\\|", 2)[1];
                    File sentDir = new File(BASE_DIR + File.separator + sentUser);
                    if (sentDir.exists()) {
                        String[] files = sentDir.list((dir, name) -> name.startsWith("sent_"));
                        String fileList = (files != null && files.length > 0) ? String.join(",", files) : "";
                        return "LIST_SENT|" + fileList; 
                    }
                    return "ERROR|Tài khoản không tồn tại!";

                case "READ":
                    String[] readParts = msg.split("\\|", 3);
                    String readUser = readParts[1];
                    String fileName = readParts[2];
                    File emailFile = new File(BASE_DIR + File.separator + readUser + File.separator + fileName);
                    
                    if (emailFile.exists()) {
                        String fullContent = new String(Files.readAllBytes(emailFile.toPath()), "UTF-8");
                        int totalChunks = (int) Math.ceil((double) fullContent.length() / 4000);
                        for (int i = 0; i < totalChunks; i++) {
                            int end = Math.min((i + 1) * 4000, fullContent.length());
                            String chunk = fullContent.substring(i * 4000, end);
                            String responseMsg = "CONTENT_CHUNK|" + fileName + "|" + totalChunks + "|" + i + "|" + chunk;
                            
                            byte[] sendData = responseMsg.getBytes("UTF-8");
                            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, receivePacket.getAddress(), receivePacket.getPort());
                            socket.send(sendPacket);
                            Thread.sleep(10);
                        }
                        return "IGNORE"; 
                    }
                    return "ERROR|File không tồn tại!";

                case "SEND":
                    String[] sendParts = msg.split("\\|", 5); 
                    String sender = sendParts[1];
                    String receiver = sendParts[2];
                    String subject = (sendParts.length == 5) ? sendParts[3] : "(Không có chủ đề)";
                    String content = (sendParts.length == 5) ? sendParts[4] : sendParts[3];
                    
                    File receiverDir = new File(BASE_DIR + File.separator + receiver);
                    if (receiverDir.exists()) {
                        long timestamp = System.currentTimeMillis();
                        
                        File newEmailFile = new File(receiverDir, "email_" + timestamp + ".txt");
                        FileWriter fw = new FileWriter(newEmailFile);
                        fw.write("From: " + sender + "\nSubject: " + subject + "\n\n" + content);
                        fw.close();
                        
                        File senderDir = new File(BASE_DIR + File.separator + sender);
                        if (senderDir.exists()) {
                            File sentEmailFile = new File(senderDir, "sent_" + timestamp + ".txt");
                            FileWriter fw2 = new FileWriter(sentEmailFile);
                            fw2.write("To: " + receiver + "\nSubject: " + subject + "\n\n" + content);
                            fw2.close();
                        }
                        
                        log("[" + clientIP + "] Nhận email từ " + sender + " gửi đến " + receiver);
                        return "SUCCESS|Gửi email thành công!";
                    }
                    return "ERROR|Tài khoản người nhận không tồn tại!";

                case "FILE_CHUNK": 
                    String[] fcParts = msg.split("\\|", 6);
                    String fcSender = fcParts[1];
                    String fcReceiver = fcParts[2];
                    int totalC = Integer.parseInt(fcParts[3]);
                    int cIndex = Integer.parseInt(fcParts[4]);
                    String cData = (fcParts.length == 6) ? fcParts[5] : "";

                    String key = fcSender + "_" + fcReceiver + "_upload";
                    fileBuffers.putIfAbsent(key, new String[totalC]);
                    fileBuffers.get(key)[cIndex] = cData;

                    boolean isDone = true;
                    for (String c : fileBuffers.get(key)) {
                        if (c == null) { isDone = false; break; }
                    }

                    if (isDone) {
                        StringBuilder fullPayload = new StringBuilder();
                        for (String c : fileBuffers.get(key)) { fullPayload.append(c); }
                        fileBuffers.remove(key);
                        
                        String[] pParts = fullPayload.toString().split("\\|\\|\\|", 4);
                        String pSubject = pParts[0];
                        String pContent = pParts[1];
                        String pFileName = pParts[2];
                        String pBase64 = pParts.length == 4 ? pParts[3] : "";
                        
                        File rcvDir = new File(BASE_DIR + File.separator + fcReceiver);
                        if (!rcvDir.exists()) rcvDir.mkdirs();
                        
                        long ts = System.currentTimeMillis();
                        
                        File newF = new File(rcvDir, "email_" + ts + ".txt");
                        FileWriter fw = new FileWriter(newF);
                        fw.write("From: " + fcSender + "\nSubject: " + pSubject + "\nAttachment: " + pFileName + "\n\n" + pContent + "\n\n---ATTACHMENT---\n" + pBase64);
                        fw.close();
                        
                        File sdrDir = new File(BASE_DIR + File.separator + fcSender);
                        if (sdrDir.exists()) {
                            File sentF = new File(sdrDir, "sent_" + ts + ".txt");
                            FileWriter fw2 = new FileWriter(sentF);
                            fw2.write("To: " + fcReceiver + "\nSubject: " + pSubject + "\nAttachment: " + pFileName + "\n\n" + pContent + "\n\n---ATTACHMENT---\n" + pBase64);
                            fw2.close();
                        }
                        
                        log("[" + clientIP + "] Đã nhận email có đính kèm từ " + fcSender + " gửi đến " + fcReceiver);
                        return "SUCCESS|Gửi thư đính kèm thành công!";
                    }
                    return "IGNORE"; 

                default:
                    return "ERROR|Lệnh không hợp lệ!";
            }
        } catch (Exception e) {
            return "ERROR|Lỗi xử lý: " + e.getMessage();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MailServer().setVisible(true));
    }
}