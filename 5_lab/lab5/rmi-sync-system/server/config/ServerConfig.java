package server.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Cấu hình cho từng Server vật lý trong mô hình Active-Active.
 * Có thể nạp từ file .properties hoặc truyền qua tham số dòng lệnh (CLI args).
 */
public class ServerConfig {
    private String serverId;         // Ví dụ: "SERVER_1"
    private String hostIp;           // IP máy hiện tại (dùng cho java.rmi.server.hostname)
    private int registryPort;        // Port RMI Registry (mặc định 1099)
    private int exportPort;          // Port cố định cho UnicastRemoteObject (mặc định 1100)

    private String peerHostIp;       // IP của Server đối tác
    private int peerRegistryPort;    // Port RMI Registry của Server đối tác

    private String dbDirPath;        // Đường dẫn thư mục lưu bản ghi (ví dụ: ./data/server1/db)
    private String queueDirPath;     // Đường dẫn thư mục lưu hàng đợi sync (ví dụ: ./data/server1/queue)
    private int heartbeatIntervalSec;// Chu kỳ kiểm tra kết nối và đẩy hàng đợi (mặc định 3 giây)

    public ServerConfig(String serverId, String hostIp, int registryPort, int exportPort,
                        String peerHostIp, int peerRegistryPort,
                        String dbDirPath, String queueDirPath, int heartbeatIntervalSec) {
        this.serverId = serverId;
        this.hostIp = hostIp;
        this.registryPort = registryPort;
        this.exportPort = exportPort;
        this.peerHostIp = peerHostIp;
        this.peerRegistryPort = peerRegistryPort;
        this.dbDirPath = dbDirPath;
        this.queueDirPath = queueDirPath;
        this.heartbeatIntervalSec = heartbeatIntervalSec;
    }

    /**
     * Đọc cấu hình từ file .properties
     */
    public static ServerConfig fromPropertiesFile(String filePath) throws IOException {
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(filePath)) {
            props.load(fis);
        }
        String serverId = props.getProperty("server.id", "SERVER_1").trim();
        String hostIp = props.getProperty("server.host", "127.0.0.1").trim();
        int registryPort = Integer.parseInt(props.getProperty("server.registry.port", "1099").trim());
        int exportPort = Integer.parseInt(props.getProperty("server.export.port", "1100").trim());

        String peerHostIp = props.getProperty("peer.host", "127.0.0.1").trim();
        int peerRegistryPort = Integer.parseInt(props.getProperty("peer.registry.port", "1099").trim());

        String dbDir = props.getProperty("storage.db.dir", "./data/" + serverId.toLowerCase() + "/db").trim();
        String queueDir = props.getProperty("storage.queue.dir", "./data/" + serverId.toLowerCase() + "/queue").trim();
        int heartbeatSec = Integer.parseInt(props.getProperty("sync.heartbeat.seconds", "3").trim());

        return new ServerConfig(serverId, hostIp, registryPort, exportPort,
                peerHostIp, peerRegistryPort, dbDir, queueDir, heartbeatSec);
    }

    public String getServerId() {
        return serverId;
    }

    public String getHostIp() {
        return hostIp;
    }

    public int getRegistryPort() {
        return registryPort;
    }

    public int getExportPort() {
        return exportPort;
    }

    public String getPeerHostIp() {
        return peerHostIp;
    }

    public int getPeerRegistryPort() {
        return peerRegistryPort;
    }

    public String getDbDirPath() {
        return dbDirPath;
    }

    public String getQueueDirPath() {
        return queueDirPath;
    }

    public int getHeartbeatIntervalSec() {
        return heartbeatIntervalSec;
    }

    @Override
    public String toString() {
        return String.format(
                "ServerConfig[id=%s, host=%s:%d (export=%d), peer=%s:%d, db=%s, queue=%s]",
                serverId, hostIp, registryPort, exportPort, peerHostIp, peerRegistryPort, dbDirPath, queueDirPath
        );
    }
}
