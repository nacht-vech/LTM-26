package server.sync;

import server.config.ServerConfig;
import server.storage.FileStorageManager;
import server.storage.SyncQueueManager;
import shared.model.Record;
import shared.model.SyncAction;
import shared.service.ISyncService;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Bộ điều phối đồng bộ Peer-to-Peer giữa 2 Server.
 * - Quản lý stub kết nối tới `ISyncService` của Server đối tác.
 * - Thực hiện gửi lệnh đồng bộ bất đồng bộ để không làm chậm phản hồi của Client.
 * - Tự động lưu vào `SyncQueueManager` nếu đối tác đang offline.
 * - Đẩy toàn bộ hàng đợi tồn đọng (Flush Queue) khi đối tác online trở lại.
 */
public class PeerSyncEngine {
    private final ServerConfig config;
    private final FileStorageManager storageManager;
    private final SyncQueueManager queueManager;
    private final ExecutorService asyncSyncExecutor;

    private volatile ISyncService peerSyncStub;
    private volatile boolean peerOnline = false;

    public PeerSyncEngine(ServerConfig config, FileStorageManager storageManager, SyncQueueManager queueManager) {
        this.config = config;
        this.storageManager = storageManager;
        this.queueManager = queueManager;
        this.asyncSyncExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Async-Peer-Sync-Worker");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Gửi bản ghi sang Server đối tác (chạy bất đồng bộ).
     * Nếu lỗi kết nối RMI, tự động ghi vào thư mục `data/queue/`.
     */
    public void triggerSyncToPeer(Record record, SyncAction action) {
        asyncSyncExecutor.submit(() -> {
            try {
                ISyncService stub = getOrLookupPeerStub();
                stub.replicateRecord(record, action, config.getServerId());
                if (!peerOnline) {
                    peerOnline = true;
                    System.out.println("[SYNC-INFO] Kết nối tới Server đối tác đã thông suốt.");
                }
                System.out.printf("[SYNC-SUCCESS] Đã đồng bộ bản ghi ID=%s (%s) sang đối tác %s:%d%n",
                        record.getId(), action, config.getPeerHostIp(), config.getPeerRegistryPort());
            } catch (Exception e) {
                peerOnline = false;
                peerSyncStub = null;
                System.err.printf("[SYNC-OFFLINE] Không thể kết nối Server đối tác (%s:%d). Đưa bản ghi ID=%s vào Queue.%n",
                        config.getPeerHostIp(), config.getPeerRegistryPort(), record.getId());
                queueManager.enqueue(record, action);
            }
        });
    }

    /**
     * Kiểm tra kết nối tới đối tác và đẩy toàn bộ các file tồn đọng trong `data/queue/` sang đối tác.
     * Được gọi định kỳ bởi `HeartbeatTask`.
     */
    public synchronized void checkHeartbeatAndFlushQueue() {
        try {
            ISyncService stub = getOrLookupPeerStub();
            String peerId = stub.ping();
            if (!peerOnline) {
                peerOnline = true;
                System.out.printf("[HEARTBEAT] Đã kết nối thành công với Server đối tác: %s (%s:%d)%n",
                        peerId, config.getPeerHostIp(), config.getPeerRegistryPort());
                // Khi mới kết nối lại, kéo dữ liệu từ đối tác về để bù các thay đổi nếu bản thân vừa mới khởi động
                pullMissingRecordsFromPeer(stub);
            }

            // Đẩy toàn bộ các bản ghi trong hàng đợi ngoại tuyến sang đối tác
            List<SyncQueueManager.QueuedSyncItem> pendingItems = queueManager.getPendingItems();
            if (!pendingItems.isEmpty()) {
                System.out.printf("[QUEUE-FLUSH] Phát hiện %d tác vụ tồn đọng. Bắt đầu đồng bộ bù sang %s...%n",
                        pendingItems.size(), peerId);
                for (SyncQueueManager.QueuedSyncItem item : pendingItems) {
                    try {
                        stub.replicateRecord(item.getRecord(), item.getAction(), config.getServerId());
                        queueManager.removeQueueFile(item.getFilePath());
                        System.out.printf("[QUEUE-FLUSHED] Đã đẩy bù thành công bản ghi ID=%s%n",
                                item.getRecord().getId());
                    } catch (Exception ex) {
                        System.err.println("[QUEUE-FLUSH-STOP] Mất kết nối khi đang đẩy bù: " + ex.getMessage());
                        peerOnline = false;
                        peerSyncStub = null;
                        break;
                    }
                }
            }
        } catch (Exception e) {
            if (peerOnline) {
                System.err.printf("[HEARTBEAT-WARN] Mất kết nối tới Server đối tác (%s:%d). Chuyển sang chế độ chờ Queue.%n",
                        config.getPeerHostIp(), config.getPeerRegistryPort());
            }
            peerOnline = false;
            peerSyncStub = null;
        }
    }

    /**
     * Khi Server vừa khởi động hoặc mới kết nối lại với đối tác,
     * chủ động lấy danh sách bản ghi từ đối tác và đối soát LWW để không bỏ sót dữ liệu.
     */
    private void pullMissingRecordsFromPeer(ISyncService stub) {
        try {
            List<Record> peerRecords = stub.getAllRecordsForSync();
            int updatedCount = 0;
            for (Record peerRecord : peerRecords) {
                if (storageManager.saveWithLWW(peerRecord)) {
                    updatedCount++;
                }
            }
            if (updatedCount > 0) {
                System.out.printf("[INITIAL-SYNC] Đã đồng bộ về %d bản ghi mới hơn từ Server đối tác.%n", updatedCount);
            }
        } catch (Exception e) {
            System.err.println("[INITIAL-SYNC-WARN] Không thể kéo dữ liệu đối soát từ đối tác: " + e.getMessage());
        }
    }

    private ISyncService getOrLookupPeerStub() throws Exception {
        if (peerSyncStub != null) {
            return peerSyncStub;
        }
        Registry registry = LocateRegistry.getRegistry(config.getPeerHostIp(), config.getPeerRegistryPort());
        peerSyncStub = (ISyncService) registry.lookup("SyncService");
        return peerSyncStub;
    }

    public boolean isPeerOnline() {
        return peerOnline;
    }

    public void shutdown() {
        asyncSyncExecutor.shutdownNow();
    }
}
