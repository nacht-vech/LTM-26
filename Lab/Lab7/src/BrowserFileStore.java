import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Quản lý lưu trữ theo cơ chế: MỖI TÀI KHOẢN CÓ 1 FILE .TXT DUY NHẤT RIÊNG BIỆT (VD: user_admin.txt).
 * Bên trong file .txt của mỗi tài khoản lưu hợp nhất toàn bộ:
 * - Thông tin tài khoản (username, mật khẩu, họ tên, thời gian tạo, thời gian đăng nhập gần nhất, số lần đăng nhập)
 * - Lịch sử các lần đăng nhập (LOGIN_EVENT)
 * - Danh sách Bookmark riêng của tài khoản đó (BOOKMARK)
 * - Lịch sử gửi Request GET/POST/HEAD, thống kê thẻ HTML và File đã gửi/tải của tài khoản đó (REQUEST_FILE_LOG)
 * Mọi cập nhật của tài khoản nào đều được ghi đè tại chỗ (in-place) ngay trong file .txt của tài khoản đó.
 */
public class BrowserFileStore {

    private static final String DATE_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private final File storageDir;

    public static class AccountRecord {
        public String username;
        public String password;
        public String fullName;
        public String createdAt;
        public String lastLoginAt;
        public int loginCount;
        public File accountFile;

        public AccountRecord(String username, String password, String fullName,
                             String createdAt, String lastLoginAt, int loginCount, File accountFile) {
            this.username = username;
            this.password = password;
            this.fullName = fullName;
            this.createdAt = createdAt;
            this.lastLoginAt = lastLoginAt;
            this.loginCount = loginCount;
            this.accountFile = accountFile;
        }
    }

    public static class BookmarkRecord {
        public String id;
        public String username;
        public String title;
        public String url;
        public String createdAt;

        public BookmarkRecord(String id, String username, String title, String url, String createdAt) {
            this.id = id;
            this.username = username;
            this.title = title;
            this.url = url;
            this.createdAt = createdAt;
        }
    }

    public static class RequestLogRecord {
        public String id;
        public String username;
        public String timestamp;
        public String method;
        public String url;
        public int statusCode;
        public int htmlLength;
        public int pCount;
        public int divCount;
        public int spanCount;
        public int imgCount;
        public String attachedFileOrData;

        public RequestLogRecord(String id, String username, String timestamp, String method,
                                String url, int statusCode, int htmlLength,
                                int pCount, int divCount, int spanCount, int imgCount,
                                String attachedFileOrData) {
            this.id = id;
            this.username = username;
            this.timestamp = timestamp;
            this.method = method;
            this.url = url;
            this.statusCode = statusCode;
            this.htmlLength = htmlLength;
            this.pCount = pCount;
            this.divCount = divCount;
            this.spanCount = spanCount;
            this.imgCount = imgCount;
            this.attachedFileOrData = attachedFileOrData;
        }
    }

    public BrowserFileStore(String dirPath) {
        this.storageDir = new File(dirPath == null || dirPath.trim().isEmpty() ? "." : dirPath);
        if (!this.storageDir.exists()) {
            this.storageDir.mkdirs();
        }
        ensureDefaultAccountsInitialized();
    }

    public static String now() {
        return new SimpleDateFormat(DATE_PATTERN).format(new Date());
    }

    private String sanitize(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("|", "/").replace("\r", " ").replace("\n", " ").trim();
    }

    private String safeUsernameForFile(String username) {
        if (username == null) {
            return "unknown";
        }
        return username.trim().toLowerCase().replaceAll("[^a-z0-9._-]", "_");
    }

    /**
     * Trả về đúng file .txt riêng của một tài khoản: user_<username>.txt
     */
    public File getAccountFile(String username) {
        return new File(storageDir, "user_" + safeUsernameForFile(username) + ".txt");
    }

    /**
     * Khởi tạo 2 file tài khoản mẫu (user_admin.txt và user_sinhvien.txt) nếu chưa có.
     */
    private synchronized void ensureDefaultAccountsInitialized() {
        File adminFile = getAccountFile("admin");
        if (!adminFile.exists() || adminFile.length() == 0) {
            createNewAccountFile("admin", "123456", "Quan Tri Vien VKU", true);
        }
        File svFile = getAccountFile("sinhvien");
        if (!svFile.exists() || svFile.length() == 0) {
            createNewAccountFile("sinhvien", "123456", "Sinh Vien Mang May Tinh VKU", true);
        }
    }

