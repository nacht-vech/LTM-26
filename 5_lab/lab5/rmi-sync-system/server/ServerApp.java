package server;

import server.config.ServerConfig;
import server.service.ClientServiceImpl;
import server.service.SyncServiceImpl;
import server.storage.FileStorageManager;
import server.storage.SyncQueueManager;
import server.sync.HeartbeatTask;
import server.sync.PeerSyncEngine;

import java.io.File;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

/**
 * Điểm khởi chạy chính của ứng dụng Server.
 * Cách sử dụng:
 * 1. Truyền đường dẫn file cấu hình:
 *    java -cp ... server.ServerApp config/server1.properties
 * 2. Hoặc truyền trực tiếp tham số dòng lệnh:
 *    java -cp ... server.ServerApp SERVER_1 192.168.1.10 1099 1100 192.168.1.11 1099
 */
public class ServerApp {

    public static void main(String[] args) {
        try {
            ServerConfig config = parseConfig(args);

            // Thiết lập bắt buộc cho RMI để stub không bị bind nhầm vào 127.0.0.1 khi gọi qua LAN / VM
            System.setProperty("java.rmi.server.hostname", config.getHostIp());

            System.out.println("==================================================================");
            System.out.println("   KHỞI ĐỘNG JAVA RMI ACTIVE-ACTIVE SERVER: " + config.getServerId());
            System.out.println("==================================================================");
            System.out.println("Cấu hình: " + config);

            // 1. Khởi tạo bộ quản lý lưu trữ File và Hàng đợi Queue
            FileStorageManager storageManager = new FileStorageManager(config.getDbDirPath());
            SyncQueueManager queueManager = new SyncQueueManager(config.getQueueDirPath());

            // 2. Khởi tạo bộ điều phối đồng bộ Peer-to-Peer
            PeerSyncEngine syncEngine = new PeerSyncEngine(config, storageManager, queueManager);

            // 3. Khởi tạo các dịch vụ RMI (sử dụng port cố định config.getExportPort())
            ClientServiceImpl clientService = new ClientServiceImpl(config, storageManager, syncEngine);
            SyncServiceImpl syncService = new SyncServiceImpl(config, storageManager);

            // 4. Tạo RMI Registry và đăng ký (bind) cả 2 dịch vụ
            Registry registry;
            try {
                registry = LocateRegistry.createRegistry(config.getRegistryPort());
                System.out.println("[RMI-REGISTRY] Đã khởi tạo RMI Registry tại cổng " + config.getRegistryPort());
            } catch (Exception e) {
                registry = LocateRegistry.getRegistry(config.getRegistryPort());
                System.out.println("[RMI-REGISTRY] Sử dụng RMI Registry sẵn có tại cổng " + config.getRegistryPort());
            }

            registry.rebind("ClientService", clientService);
            registry.rebind("SyncService", syncService);

            System.out.println("[RMI-BIND] Đã bind thành công 'ClientService' và 'SyncService'.");

            // 5. Khởi chạy tiến trình Heartbeat kiểm tra kết nối đối tác & đẩy Queue
            HeartbeatTask heartbeatTask = new HeartbeatTask(syncEngine, config.getHeartbeatIntervalSec());
            heartbeatTask.start();

            // Đăng ký Shutdown Hook để đóng tài nguyên an toàn
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n[SHUTDOWN] Đang dừng " + config.getServerId() + "...");
                heartbeatTask.stop();
                syncEngine.shutdown();
            }));

            System.out.println(">>> " + config.getServerId() + " ĐÃ SẴN SÀNG PHỤC VỤ CLIENT VÀ ĐỒNG BỘ 2 CHIỀU <<<");

        } catch (Exception e) {
            System.err.println("[FATAL] Lỗi khởi động Server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static ServerConfig parseConfig(String[] args) throws Exception {
        if (args.length == 1 && new File(args[0]).exists()) {
            return ServerConfig.fromPropertiesFile(args[0]);
        }
        if (args.length >= 6) {
            String serverId = args[0];
            String hostIp = args[1];
            int registryPort = Integer.parseInt(args[2]);
            int exportPort = Integer.parseInt(args[3]);
            String peerIp = args[4];
            int peerPort = Integer.parseInt(args[5]);
            String dbDir = "./data/" + serverId.toLowerCase() + "/db";
            String queueDir = "./data/" + serverId.toLowerCase() + "/queue";
            return new ServerConfig(serverId, hostIp, registryPort, exportPort, peerIp, peerPort, dbDir, queueDir, 3);
        }

        // Mặc định nếu chạy thử nghiệm nhanh không truyền tham số (chạy SERVER_1 tại localhost)
        System.out.println("[INFO] Không truyền tham số cấu hình, sử dụng cấu hình mặc định cho SERVER_1 (127.0.0.1:1099 -> Peer 127.0.0.1:2099)");
        return new ServerConfig(
                "SERVER_1", "127.0.0.1", 1099, 1100,
                "127.0.0.1", 2099,
                "./data/server_1/db", "./data/server_1/queue", 3
        );
    }
}
