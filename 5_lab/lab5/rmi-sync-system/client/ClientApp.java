package client;

import client.controller.MainController;
import client.model.ClientModel;
import client.service.RmiClientManager;
import client.view.MainFrame;

import javax.swing.*;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Điểm khởi chạy ứng dụng Client (Java Swing) trên Máy ảo (VM).
 * Cách chạy:
 * 1. Qua file cấu hình:
 *    java -cp ... client.ClientApp config/client.properties
 * 2. Hoặc truyền tham số dòng lệnh:
 *    java -cp ... client.ClientApp 192.168.1.10 1099 192.168.1.11 1099
 */
public class ClientApp {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }

            List<RmiClientManager.ServerEndpoint> endpoints = parseEndpoints(args);
            ClientModel model = new ClientModel();
            RmiClientManager rmiManager = new RmiClientManager(endpoints);
            MainFrame frame = new MainFrame(model, endpoints);
            new MainController(model, frame, rmiManager);
            frame.setVisible(true);
        });
    }

    private static List<RmiClientManager.ServerEndpoint> parseEndpoints(String[] args) {
        List<RmiClientManager.ServerEndpoint> list = new ArrayList<>();
        try {
            if (args.length == 1 && new File(args[0]).exists()) {
                Properties props = new Properties();
                try (FileInputStream fis = new FileInputStream(args[0])) {
                    props.load(fis);
                }
                String s1Host = props.getProperty("server1.host", "127.0.0.1").trim();
                int s1Port = Integer.parseInt(props.getProperty("server1.port", "1099").trim());
                String s2Host = props.getProperty("server2.host", "127.0.0.1").trim();
                int s2Port = Integer.parseInt(props.getProperty("server2.port", "2099").trim());

                list.add(new RmiClientManager.ServerEndpoint("SERVER_1", s1Host, s1Port));
                list.add(new RmiClientManager.ServerEndpoint("SERVER_2", s2Host, s2Port));
                return list;
            }

            if (args.length >= 4) {
                list.add(new RmiClientManager.ServerEndpoint("SERVER_1", args[0], Integer.parseInt(args[1])));
                list.add(new RmiClientManager.ServerEndpoint("SERVER_2", args[2], Integer.parseInt(args[3])));
                return list;
            }
        } catch (Exception e) {
            System.err.println("[CLIENT-CONFIG-WARN] Lỗi đọc tham số cấu hình, dùng cấu hình mặc định: " + e.getMessage());
        }

        // Mặc định cho môi trường test local (Server 1 cổng 1099, Server 2 cổng 2099)
        list.add(new RmiClientManager.ServerEndpoint("SERVER_1", "127.0.0.1", 1099));
        list.add(new RmiClientManager.ServerEndpoint("SERVER_2", "127.0.0.1", 2099));
        return list;
    }
}
