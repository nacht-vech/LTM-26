import client.ClientApp;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * File khởi chạy nhanh giao diện Client (Java Swing) trực tiếp từ nút Run trên VS Code.
 * Đặt tại thư mục gốc (cùng cấp với thư mục shared, server, client).
 */
public class RunClient {

    public static void main(String[] args) {

        try {
            System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }

        if (args.length > 0) {
            ClientApp.main(args);
        } else {
            ClientApp.main(new String[]{"config/client.properties"});
        }
    }
}