    private synchronized void createNewAccountFile(String username, String password, String fullName, boolean addDefaultBookmarks) {
        File userFile = getAccountFile(username);
        List<String> lines = new ArrayList<>();
        String currentTime = now();
        lines.add("# ===========================================================================");
        lines.add("# FILE DU LIEU RIENG CUA TAI KHOAN: " + username + " (" + userFile.getName() + ")");
        lines.add("# Luu chung trong 1 file duy nhat cua tai khoan nay:");
        lines.add("#   1. ACCOUNT          : Thong tin tai khoan, gio tao, gio dang nhap gan nhat, so lan login");
        lines.add("#   2. LOGIN_EVENT      : Lich su dang nhap chi tiet kem IP/Host may tram");
        lines.add("#   3. BOOKMARK         : Cac trang web yeu thich cua tai khoan " + username);
        lines.add("#   4. REQUEST_FILE_LOG : Lich su GET/POST/HEAD, thong ke the HTML & File gui/nhan");
        lines.add("# Moi thay doi cua tai khoan " + username + " deu cap nhat truc tiep trong file nay.");
        lines.add("# ===========================================================================");
        lines.add("ACCOUNT|" + sanitize(username) + "|" + sanitize(password) + "|"
                + sanitize(fullName.isEmpty() ? username : fullName) + "|"
                + currentTime + "|Chua dang nhap|0");

        if (addDefaultBookmarks) {
            lines.add("BOOKMARK|BM01|" + sanitize(username) + "|Trang chu VKU|http://vku.udn.vn|" + currentTime);
            lines.add("BOOKMARK|BM02|" + sanitize(username) + "|Bao Tuoi Tre|https://www.tuoitre.vn|" + currentTime);
            lines.add("BOOKMARK|BM03|" + sanitize(username) + "|Server Test Localhost (GET/POST/HEAD)|http://localhost:8088/vku-demo|" + currentTime);
            lines.add("BOOKMARK|BM04|" + sanitize(username) + "|Example Standard HTML|http://example.com|" + currentTime);
        }
        writeLinesToFile(userFile, lines);
    }

