package server.service;

import server.config.ServerConfig;
import server.storage.FileStorageManager;
import shared.model.Record;
import shared.model.SyncAction;
import shared.service.ISyncService;

import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;

/**
 * Hiện thực Remote Interface `ISyncService` để tiếp nhận dữ liệu đồng bộ từ Server đối tác.
 * Tuân thủ nghiêm ngặt nguyên tắc chống vòng lặp đồng bộ vô hạn (Echo Sync):
 * - Kiểm tra `senderId`, nếu trùng với `serverId` hiện tại thì bỏ qua.
 * - Lưu dữ liệu vào `FileStorageManager` bằng thuật toán Last-Write-Wins (LWW).
 * - Tuyệt đối KHÔNG gọi ngược lại `triggerSyncToPeer`.
 */
public class SyncServiceImpl extends UnicastRemoteObject implements ISyncService {
    private static final long serialVersionUID = 1L;

    private final ServerConfig config;
    private final FileStorageManager storageManager;

    public SyncServiceImpl(ServerConfig config, FileStorageManager storageManager) throws RemoteException {
        super(config.getExportPort());
        this.config = config;
        this.storageManager = storageManager;
    }

    @Override
    public boolean replicateRecord(Record record, SyncAction action, String senderId) throws RemoteException {
        if (record == null) {
            return false;
        }
        // Chống vòng lặp đồng bộ vô hạn (Ping-Pong / Echo Sync)
        if (config.getServerId().equalsIgnoreCase(senderId)) {
            return false;
        }

        try {
            System.out.printf("[PEER-RECV] Nhận đồng bộ từ %s: Action=%s, Record=%s%n",
                    senderId, action, record);
            return storageManager.saveWithLWW(record);
        } catch (IOException e) {
            System.err.println("[PEER-RECV-ERROR] Lỗi lưu bản ghi đồng bộ từ đối tác: " + e.getMessage());
            throw new RemoteException("Lỗi ghi file khi đồng bộ trên " + config.getServerId(), e);
        }
    }

    @Override
    public List<Record> getAllRecordsForSync() throws RemoteException {
        return storageManager.findAllIncludeDeleted();
    }

    @Override
    public String ping() throws RemoteException {
        return config.getServerId();
    }
}
