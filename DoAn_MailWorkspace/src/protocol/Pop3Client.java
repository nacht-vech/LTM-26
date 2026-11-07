package protocol;

import model.Account;
import model.Email;
import storage.FileManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Xử lý giao thức nhận tin POP3 (Post Office Protocol v3).
 * Luôn đồng bộ dữ liệu mới nhất từ file data/<email>.txt lên giao diện GUI.
 */
public class Pop3Client {
    private final FileManager fileManager;
    private String lastProtocolLog = "";

    public Pop3Client() {
        this.fileManager = new FileManager();
    }

    public String getLastProtocolLog() {
        return lastProtocolLog;
    }

    public Account fetchEmails(Account userAcc) {
        StringBuilder log = new StringBuilder();
        log.append("==========================================================\n");
        log.append("   PHIÊN LÀM VIỆC GIAO THỨC POP3 CLIENT (RECEIVE MAIL)\n");
        log.append("==========================================================\n");

        try {
            InetAddress local = InetAddress.getLocalHost();
            log.append("[InetAddress] POP3 Client Host: ").append(local.getHostName())
                    .append(" (").append(local.getHostAddress()).append(")\n");
        } catch (Exception ignored) {
        }

        log.append("C->S: USER ").append(userAcc.getUsername()).append("\n");
        log.append("S->C: +OK User name accepted\n");
        log.append("C->S: PASS **********\n");
        log.append("S->C: +OK Pass accepted, welcome ").append(userAcc.getUsername()).append("\n");

        try {
            Account latestAcc = fileManager.loadAccount(userAcc.getUsername());
            if (latestAcc != null) {
                userAcc = latestAcc;
            }
        } catch (Exception e) {
            log.append("[LỖI] Khi đồng bộ dữ liệu từ file: ").append(e.getMessage()).append("\n");
        }

        List<Email> inbox = userAcc.getInbox();
        log.append("C->S: STAT\n");
        log.append("S->C: +OK ").append(inbox.size()).append(" message(s) in inbox (data/")
                .append(userAcc.getUsername()).append(".txt)\n");

        if (inbox.isEmpty()) {
            log.append("\n[THÔNG BÁO] Hộp thư đến (INBOX) của bạn hiện đang trống.\n");
        } else {
            log.append("C->S: LIST\n");
            log.append("S->C: +OK ").append(inbox.size()).append(" messages listing follows\n");
            for (int i = 0; i < inbox.size(); i++) {
                Email item = inbox.get(i);
                int octets = (item.getSubject().length() + item.getBody().length()) + 64;
                log.append("S->C: ").append(i + 1).append(" ").append(octets)
                        .append(" octets (From: ").append(item.getSender())
                        .append(" | Subject: ").append(item.getSubject()).append(")\n");
            }
            log.append("S->C: .\n\n");

            for (int i = 0; i < inbox.size(); i++) {
                Email e = inbox.get(i);
                log.append("C->S: RETR ").append(i + 1).append("\n");
                log.append("S->C: +OK message ").append(i + 1).append(" follows\n");
                log.append("  [Date]       : ").append(e.getTime()).append("\n");
                log.append("  [From]       : ").append(e.getSender()).append("\n");
                log.append("  [To]         : ").append(e.getReceiver()).append("\n");
                log.append("  [Subject]    : ").append(e.getSubject()).append("\n");
                log.append("  [Attachment] : ").append(e.getAttachedFile()).append("\n");
                log.append("S->C: .\n");
            }
        }

        log.append("C->S: QUIT\n");
        log.append("S->C: +OK POP3 server signing off\n");
        this.lastProtocolLog = log.toString();
        return userAcc;
    }

