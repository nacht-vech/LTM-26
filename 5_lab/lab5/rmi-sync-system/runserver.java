import server.ServerApp;
import java.nio.charset.StandardCharsets;
import java.io.PrintStream;
import javax.swing.*;

/**
 * File khởi chạy nhanh Server trực tiếp từ nút Run trên VS Code.
 * Đặt tại thư mục gốc (cùng cấp với thư mục shared, server, client).
 */
public class RunServer {

    public static void main(String[] args) {

        try {
            System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }

        if (args.length > 0) {
            ServerApp.main(args);
            return;
        }

        String[] options = {
                "1. Chạy SERVER 1 (config/server1.properties)",
                "2. Chạy SERVER 2 (config/server2.properties)",
                "3. Chạy cả 2 Server cùng lúc (Local Test)"
        };

        int choice = JOptionPane.showOptionDialog(
                null,
                "Chọn chế độ khởi chạy Server:",
                "Khởi động RMI Active-Active Server",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]
        );

        if (choice == 0) {
            ServerApp.main(new String[]{"config/server1.properties"});
        } else if (choice == 1) {
            ServerApp.main(new String[]{"config/server2.properties"});
        } else if (choice == 2) {
            new Thread(() -> ServerApp.main(new String[]{"config/server1.properties"}), "Server-1-Main").start();
            try {
                Thread.sleep(800);
            } catch (InterruptedException ignored) {
            }
            new Thread(() -> ServerApp.main(new String[]{"config/server2.properties"}), "Server-2-Main").start();
        }
    }
}