    private synchronized List<String> readLinesFromFile(File file) {
        List<String> lines = new ArrayList<>();
        if (file == null || !file.exists()) {
            return lines;
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return lines;
    }

    private synchronized void writeLinesToFile(File file, List<String> lines) {
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(file, false), StandardCharsets.UTF_8))) {
            for (String line : lines) {
                writer.write(line);
                writer.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public synchronized String registerAccount(String username, String password, String fullName) {
        username = sanitize(username);
        password = sanitize(password);
        fullName = sanitize(fullName);

        if (username.isEmpty() || password.isEmpty()) {
            return "Tên đăng nhập và mật khẩu không được để trống!";
        }
        File userFile = getAccountFile(username);
        if (userFile.exists()) {
            return "Tài khoản '" + username + "' đã tồn tại (File " + userFile.getName() + " đã có sẵn)!";
        }
        createNewAccountFile(username, password, fullName, true);
        return "OK";
    }

    public synchronized AccountRecord loginAndUpdateTimestamp(String username, String password) {
        username = sanitize(username);
        password = sanitize(password);
        File userFile = getAccountFile(username);
        if (!userFile.exists()) {
            return null;
        }

        List<String> lines = readLinesFromFile(userFile);
        AccountRecord matched = null;
        String loginTime = now();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.startsWith("ACCOUNT|")) {
                String[] parts = line.split("\\|", -1);
                if (parts.length >= 7 && parts[1].equalsIgnoreCase(username) && parts[2].equals(password)) {
                    int count = 0;
                    try {
                        count = Integer.parseInt(parts[6]);
                    } catch (NumberFormatException ignored) {
                    }
                    count++;
                    matched = new AccountRecord(parts[1], parts[2], parts[3], parts[4], loginTime, count, userFile);
                    String updatedLine = "ACCOUNT|" + matched.username + "|" + matched.password + "|"
                            + matched.fullName + "|" + matched.createdAt + "|"
                            + matched.lastLoginAt + "|" + matched.loginCount;
                    lines.set(i, updatedLine);
                    break;
                }
            }
        }

        if (matched != null) {
            String localInfo = "unknown-host";
            try {
                InetAddress local = InetAddress.getLocalHost();
                localInfo = local.getHostName() + " (" + local.getHostAddress() + ")";
            } catch (Exception ignored) {
            }
            lines.add("LOGIN_EVENT|" + matched.username + "|" + loginTime + "|" + sanitize(localInfo)
                    + "|Dang nhap thanh cong (Lan thu " + matched.loginCount + ")");
            writeLinesToFile(userFile, lines);
        }
        return matched;
    }

    public synchronized boolean updateAccountPassword(String username, String newPassword, String newFullName) {
        File userFile = getAccountFile(username);
        if (!userFile.exists()) {
            return false;
        }
        List<String> lines = readLinesFromFile(userFile);
        boolean updated = false;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.startsWith("ACCOUNT|")) {
                String[] parts = line.split("\\|", -1);
                if (parts.length >= 7 && parts[1].equalsIgnoreCase(username)) {
                    String pass = (newPassword == null || newPassword.isEmpty()) ? parts[2] : sanitize(newPassword);
                    String name = (newFullName == null || newFullName.isEmpty()) ? parts[3] : sanitize(newFullName);
                    lines.set(i, "ACCOUNT|" + parts[1] + "|" + pass + "|" + name + "|"
                            + parts[4] + "|" + parts[5] + "|" + parts[6]);
                    updated = true;
                    break;
                }
            }
        }
        if (updated) {
            writeLinesToFile(userFile, lines);
        }
        return updated;
    }

    public synchronized void addOrUpdateBookmark(String username, String title, String url) {
        File userFile = getAccountFile(username);
        username = sanitize(username);
        title = sanitize(title);
        url = sanitize(url);
        List<String> lines = readLinesFromFile(userFile);
        String timestamp = now();
        boolean updated = false;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.startsWith("BOOKMARK|")) {
                String[] p = line.split("\\|", -1);
                if (p.length >= 6 && p[4].equalsIgnoreCase(url)) {
                    lines.set(i, "BOOKMARK|" + p[1] + "|" + username + "|" + title + "|" + url + "|" + timestamp);
                    updated = true;
                    break;
                }
            }
        }
        if (!updated) {
            String id = "BM" + (System.currentTimeMillis() % 100000);
            lines.add("BOOKMARK|" + id + "|" + username + "|" + title + "|" + url + "|" + timestamp);
        }
        writeLinesToFile(userFile, lines);
    }

    public synchronized void deleteBookmark(String username, String bookmarkId) {
        File userFile = getAccountFile(username);
        List<String> lines = readLinesFromFile(userFile);
        lines.removeIf(line -> line.startsWith("BOOKMARK|" + bookmarkId + "|"));
        writeLinesToFile(userFile, lines);
    }

    public synchronized List<BookmarkRecord> getBookmarks(String username) {
        File userFile = getAccountFile(username);
        List<BookmarkRecord> list = new ArrayList<>();
        for (String line : readLinesFromFile(userFile)) {
            if (line.startsWith("BOOKMARK|")) {
                String[] p = line.split("\\|", -1);
                if (p.length >= 6) {
                    list.add(new BookmarkRecord(p[1], p[2], p[3], p[4], p[5]));
                }
            }
        }
        return list;
    }

    public synchronized void logRequestAndFile(String username, String method, String url,
                                               int statusCode, int htmlLength,
                                               int pCount, int divCount, int spanCount, int imgCount,
                                               String attachedFileOrData) {
        File userFile = getAccountFile(username);
        List<String> lines = readLinesFromFile(userFile);
        String id = "REQ" + (System.currentTimeMillis() % 1000000);
        String line = "REQUEST_FILE_LOG|" + id + "|" + sanitize(username) + "|" + now() + "|"
                + sanitize(method) + "|" + sanitize(url) + "|" + statusCode + "|" + htmlLength + "|"
                + pCount + "|" + divCount + "|" + spanCount + "|" + imgCount + "|"
                + sanitize(attachedFileOrData == null || attachedFileOrData.isEmpty() ? "Khong co" : attachedFileOrData);
        lines.add(line);
        writeLinesToFile(userFile, lines);
    }

    public synchronized void clearRequestHistory(String username) {
        File userFile = getAccountFile(username);
        List<String> lines = readLinesFromFile(userFile);
        lines.removeIf(line -> line.startsWith("REQUEST_FILE_LOG|"));
        writeLinesToFile(userFile, lines);
    }

    public synchronized List<RequestLogRecord> getRequestLogs(String username) {
        File userFile = getAccountFile(username);
        List<RequestLogRecord> list = new ArrayList<>();
        for (String line : readLinesFromFile(userFile)) {
            if (line.startsWith("REQUEST_FILE_LOG|")) {
                String[] p = line.split("\\|", -1);
                if (p.length >= 13) {
                    try {
                        list.add(new RequestLogRecord(
                                p[1], p[2], p[3], p[4], p[5],
                                Integer.parseInt(p[6]),
                                Integer.parseInt(p[7]),
                                Integer.parseInt(p[8]),
                                Integer.parseInt(p[9]),
                                Integer.parseInt(p[10]),
                                Integer.parseInt(p[11]),
                                p[12]
                        ));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return list;
    }

    public synchronized String readRawAccountFileText(String username) {
        File userFile = getAccountFile(username);
        StringBuilder sb = new StringBuilder();
        for (String line : readLinesFromFile(userFile)) {
            sb.append(line).append("\n");
        }
        return sb.toString();
    }
}