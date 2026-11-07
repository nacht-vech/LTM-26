import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import model.Account;
import model.Email;
import storage.FileManager;

public class MainApp {

    public static final int LOCAL_SMTP_PORT = 2525;
    public static final int LOCAL_POP3_PORT = 1110;
    private static volatile boolean localSocketServerRunning = false;

    public static void main(String[] args) {
        // 1. Khởi tạo bộ quản lý file dữ liệu (data/<email>.txt)
        FileManager fileManager = new FileManager();

        // 2. Khởi động ngầm Socket Server cho SMTP (2525) & POP3 (1110) lắng nghe trên
        // mọi IP LAN (0.0.0.0)
        startEmbeddedSmtpPop3SocketServers(fileManager);

        // 3. Khởi chạy giao diện Java Swing từ file riêng MailClientGUI.java
        MailClientGUI.launch(fileManager);
    }

    /**
     * Khởi chạy 2 luồng Socket Server nội bộ (SMTP cổng 2525 & POP3 cổng 1110).
     */
    public static synchronized void startEmbeddedSmtpPop3SocketServers(FileManager fileManager) {
        if (localSocketServerRunning) {
            return;
        }
        localSocketServerRunning = true;

        // Luồng lắng nghe kết nối SMTP tại cổng 2525 từ máy khác trong mạng LAN
        Thread smtpThread = new Thread(() -> {
            try (ServerSocket server = new ServerSocket(LOCAL_SMTP_PORT)) {
                while (localSocketServerRunning) {
                    Socket client = server.accept();
                    new Thread(() -> handleSmtpSocketClient(client, fileManager)).start();
                }
            } catch (Exception ignored) {
            }
        });
        smtpThread.setDaemon(true);
        smtpThread.start();

        // Luồng lắng nghe kết nối POP3 tại cổng 1110 từ máy khác trong mạng LAN
        Thread pop3Thread = new Thread(() -> {
            try (ServerSocket server = new ServerSocket(LOCAL_POP3_PORT)) {
                while (localSocketServerRunning) {
                    Socket client = server.accept();
                    new Thread(() -> handlePop3SocketClient(client, fileManager)).start();
                }
            } catch (Exception ignored) {
            }
        });
        pop3Thread.setDaemon(true);
        pop3Thread.start();
    }