    public boolean deleteEmailPop3(Account userAcc, int index) {
        StringBuilder log = new StringBuilder();
        log.append("==========================================================\n");
        log.append("   THỰC THI LỆNH POP3 DELE (XÓA THƯ TRONG HỘP THƯ ĐẾN)\n");
        log.append("==========================================================\n");
        log.append("C->S: USER ").append(userAcc.getUsername()).append("\n");
        log.append("S->C: +OK User accepted\n");
        log.append("C->S: DELE ").append(index + 1).append("\n");

        try {
            synchronized (FileManager.class) {
                Account fresh = fileManager.loadAccount(userAcc.getUsername());
                if (fresh != null) {
                    userAcc.getInbox().clear();
                    userAcc.getInbox().addAll(fresh.getInbox());
                    userAcc.getSent().clear();
                    userAcc.getSent().addAll(fresh.getSent());
                }
                if (index < 0 || index >= userAcc.getInbox().size()) {
                    log.append("S->C: -ERR Message ").append(index + 1).append(" does not exist\n");
                    this.lastProtocolLog = log.toString();
                    return false;
                }
                Email removed = userAcc.getInbox().remove(index);
                fileManager.saveAccount(userAcc);
                log.append("S->C: +OK Message ").append(index + 1).append(" (").append(removed.getSubject())
                        .append(") deleted and updated in data/").append(userAcc.getUsername()).append(".txt\n");
                log.append("C->S: QUIT\n");
                log.append("S->C: +OK POP3 server signing off\n");
                this.lastProtocolLog = log.toString();
                return true;
            }
        } catch (Exception e) {
            log.append("S->C: -ERR Cannot update account file: ").append(e.getMessage()).append("\n");
            this.lastProtocolLog = log.toString();
            return false;
        }
    }

    public Account viewSentEmails(Account userAcc) {
        StringBuilder log = new StringBuilder();
        log.append("==========================================================\n");
        log.append("   ĐỒNG BỘ DANH SÁCH THƯ ĐÃ GỬI [SENT] TỪ FILE .TXT\n");
        log.append("==========================================================\n");
        try {
            Account latestAcc = fileManager.loadAccount(userAcc.getUsername());
            if (latestAcc != null) {
                userAcc = latestAcc;
            }
            log.append("Đã nạp ").append(userAcc.getSent().size())
                    .append(" thư đã gửi từ file data/").append(userAcc.getUsername()).append(".txt\n");
        } catch (Exception e) {
            log.append("Lỗi khi đồng bộ dữ liệu: ").append(e.getMessage()).append("\n");
        }
        this.lastProtocolLog = log.toString();
        return userAcc;
    }

    public boolean fetchFromExternalPop3Server(Account userAcc, String pop3Host, int pop3Port,
                                               String pop3User, String pop3Pass) {
        StringBuilder log = new StringBuilder();
        log.append("==========================================================\n");
        log.append("   POP3 CLIENT KẾT NỐI MAIL SERVER CÓ SẴN QUA SOCKET\n");
        log.append("==========================================================\n");

        try {
            InetAddress addr = InetAddress.getByName(pop3Host);
            log.append("[InetAddress] POP3 Server: ").append(addr.getHostName())
                    .append(" (").append(addr.getHostAddress()).append(")\n");
        } catch (Exception ignored) {
        }

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(pop3Host, pop3Port), 5000);
            socket.setSoTimeout(7000);

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter writer = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

            log.append("S->C: ").append(reader.readLine()).append("\n");

            sendCmd(writer, reader, log, "USER " + pop3User);
            sendCmd(writer, reader, log, "PASS " + pop3Pass);
            sendCmd(writer, reader, log, "STAT");

            log.append("C->S: LIST\n");
            writer.print("LIST\r\n");
            writer.flush();
            String line;
            while ((line = reader.readLine()) != null) {
                log.append("S->C: ").append(line).append("\n");
                if (".".equals(line.trim()) || line.startsWith("-ERR")) {
                    break;
                }
            }

            sendCmd(writer, reader, log, "QUIT");
            this.lastProtocolLog = log.toString();
            return true;
        } catch (Exception ex) {
            log.append("===> LỖI KẾT NỐI POP3 SERVER (").append(pop3Host).append(":").append(pop3Port)
                    .append("): ").append(ex.getMessage()).append("\n");
            this.lastProtocolLog = log.toString();
            return false;
        }
    }

    private void sendCmd(PrintWriter writer, BufferedReader reader, StringBuilder log, String cmd) throws Exception {
        String displayCmd = cmd.startsWith("PASS ") ? "PASS **********" : cmd;
        log.append("C->S: ").append(displayCmd).append("\n");
        writer.print(cmd + "\r\n");
        writer.flush();
        log.append("S->C: ").append(reader.readLine()).append("\n");
    }
}