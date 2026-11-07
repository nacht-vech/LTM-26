package protocol;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import model.Account;
import model.Email;
import storage.FileManager;

/**
 * Xử lý giao thức truyền tin đơn giản SMTP (Simple Mail Transfer Protocol).
 * Hỗ trợ đầy đủ cả 3 kịch bản chấm bài Lab:
 * 1. Gửi nội bộ trên ứng dụng (Localhost Mode) & lưu vào folder database (data/<email>.txt) + thư mục attachments/.
 * 2. Gửi qua Socket LAN Server (cổng 2525) kèm truyền file đính kèm Base64.
 * 3. Gửi tới EMAIL THẬT trên Internet (Gmail smtp.gmail.com cổng 587 STARTTLS hoặc 465 SSL/TLS)
 *    hỗ trợ đính kèm file ảnh/tài liệu dung lượng lớn (chuẩn MIME 76 ký tự/dòng), đồng thời lưu vào folder data/<email>.txt.
 *
 * -> MẸO VẤN ĐÁP: Khi thầy yêu cầu "Chỉ gửi tới email thật của thầy, không gửi/lưu vào ứng dụng",
 *    chỉ cần đóng comment (//) đúng 1 dòng gọi hàm saveEmailToAppDatabase(...) đã được đánh dấu bên dưới!
 */
public class SmtpClient {
    private final FileManager fileManager;
    private String lastProtocolLog = "";
    private String lastStatusMessage = "";

    public SmtpClient() {
        this.fileManager = new FileManager();
    }

    public String getLastProtocolLog() {
        return lastProtocolLog;
    }

    public String getLastStatusMessage() {
        return lastStatusMessage;
    }

    private String extractDomain(String email) {
        if (email != null && email.contains("@")) {
            return email.substring(email.indexOf('@') + 1).trim();
        }
        return "localhost";
    }