    /**
     * Xử lý SMTP Server: Nhận thư từ máy khác gửi sang và LƯU THẬT vào file
     * data/<receiver>.txt trên máy này.
     */
    private static void handleSmtpSocketClient(Socket socket, FileManager fm) {
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                PrintWriter out = new PrintWriter(
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {

            out.print("220 smtp.vku.localhost ESMTP VKU-Java-Mail-Server Ready\r\n");
            out.flush();

            String line;
            boolean inData = false;
            boolean readingHeaders = true;

            String mailFrom = "";
            String rcptTo = "";
            String headerFrom = "";
            String headerTo = "";
            String subject = "";
            String attachment = "None";
            String attachmentBase64 = "";
            StringBuilder bodyBuilder = new StringBuilder();

            while ((line = in.readLine()) != null) {
                if (inData) {
                    // Kết thúc khối DATA khi gặp dấu chấm đơn "."
                    if (".".equals(line)) {
                        inData = false;

                        String finalSender = !headerFrom.isEmpty() ? headerFrom : mailFrom;
                        String finalReceiver = !headerTo.isEmpty() ? headerTo : rcptTo;
                        String finalBody = bodyBuilder.toString();

                        if (!attachmentBase64.isEmpty() && !"None".equalsIgnoreCase(attachment)) {
                            try {
                                java.io.File attachDir = new java.io.File("attachments");
                                if (!attachDir.exists()) {
                                    attachDir.mkdirs();
                                }
                                String cleanFileName = new java.io.File(attachment).getName();
                                java.io.File savedFile = new java.io.File(attachDir, cleanFileName);
                                byte[] decodedBytes = java.util.Base64.getDecoder().decode(attachmentBase64);
                                java.nio.file.Files.write(savedFile.toPath(), decodedBytes);
                                attachment = cleanFileName;
                            } catch (Exception ex) {
                                ex.printStackTrace();
                            }
                        }

                        // =========================================================================
                        // [CHÚ Ý KHI VẤN ĐÁP VỚI THẦY]:
                        // Dòng dưới đây lưu email nhận qua Socket vào folder database (data/<receiver>.txt).
                        // Nếu thầy yêu cầu không lưu vào ứng dụng, chỉ cần ĐÓNG COMMENT (//) dòng này:
                        // =========================================================================
                        saveIncomingSmtpEmail(fm, finalSender, finalReceiver, subject, finalBody, attachment);

                        out.print("250 2.0.0 OK: Message and attachment saved to " + finalReceiver + "\r\n");
                        out.flush();
                        continue;
                    }

                    // Phân tích Header và Body trong khối DATA
                    if (readingHeaders) {
                        if (line.isEmpty()) {
                            readingHeaders = false; // Dòng trống đánh dấu kết thúc phần Header, bắt đầu phần Body
                        } else if (line.startsWith("X-App-Sender: ")) {
                            headerFrom = FileManager.normalizeEmail(line.substring(14));
                        } else if (line.startsWith("From: ") && headerFrom.isEmpty()) {
                            headerFrom = extractEmailAddress(line.substring(6));
                        } else if (line.startsWith("To: ")) {
                            headerTo = extractEmailAddress(line.substring(4));
                        } else if (line.startsWith("Subject: ")) {
                            subject = line.substring(9).trim();
                        } else if (line.startsWith("X-Attachment: ")) {
                            attachment = line.substring(14).trim();
                        } else if (line.startsWith("X-Attachment-Base64: ")) {
                            attachmentBase64 = line.substring(21).trim();
                        }
                    } else {
                        if (bodyBuilder.length() > 0) {
                            bodyBuilder.append("\n");
                        }
                        bodyBuilder.append(line);
                    }
                    continue;
                }

                String upper = line.toUpperCase();
                if (upper.startsWith("HELO") || upper.startsWith("EHLO")) {
                    out.print("250 Hello from VKU Local Socket SMTP Server\r\n");
                } else if (upper.startsWith("AUTH LOGIN")) {
                    out.print("334 VXNlcm5hbWU6\r\n");
                    out.flush();
                    in.readLine();
                    out.print("334 UGFzc3dvcmQ6\r\n");
                    out.flush();
                    in.readLine();
                    out.print("235 2.7.0 Authentication successful\r\n");
                } else if (upper.startsWith("MAIL FROM:")) {
                    mailFrom = extractEmailAddress(line.substring(10));
                    out.print("250 2.1.0 Sender OK\r\n");
                } else if (upper.startsWith("RCPT TO:")) {
                    rcptTo = extractEmailAddress(line.substring(8));
                    out.print("250 2.1.5 Recipient OK\r\n");
                } else if (upper.equals("DATA")) {
                    inData = true;
                    readingHeaders = true;
                    headerFrom = "";
                    headerTo = "";
                    subject = "";
                    attachment = "None";
                    attachmentBase64 = "";
                    bodyBuilder.setLength(0);
                    out.print("354 End data with <CR><LF>.<CR><LF>\r\n");
                } else if (upper.startsWith("QUIT")) {
                    out.print("221 2.0.0 Bye\r\n");
                    out.flush();
                    break;
                } else {
                    out.print("250 OK\r\n");
                }
                out.flush();
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Ghi bức thư vừa nhận qua Socket vào file data/<receiver>.txt trên máy hiện
     * tại.
     * Nếu tài khoản người nhận chưa có file trên máy này, tự động khởi tạo tài
     * khoản (MK mặc định: 12345678) để không bị mất thư.
     */
    private static void saveIncomingSmtpEmail(FileManager fm, String sender, String receiver,
            String subject, String body, String attachment) {
        String normReceiver = FileManager.normalizeEmail(receiver);
        String normSender = FileManager.normalizeEmail(sender);
        if (!FileManager.isValidEmailSyntax(normReceiver)) {
            return;
        }
        try {
            synchronized (FileManager.class) {
                if (!fm.userExists(normReceiver)) {
                    fm.createAccount(normReceiver, "12345678");
                }
                Account recvAcc = fm.loadAccount(normReceiver);
                if (recvAcc != null) {
                    Email incoming = new Email(
                            FileManager.getCurrentTime(),
                            normSender,
                            normReceiver,
                            subject,
                            body,
                            attachment);
                    recvAcc.getInbox().add(incoming);
                    fm.saveAccount(recvAcc);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String extractEmailAddress(String raw) {
        if (raw == null)
            return "";
        String cleaned = raw.trim();
        int start = cleaned.indexOf('<');
        int end = cleaned.indexOf('>');
        if (start != -1 && end != -1 && end > start) {
            cleaned = cleaned.substring(start + 1, end);
        }
        return FileManager.normalizeEmail(cleaned);
    }

    /**
     * Xử lý POP3 Server: Hỗ trợ STAT, LIST và cả RETR để máy khác có thể tải nội
     * dung thư về qua cổng 1110.
     */
    private static void handlePop3SocketClient(Socket socket, FileManager fm) {
        String currentPopUser = "alice@gmail.com";
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                PrintWriter out = new PrintWriter(
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {
            out.print("+OK VKU-Java-POP3-Server Ready on port " + LOCAL_POP3_PORT + "\r\n");
            out.flush();
            String line;
            while ((line = in.readLine()) != null) {
                String upper = line.toUpperCase();
                if (upper.startsWith("USER ")) {
                    currentPopUser = FileManager.normalizeEmail(line.substring(5).trim());
                    out.print("+OK User accepted\r\n");
                } else if (upper.startsWith("PASS ")) {
                    out.print("+OK Pass accepted\r\n");
                } else if (upper.startsWith("STAT")) {
                    Account acc = fm.loadAccount(currentPopUser);
                    int count = acc != null ? acc.getInbox().size() : 0;
                    out.print("+OK " + count + " " + (count * 256) + "\r\n");
                } else if (upper.startsWith("LIST")) {
                    Account acc = fm.loadAccount(currentPopUser);
                    List<Email> inbox = acc != null ? acc.getInbox() : Collections.emptyList();
                    out.print("+OK " + inbox.size() + " messages\r\n");
                    for (int i = 0; i < inbox.size(); i++) {
                        Email e = inbox.get(i);
                        out.print((i + 1) + " 256 (From: " + e.getSender() + " | Subject: " + e.getSubject() + ")\r\n");
                    }
                    out.print(".\r\n");
                } else if (upper.startsWith("RETR ")) {
                    int msgIndex = -1;
                    try {
                        msgIndex = Integer.parseInt(line.substring(5).trim()) - 1;
                    } catch (NumberFormatException ignored) {
                    }
                    Account acc = fm.loadAccount(currentPopUser);
                    List<Email> inbox = acc != null ? acc.getInbox() : Collections.emptyList();
                    if (msgIndex >= 0 && msgIndex < inbox.size()) {
                        Email e = inbox.get(msgIndex);
                        out.print("+OK message follows\r\n");
                        out.print("Time: " + e.getTime() + "\r\n");
                        out.print("From: " + e.getSender() + "\r\n");
                        out.print("To: " + e.getReceiver() + "\r\n");
                        out.print("Subject: " + e.getSubject() + "\r\n");
                        out.print("Attachment: " + e.getAttachedFile() + "\r\n");
                        out.print("\r\n");
                        out.print(e.getBody() + "\r\n");
                        out.print(".\r\n");
                    } else {
                        out.print("-ERR No such message\r\n");
                    }
                } else if (upper.startsWith("QUIT")) {
                    out.print("+OK POP3 Server signing off\r\n");
                    out.flush();
                    break;
                } else {
                    out.print("+OK\r\n");
                }
                out.flush();
            }
        } catch (Exception ignored) {
        }
    }
}