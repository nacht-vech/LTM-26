package storage;

import model.Account;
import model.Email;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Quản lý đọc/ghi dữ liệu cho từng tài khoản vào 1 file .txt duy nhất (data/<email>.txt).
 * - Tên tài khoản bắt buộc phải là địa chỉ Email hợp lệ (VD: user@gmail.com, user@hotmail.com...).
 * - Giữ nguyên tuyệt đối tên miền Email của người dùng, KHÔNG tự động gán @vku.udn.vn.
 * - Mật khẩu khi tạo tài khoản mới bắt buộc từ 8 ký tự trở lên.
 * - Đồng bộ khóa tĩnh (FileManager.class) để đảm bảo gửi/nhận thư giữa nhiều cửa sổ luôn chính xác.
 */
public class FileManager {
    private static final String DATA_DIR = "data/";
    public static final int MIN_PASSWORD_LENGTH = 8;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public FileManager() {
        synchronized (FileManager.class) {
            File dir = new File(DATA_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            ensureSampleAccounts();
        }
    }

    public static String getCurrentTime() {
        return LocalDateTime.now().format(FORMATTER);
    }

    /**
     * Kiểm tra đúng cú pháp địa chỉ Email (VD: user@gmail.com, user@hotmail.com, user@yahoo.com...).
     */
    public static boolean isValidEmailSyntax(String email) {
        if (email == null) {
            return false;
        }
        return EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    /**
     * Kiểm tra điều kiện mật khẩu khi tạo tài khoản mới: ít nhất 8 ký tự.
     */
    public static boolean isValidNewPassword(String password) {
        return password != null && password.trim().length() >= MIN_PASSWORD_LENGTH;
    }

    /**
     * Chuẩn hóa địa chỉ Email (giữ nguyên toàn bộ phần tên và @domain của người dùng, chỉ cắt khoảng trắng và chuyển chữ thường).
     */
    public static String normalizeEmail(String email) {
        if (email == null) {
            return "";
        }
        return email.trim().toLowerCase();
    }

    /**
     * Mỗi tài khoản Email có 1 file .txt riêng biệt theo đúng địa chỉ Email đó: data/<email>.txt
     * (Ví dụ: data/alice@gmail.com.txt, data/bob@hotmail.com.txt).
     */
    public File getAccountFile(String email) {
        String normalized = normalizeEmail(email);
        return new File(DATA_DIR, normalized + ".txt");
    }

    public boolean userExists(String email) {
        synchronized (FileManager.class) {
            if (!isValidEmailSyntax(email)) {
                return false;
            }
            return getAccountFile(email).exists();
        }
    }

    /**
     * Khởi tạo sẵn 2 tài khoản mẫu đúng chuẩn Email (alice@gmail.com và bob@hotmail.com)
     * với mật khẩu đủ 8 ký tự (12345678) để có thể kiểm thử gửi/nhận ngay.
     */
    private void ensureSampleAccounts() {
        try {
            if (!userExists("alice@gmail.com")) {
                String now = getCurrentTime();
                saveAccount(new Account("alice@gmail.com", "12345678", now, now));
            }
            if (!userExists("bob@hotmail.com")) {
                String now = getCurrentTime();
                saveAccount(new Account("bob@hotmail.com", "12345678", now, now));
            }
        } catch (IOException ignored) {
        }
    }

    /**
     * Tạo tài khoản mới:
     * - Tên tài khoản bắt buộc phải là địa chỉ Email hợp lệ.
     * - Mật khẩu bắt buộc phải có ít nhất 8 ký tự.
     */
    public boolean createAccount(String email, String password) throws IOException {
        synchronized (FileManager.class) {
            String normalizedEmail = normalizeEmail(email);
            if (!isValidEmailSyntax(normalizedEmail) || !isValidNewPassword(password)) {
                return false;
            }
            if (userExists(normalizedEmail)) {
                return false;
            }
            String now = getCurrentTime();
            Account newAccount = new Account(normalizedEmail, password.trim(), now, now);
            saveAccount(newAccount);
            return true;
        }
    }

    /**
     * Load TOÀN BỘ dữ liệu của tài khoản từ file data/<email>.txt.
     */
    public Account loadAccount(String email) throws IOException {
        synchronized (FileManager.class) {
            String normalizedEmail = normalizeEmail(email);
            if (!isValidEmailSyntax(normalizedEmail)) {
                return null;
            }
            File file = getAccountFile(normalizedEmail);
            if (!file.exists()) {
                return null;
            }

            String password = "";
            String createdAt = "";
            String lastLogin = "";
            List<Email> inboxList = new ArrayList<>();
            List<Email> sentList = new ArrayList<>();

            String currentSection = "";
            String eTime = "", eSender = "", eReceiver = "", eSubject = "", eBody = "", eFile = "";
            boolean inMsgBlock = false;

            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                String rawLine;
                while ((rawLine = br.readLine()) != null) {
                    String line = rawLine.trim();
                    if (line.isEmpty()) continue;

                    if (line.equalsIgnoreCase("[ACCOUNT]")) {
                        currentSection = "ACCOUNT";
                        inMsgBlock = false;
                        continue;
                    } else if (line.equalsIgnoreCase("[INBOX]")) {
                        currentSection = "INBOX";
                        inMsgBlock = false;
                        continue;
                    } else if (line.equalsIgnoreCase("[SENT]")) {
                        currentSection = "SENT";
                        inMsgBlock = false;
                        continue;
                    }

                    if ("ACCOUNT".equals(currentSection)) {
                        if (line.startsWith("Password=")) password = line.substring(9).trim();
                        else if (line.startsWith("CreatedAt=")) createdAt = line.substring(10).trim();
                        else if (line.startsWith("LastLogin=")) lastLogin = line.substring(10).trim();
                    } else if ("INBOX".equals(currentSection) || "SENT".equals(currentSection)) {
                        if (line.equals("---MSG---")) {
                            eTime = "";
                            eSender = "";
                            eReceiver = "";
                            eSubject = "";
                            eBody = "";
                            eFile = "";
                            inMsgBlock = true;
                        } else if (line.equals("---END_MSG---")) {
                            if (inMsgBlock) {
                                Email mailObj = new Email(eTime, eSender, eReceiver, eSubject, decodeMultiline(eBody), eFile);
                                if ("INBOX".equals(currentSection)) {
                                    inboxList.add(mailObj);
                                } else {
                                    sentList.add(mailObj);
                                }
                            }
                            inMsgBlock = false;
                        } else if (inMsgBlock) {
                            if (line.startsWith("Time=")) eTime = line.substring(5);
                            else if (line.startsWith("Sender=")) eSender = line.substring(7);
                            else if (line.startsWith("Receiver=")) eReceiver = line.substring(9);
                            else if (line.startsWith("Subject=")) eSubject = line.substring(8);
                            else if (line.startsWith("Body=")) eBody = line.substring(5);
                            else if (line.startsWith("File=")) eFile = line.substring(5);
                        }
                    }
                }
            }

            Account account = new Account(normalizedEmail, password, createdAt, lastLogin);
            account.getInbox().addAll(inboxList);
            account.getSent().addAll(sentList);
            return account;
        }
    }

    /**
     * Ghi đè toàn bộ dữ liệu cập nhật vào file .txt duy nhất của tài khoản (data/<email>.txt).
     */
    public void saveAccount(Account account) throws IOException {
        synchronized (FileManager.class) {
            String normalizedEmail = normalizeEmail(account.getUsername());
            File file = getAccountFile(normalizedEmail);
            try (BufferedWriter bw = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(file, false), StandardCharsets.UTF_8))) {

                bw.write("[ACCOUNT]\n");
                bw.write("Email=" + sanitizeSingleLine(normalizedEmail) + "\n");
                bw.write("Password=" + sanitizeSingleLine(account.getPassword()) + "\n");
                bw.write("CreatedAt=" + sanitizeSingleLine(account.getCreatedAt()) + "\n");
                bw.write("LastLogin=" + sanitizeSingleLine(account.getLastLogin()) + "\n\n");

                bw.write("[INBOX]\n");
                for (Email e : account.getInbox()) {
                    writeEmailBlock(bw, e);
                }
                bw.write("\n");

                bw.write("[SENT]\n");
                for (Email e : account.getSent()) {
                    writeEmailBlock(bw, e);
                }
                bw.flush();
            }
        }
    }

    private void writeEmailBlock(BufferedWriter bw, Email e) throws IOException {
        bw.write("---MSG---\n");
        bw.write("Time=" + sanitizeSingleLine(e.getTime()) + "\n");
        bw.write("Sender=" + sanitizeSingleLine(e.getSender()) + "\n");
        bw.write("Receiver=" + sanitizeSingleLine(e.getReceiver()) + "\n");
        bw.write("Subject=" + sanitizeSingleLine(e.getSubject()) + "\n");
        bw.write("Body=" + encodeMultiline(e.getBody()) + "\n");
        bw.write("File=" + sanitizeSingleLine(e.getAttachedFile()) + "\n");
        bw.write("---END_MSG---\n");
    }

    private String sanitizeSingleLine(String s) {
        if (s == null) return "";
        return s.replace("\r", " ").replace("\n", " ").trim();
    }

    private String encodeMultiline(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\r\n", "\\n").replace("\n", "\\n").replace("\r", "\\n");
    }

    private String decodeMultiline(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                if (next == 'n') {
                    out.append('\n');
                    i++;
                    continue;
                } else if (next == '\\') {
                    out.append('\\');
                    i++;
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
    }

    /**
     * Đọc nguyên văn nội dung file data/<email>.txt để hiển thị trực tiếp lên giao diện GUI.
     */
    public String readRawAccountFile(String email) {
        synchronized (FileManager.class) {
            File file = getAccountFile(email);
            if (!file.exists()) {
                return "(File " + file.getPath() + " chưa tồn tại)";
            }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            } catch (IOException e) {
                sb.append("Lỗi đọc file: ").append(e.getMessage());
            }
            return sb.toString();
        }
    }

    /**
     * Lấy danh sách tất cả tài khoản Email hợp lệ đã đăng ký trong thư mục data/*.txt.
     */
    public List<String> listAllUsernames() {
        synchronized (FileManager.class) {
            List<String> emails = new ArrayList<>();
            File dir = new File(DATA_DIR);
            File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".txt"));
            if (files != null) {
                for (File f : files) {
                    String name = f.getName();
                    String candidate = name.substring(0, name.length() - 4);
                    if (isValidEmailSyntax(candidate)) {
                        emails.add(candidate);
                    }
                }
            }
            return emails;
        }
    }
}