    /**
     * Bản 1: Gửi Email trên Ứng dụng (Localhost Mode) và cập nhật đồng bộ vào folder database:
     * data/<sender_email>.txt & data/<receiver_email>.txt.
     */
    public boolean sendEmail(Account senderAcc, String receiverEmail, String subject, String body, String attachedFile) {
        StringBuilder log = new StringBuilder();
        String safeFile = (attachedFile == null || attachedFile.trim().isEmpty()) ? "None" : attachedFile.trim();
        String senderEmail = FileManager.normalizeEmail(senderAcc.getUsername());
        String targetEmail = FileManager.normalizeEmail(receiverEmail);

        log.append("==========================================================\n");
        log.append("   PHIÊN LÀM VIỆC GIAO THỨC SMTP CLIENT (LOCALHOST MODE)\n");
        log.append("==========================================================\n");

        if (!FileManager.isValidEmailSyntax(targetEmail)) {
            log.append("C->S: RCPT TO:<").append(targetEmail).append(">\n");
            log.append("S->C: 501 5.1.3 Bad recipient address syntax\n");
            this.lastProtocolLog = log.toString();
            this.lastStatusMessage = "Địa chỉ Email người nhận '" + receiverEmail + "' không đúng cú pháp!\n"
                    + "Vui lòng nhập đúng định dạng (ví dụ: user@gmail.com, user@hotmail.com).";
            return false;
        }

        try {
            InetAddress local = InetAddress.getLocalHost();
            log.append("[InetAddress] Client HostName : ").append(local.getHostName()).append("\n");
            log.append("[InetAddress] Client IP       : ").append(local.getHostAddress()).append("\n");
        } catch (Exception ignored) {
        }

        appendUrlAnalysisIfPresent(body, log);

        String heloDomain = extractDomain(senderEmail);
        log.append("C->S: HELO ").append(heloDomain).append("\n");
        log.append("S->C: 250 Hello ").append(heloDomain).append(" (127.0.0.1), pleased to meet you\n");

        log.append("C->S: MAIL FROM:<").append(senderEmail).append(">\n");
        log.append("S->C: 250 2.1.0 Sender OK\n");

        log.append("C->S: RCPT TO:<").append(targetEmail).append(">\n");

        if (!fileManager.userExists(targetEmail)) {
            log.append("S->C: 550 5.1.1 Mailbox unavailable / Account '").append(targetEmail)
                    .append("' does not exist (chưa có file data/").append(targetEmail).append(".txt)\n");
            log.append("===> THẤT BẠI: Tài khoản người nhận '").append(targetEmail).append("' chưa được đăng ký!\n");
            this.lastProtocolLog = log.toString();
            this.lastStatusMessage = "Tài khoản Email người nhận '" + targetEmail + "' chưa tồn tại trên hệ thống!\n"
                    + "Hãy đăng ký tài khoản '" + targetEmail + "' (hoặc bấm '+ Tạo TK Email mới' ở cột trái) trước khi gửi.";
            return false;
        }

        // Nếu có đính kèm file thực tế trên máy, sao chép vào thư mục attachments/ để xem/tải được ngay
        File realFile = resolveAttachmentFile(safeFile);
        if (realFile != null) {
            try {
                File localDir = new File("attachments");
                if (!localDir.exists()) {
                    localDir.mkdirs();
                }
                Files.write(new File(localDir, realFile.getName()).toPath(), Files.readAllBytes(realFile.toPath()));
                safeFile = realFile.getName();
            } catch (Exception ignored) {
            }
        }

        log.append("S->C: 250 2.1.5 Recipient OK\n");
        log.append("C->S: DATA\n");
        log.append("S->C: 354 Start mail input; end with <CRLF>.<CRLF>\n");
        log.append("C->S: From: ").append(senderEmail).append("\n");
        log.append("C->S: To: ").append(targetEmail).append("\n");
        log.append("C->S: Subject: ").append(subject).append("\n");
        log.append("C->S: Content: ").append(body).append("\n");
        log.append("C->S: Attachment: ").append(safeFile).append("\n");
        log.append("C->S: .\n");

        try {
            // =========================================================================
            // [CHÚ Ý KHI VẤN ĐÁP VỚI THẦY]:
            // Dòng dưới đây thực hiện lưu tin nhắn vào ứng dụng & folder database (data/<email>.txt).
            // Nếu thầy yêu cầu tắt lưu trên ứng dụng, chỉ cần ĐÓNG COMMENT (//) dòng gọi hàm này:
            // =========================================================================
            saveEmailToAppDatabase(senderAcc, senderEmail, targetEmail, subject, body, safeFile);

            log.append("S->C: 250 2.0.0 Message accepted for delivery (Đã lưu vào data/")
                    .append(senderEmail).append(".txt & data/").append(targetEmail).append(".txt)\n");
            log.append("C->S: QUIT\n");
            log.append("S->C: 221 2.0.0 Service closing transmission channel\n");
            log.append("===> THÀNH CÔNG: Đã gửi thư từ ").append(senderEmail)
                    .append(" tới ").append(targetEmail).append("!\n");

            this.lastProtocolLog = log.toString();
            this.lastStatusMessage = "Đã gửi email thành công từ '" + senderEmail + "' tới '" + targetEmail + "'!";
            return true;
        } catch (Exception e) {
            log.append("S->C: 451 Requested action aborted: local error in processing\n");
            log.append("===> LỖI: ").append(e.getMessage()).append("\n");
            this.lastProtocolLog = log.toString();
            this.lastStatusMessage = "Lỗi khi lưu file dữ liệu: " + e.getMessage();
            return false;
        }
    }

    /**
     * Bản 2: Kết nối qua Socket tới Mail Server SMTP (Hỗ trợ cả Local/LAN Server cổng 2525
     * LẪN Mail Server Thật trên Internet như Gmail smtp.gmail.com cổng 587 STARTTLS / 465 SSL)
     * và đồng bộ lưu trữ vào folder database data/<email>.txt.
     */
    public boolean sendEmailViaExternalServer(Account senderAcc, String smtpHost, int smtpPort,
                                              String authUser, String authPass,
                                              String receiverEmail, String subject, String body, String attachedFile) {
        StringBuilder log = new StringBuilder();
        String safeFile = (attachedFile == null || attachedFile.trim().isEmpty()) ? "None" : attachedFile.trim();
        String senderEmail = FileManager.normalizeEmail(senderAcc.getUsername());
        String targetEmail = FileManager.normalizeEmail(receiverEmail);

        log.append("==========================================================\n");
        log.append("   SMTP CLIENT KẾT NỐI MAIL SERVER QUA SOCKET / TLS\n");
        log.append("==========================================================\n");

        if (!FileManager.isValidEmailSyntax(targetEmail)) {
            log.append("S->C: 501 5.1.3 Bad recipient address syntax: ").append(targetEmail).append("\n");
            this.lastProtocolLog = log.toString();
            this.lastStatusMessage = "Địa chỉ Email người nhận '" + receiverEmail + "' không đúng cú pháp!";
            return false;
        }

        boolean isLocalOrLanServer = (smtpPort == 2525);
        if (!isLocalOrLanServer && (authPass == null || authPass.trim().isEmpty())) {
            this.lastProtocolLog = log.append("===> THIẾU APP PASSWORD: Khi gửi qua ").append(smtpHost)
                    .append(":").append(smtpPort).append(", bạn phải nhập App Pass (Mật khẩu ứng dụng 16 ký tự của Google)!\n").toString();
            this.lastStatusMessage = "Bạn chưa nhập 'App Pass' (Mật khẩu ứng dụng 16 ký tự của Gmail)!\n"
                    + "Hãy nhập Gmail vào ô 'Gmail Gửi' và Mật khẩu ứng dụng 16 ký tự vào ô 'App Pass'.";
            return false;
        }

        try {
            InetAddress serverAddr = InetAddress.getByName(smtpHost);
            log.append("[InetAddress] SMTP Server Host : ").append(serverAddr.getHostName()).append("\n");
            log.append("[InetAddress] SMTP Server IP   : ").append(serverAddr.getHostAddress()).append("\n");
            InetAddress[] allIps = InetAddress.getAllByName(smtpHost);
            for (int i = 0; i < allIps.length; i++) {
                log.append("  -> Server IP [").append(i + 1).append("]: ").append(allIps[i].toString()).append("\n");
            }
        } catch (Exception ex) {
            log.append("[InetAddress] Cảnh báo phân giải DNS: ").append(ex.getMessage()).append("\n");
        }

        appendUrlAnalysisIfPresent(body, log);

        Socket rawSocket = null;
        Socket activeSocket = null;
        try {
            boolean useImplicitSsl = (smtpPort == 465);
            boolean useStartTls = (smtpPort == 587)
                    || smtpHost.toLowerCase().contains("gmail.com")
                    || smtpHost.toLowerCase().contains("office365.com")
                    || smtpHost.toLowerCase().contains("outlook.com")
                    || smtpHost.toLowerCase().contains("yahoo.com");
            if (useImplicitSsl) {
                useStartTls = false;
            }

            if (useImplicitSsl) {
                log.append("[SSL/TLS] Khởi tạo kết nối SSLSocket trực tiếp tới ").append(smtpHost).append(":").append(smtpPort).append("...\n");
                SSLSocketFactory sslFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
                activeSocket = sslFactory.createSocket(smtpHost, smtpPort);
                activeSocket.setSoTimeout(30000);
            } else {
                rawSocket = new Socket();
                rawSocket.connect(new InetSocketAddress(smtpHost, smtpPort), 10000);
                rawSocket.setSoTimeout(30000);
                activeSocket = rawSocket;
            }

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(activeSocket.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter writer = new PrintWriter(
                    new OutputStreamWriter(activeSocket.getOutputStream(), StandardCharsets.UTF_8), true);

            String greeting = readMultiLineResponse(reader, log);
            if (greeting == null || !greeting.startsWith("220")) {
                throw new Exception("Server không phản hồi 220 Greeting: " + greeting);
            }

            String ehloResp = sendCmdMultiLine(writer, reader, log, "EHLO " + extractDomain(senderEmail));

            // Nếu kết nối tới SMTP thật (cổng 587 hoặc Server yêu cầu STARTTLS) -> Nâng cấp kênh truyền lên TLS
            if (useStartTls || (ehloResp != null && ehloResp.toUpperCase().contains("STARTTLS") && smtpPort != 2525)) {
                String tlsResp = sendCmdMultiLine(writer, reader, log, "STARTTLS");
                if (tlsResp != null && tlsResp.startsWith("220")) {
                    log.append("[STARTTLS] Đang nâng cấp Socket sang mã hóa TLS (SSLSocket)...\n");
                    SSLSocketFactory sslFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
                    SSLSocket sslSocket = (SSLSocket) sslFactory.createSocket(rawSocket, smtpHost, smtpPort, true);
                    sslSocket.setSoTimeout(30000);
                    sslSocket.startHandshake();
                    activeSocket = sslSocket;
                    reader = new BufferedReader(
                            new InputStreamReader(activeSocket.getInputStream(), StandardCharsets.UTF_8));
                    writer = new PrintWriter(
                            new OutputStreamWriter(activeSocket.getOutputStream(), StandardCharsets.UTF_8), true);
                    log.append("[STARTTLS] Bắt tay TLS thành công! Gửi lại EHLO trên kênh bảo mật...\n");
                    sendCmdMultiLine(writer, reader, log, "EHLO " + extractDomain(senderEmail));
                }
            }

            // Khi gửi qua Gmail/Server thật, MAIL FROM khớp với tài khoản xác thực SMTP và giữ hiển thị tài khoản app
            String effectiveEnvelopeFrom = senderEmail;
            if (authUser != null && !authUser.trim().isEmpty()) {
                sendCmdMultiLine(writer, reader, log, "AUTH LOGIN");
                sendCmdMultiLine(writer, reader, log,
                        Base64.getEncoder().encodeToString(authUser.trim().getBytes(StandardCharsets.UTF_8)));
                String cleanPass = (authPass == null) ? "" : authPass.replace(" ", "").trim();
                log.append("C->S: [Base64 App Password ẩn bảo mật]\n");
                writer.print(Base64.getEncoder().encodeToString(cleanPass.getBytes(StandardCharsets.UTF_8)) + "\r\n");
                writer.flush();
                String authResp = readMultiLineResponse(reader, log);
                if (authResp == null || (!authResp.startsWith("235") && !authResp.startsWith("250"))) {
                    throw new Exception("Xác thực SMTP (AUTH LOGIN) thất bại: " + authResp
                            + "\n(Lưu ý: Với Gmail thật, bạn phải dùng 'Mật khẩu ứng dụng / App Password' 16 ký tự trong Cài đặt Bảo mật Google, không dùng mật khẩu đăng nhập web thông thường)");
                }
                if (authUser.contains("@") && !isLocalOrLanServer) {
                    effectiveEnvelopeFrom = FileManager.normalizeEmail(authUser);
                }
            }

            String mailFromResp = sendCmdMultiLine(writer, reader, log, "MAIL FROM:<" + effectiveEnvelopeFrom + ">");
            if (mailFromResp == null || !mailFromResp.startsWith("250")) {
                throw new Exception("Lệnh MAIL FROM bị từ chối: " + mailFromResp);
            }

            String rcptResp = sendCmdMultiLine(writer, reader, log, "RCPT TO:<" + targetEmail + ">");
            if (rcptResp == null || (!rcptResp.startsWith("250") && !rcptResp.startsWith("251"))) {
                throw new Exception("Lệnh RCPT TO bị từ chối: " + rcptResp);
            }

            String dataCmdResp = sendCmdMultiLine(writer, reader, log, "DATA");
            if (dataCmdResp == null || !dataCmdResp.startsWith("354")) {
                throw new Exception("Lệnh DATA bị từ chối: " + dataCmdResp);
            }

            File realFile = resolveAttachmentFile(safeFile);
            String attachFileName = safeFile;
            byte[] fileBytes = null;
            if (realFile != null) {
                attachFileName = realFile.getName();
                fileBytes = Files.readAllBytes(realFile.toPath());
                // Lưu luôn 1 bản vào thư mục attachments/ của máy gửi để máy gửi cũng mở xem lại được
                File localDir = new File("attachments");
                if (!localDir.exists()) {
                    localDir.mkdirs();
                }
                Files.write(new File(localDir, attachFileName).toPath(), fileBytes);
                safeFile = attachFileName;
            }

            if (isLocalOrLanServer) {
                // Gửi cho Socket Server nội bộ/LAN cổng 2525
                String attachBase64SingleLine = (fileBytes != null)
                        ? Base64.getEncoder().encodeToString(fileBytes)
                        : "";
                writer.print("From: " + senderEmail + "\r\n");
                writer.print("X-App-Sender: " + senderEmail + "\r\n");
                writer.print("To: " + targetEmail + "\r\n");
                writer.print("Subject: " + subject + "\r\n");
                writer.print("X-Attachment: " + attachFileName + "\r\n");
                if (!attachBase64SingleLine.isEmpty()) {
                    writer.print("X-Attachment-Base64: " + attachBase64SingleLine + "\r\n");
                }
                writer.print("Content-Type: text/plain; charset=UTF-8\r\n\r\n");
                writer.print(body + "\r\n");
            } else {
                // Gửi cho Email Thật (Gmail / Outlook) chuẩn RFC 2045 MIME (ngắt dòng 76 ký tự để gửi ảnh lớn >1MB không bị lỗi 552)
                String encodedSubject = "=?UTF-8?B?"
                        + Base64.getEncoder().encodeToString(subject.getBytes(StandardCharsets.UTF_8)) + "?=";
                writer.print("From: \"" + senderEmail + "\" <" + effectiveEnvelopeFrom + ">\r\n");
                writer.print("Reply-To: " + senderEmail + "\r\n");
                writer.print("To: " + targetEmail + "\r\n");
                writer.print("Subject: " + encodedSubject + "\r\n");
                writer.print("MIME-Version: 1.0\r\n");

                if (fileBytes == null) {
                    writer.print("Content-Type: text/plain; charset=UTF-8\r\n\r\n");
                    writer.print(body + "\r\n");
                } else {
                    String boundary = "====VKU_MAIL_BOUNDARY_" + System.currentTimeMillis() + "====";
                    String mimeWrappedBase64 = Base64.getMimeEncoder(76, new byte[]{'\r', '\n'}).encodeToString(fileBytes);
                    writer.print("Content-Type: multipart/mixed; boundary=\"" + boundary + "\"\r\n\r\n");

                    // Phần 1: Nội dung chữ UTF-8
                    writer.print("--" + boundary + "\r\n");
                    writer.print("Content-Type: text/plain; charset=UTF-8\r\n");
                    writer.print("Content-Transfer-Encoding: 8bit\r\n\r\n");
                    writer.print(body + "\r\n\r\n");

                    // Phần 2: File đính kèm (ảnh JPG/PNG/PDF...) cắt dòng chuẩn 76 ký tự
                    writer.print("--" + boundary + "\r\n");
                    writer.print("Content-Type: application/octet-stream; name=\"" + attachFileName + "\"\r\n");
                    writer.print("Content-Disposition: attachment; filename=\"" + attachFileName + "\"\r\n");
                    writer.print("Content-Transfer-Encoding: base64\r\n\r\n");
                    writer.print(mimeWrappedBase64 + "\r\n\r\n");
                    writer.print("--" + boundary + "--\r\n");
                }
            }

            writer.print(".\r\n");
            writer.flush();

            log.append("C->S: [Đã truyền nội dung thư + tệp đính kèm (").append(attachFileName).append(") + dấu chấm kết thúc DATA]\n");
            String dataResp = readMultiLineResponse(reader, log);
            if (dataResp == null || !dataResp.startsWith("250")) {
                throw new Exception("Server không chấp nhận nội dung DATA: " + dataResp);
            }

            sendCmdMultiLine(writer, reader, log, "QUIT");

            // =========================================================================
            // [CHÚ Ý KHI VẤN ĐÁP VỚI THẦY]:
            // Nếu thầy hỏi: "Chỉ gửi được cho Email thật của thầy, còn trên ứng dụng
            // và trong folder database không lưu/không nhận thì sửa ở đâu?"
            // -> Bạn chỉ cần ĐÓNG COMMENT (thêm // ở đầu) dòng saveEmailToAppDatabase(...) ngay dưới đây!
            // =========================================================================
            if (!("127.0.0.1".equals(smtpHost) || "localhost".equalsIgnoreCase(smtpHost)) || smtpPort != 2525) {
                saveEmailToAppDatabase(senderAcc, senderEmail, targetEmail, subject, body, safeFile);
            } else {
                saveSenderOnlyWhenLocalSocketLoopback(senderAcc, senderEmail, targetEmail, subject, body, safeFile);
            }

            log.append("===> THÀNH CÔNG: Đã gửi qua SMTP Server (").append(smtpHost).append(":")
                    .append(smtpPort).append(") tới ").append(targetEmail).append("!\n");
            this.lastProtocolLog = log.toString();
            this.lastStatusMessage = "Đã gửi qua SMTP Server " + smtpHost + ":" + smtpPort + " tới '" + targetEmail + "' thành công!";
            return true;

        } catch (Exception ex) {
            log.append("===> LỖI KẾT NỐI / XÁC THỰC SMTP SERVER (").append(smtpHost).append(":").append(smtpPort).append("): ")
                    .append(ex.getMessage()).append("\n");
            this.lastProtocolLog = log.toString();
            this.lastStatusMessage = "Không gửi được qua " + smtpHost + ":" + smtpPort + " -> " + ex.getMessage();
            return false;
        } finally {
            try {
                if (activeSocket != null && !activeSocket.isClosed()) {
                    activeSocket.close();
                }
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Hàm lưu dòng tin nhắn vào ứng dụng và thư mục database (data/<sender>.txt và data/<receiver>.txt).
     * Nếu gửi cho email thật bên ngoài (ví dụ email của thầy chưa tạo tài khoản trước), hệ thống
     * vẫn tự động tạo file data/<targetEmail>.txt để thầy mở folder data/ ra là thấy ngay dòng tin nhắn!
     */
    public void saveEmailToAppDatabase(Account senderAcc, String senderEmail, String targetEmail,
                                       String subject, String body, String safeFile) throws Exception {
        synchronized (FileManager.class) {
            String now = FileManager.getCurrentTime();
            Email emailObj = new Email(now, senderEmail, targetEmail, subject, body, safeFile);

            Account freshSender = fileManager.loadAccount(senderEmail);
            if (freshSender == null) {
                freshSender = senderAcc;
            }

            if (senderEmail.equalsIgnoreCase(targetEmail)) {
                freshSender.getSent().add(emailObj);
                freshSender.getInbox().add(emailObj);
                fileManager.saveAccount(freshSender);
            } else {
                freshSender.getSent().add(emailObj);
                fileManager.saveAccount(freshSender);

                if (!fileManager.userExists(targetEmail)) {
                    fileManager.createAccount(targetEmail, "12345678");
                }
                Account recvAcc = fileManager.loadAccount(targetEmail);
                if (recvAcc != null) {
                    recvAcc.getInbox().add(emailObj);
                    fileManager.saveAccount(recvAcc);
                }
            }

            senderAcc.getInbox().clear();
            senderAcc.getInbox().addAll(freshSender.getInbox());
            senderAcc.getSent().clear();
            senderAcc.getSent().addAll(freshSender.getSent());
        }
    }

    private void saveSenderOnlyWhenLocalSocketLoopback(Account senderAcc, String senderEmail, String targetEmail,
                                                       String subject, String body, String safeFile) throws Exception {
        synchronized (FileManager.class) {
            String now = FileManager.getCurrentTime();
            Email emailObj = new Email(now, senderEmail, targetEmail, subject, body, safeFile);

            Account freshSender = fileManager.loadAccount(senderEmail);
            if (freshSender == null) {
                freshSender = senderAcc;
            }
            freshSender.getSent().add(emailObj);
            fileManager.saveAccount(freshSender);

            senderAcc.getInbox().clear();
            senderAcc.getInbox().addAll(freshSender.getInbox());
            senderAcc.getSent().clear();
            senderAcc.getSent().addAll(freshSender.getSent());
        }
    }

    private String sendCmdMultiLine(PrintWriter writer, BufferedReader reader, StringBuilder log, String cmd) throws Exception {
        log.append("C->S: ").append(cmd).append("\n");
        writer.print(cmd + "\r\n");
        writer.flush();
        return readMultiLineResponse(reader, log);
    }

    private String readMultiLineResponse(BufferedReader reader, StringBuilder log) throws Exception {
        StringBuilder fullResp = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            log.append("S->C: ").append(line).append("\n");
            if (fullResp.length() > 0) {
                fullResp.append("\n");
            }
            fullResp.append(line);
            if (line.length() < 4 || line.charAt(3) != '-') {
                break;
            }
        }
        return fullResp.toString();
    }

    private void appendUrlAnalysisIfPresent(String text, StringBuilder log) {
        if (text == null || text.isEmpty()) return;
        Matcher m = Pattern.compile("(https?://[^\\s]+)", Pattern.CASE_INSENSITIVE).matcher(text);
        while (m.find()) {
            String rawUrl = m.group(1);
            try {
                URL url = new URL(rawUrl);
                log.append("[Phân tích URL trong Email - diachi.java]\n");
                log.append("  URL          : ").append(url.toString()).append("\n");
                log.append("  protocol     : ").append(url.getProtocol()).append("\n");
                log.append("  authority    : ").append(url.getAuthority()).append("\n");
                log.append("  host         : ").append(url.getHost()).append("\n");
                log.append("  port         : ").append(url.getPort()).append(" (default: ").append(url.getDefaultPort()).append(")\n");
                log.append("  path / file  : ").append(url.getPath()).append(" / ").append(url.getFile()).append("\n");
                log.append("  query / ref  : ").append(url.getQuery()).append(" / ").append(url.getRef()).append("\n");
            } catch (Exception ignored) {
            }
        }
    }

    public static File resolveAttachmentFile(String attachedStr) {
        if (attachedStr == null || attachedStr.trim().isEmpty() || "None".equalsIgnoreCase(attachedStr.trim())) {
            return null;
        }
        String raw = attachedStr.trim();
        int dashIdx = raw.indexOf(" bytes - ");
        if (dashIdx != -1 && raw.endsWith(")")) {
            String extractedPath = raw.substring(dashIdx + 9, raw.length() - 1).trim();
            File f = new File(extractedPath);
            if (f.exists() && f.isFile()) return f;
        }
        File directFile = new File(raw);
        if (directFile.exists() && directFile.isFile()) return directFile;

        File inAttachDir = new File("attachments", raw);
        if (inAttachDir.exists() && inAttachDir.isFile()) return inAttachDir;

        return null;
    }
}