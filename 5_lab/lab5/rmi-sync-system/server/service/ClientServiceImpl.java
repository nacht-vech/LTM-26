package server.service;

import server.config.ServerConfig;
import server.storage.FileStorageManager;
import server.sync.PeerSyncEngine;
import shared.model.Record;
import shared.model.SyncAction;
import shared.service.IClientService;

import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;
import java.util.UUID;

/**
 * Hiện thực Remote Interface `IClientService` phục vụ các yêu cầu từ Client (trên máy ảo VM).
 * Khi nhận lệnh thêm/sửa/xóa từ Client:
 * 1. Chuẩn hóa dữ liệu (`id`, `timestamp`, `sourceServerId`).
 * 2. Lưu vào file cục bộ thông qua `FileStorageManager.saveWithLWW`.
 * 3. Nếu lưu thành công, kích hoạt `PeerSyncEngine.triggerSyncToPeer` để đồng bộ sang Server còn lại.
 */
public class ClientServiceImpl extends UnicastRemoteObject implements IClientService {
    private static final long serialVersionUID = 1L;

    private final ServerConfig config;
    private final FileStorageManager storageManager;
    private final PeerSyncEngine syncEngine;

    public ClientServiceImpl(ServerConfig config, FileStorageManager storageManager, PeerSyncEngine syncEngine) throws RemoteException {
        super(config.getExportPort());
        this.config = config;
        this.storageManager = storageManager;
        this.syncEngine = syncEngine;
    }

    @Override
    public List<Record> getAllRecords() throws RemoteException {
        return storageManager.findAllActive();
    }

    @Override
    public Record getRecordById(String id) throws RemoteException {
        return storageManager.findByIdActive(id);
    }

    @Override
    public Record saveRecord(Record record) throws RemoteException {
        if (record == null) {
            throw new RemoteException("Bản ghi không được phép null.");
        }

        boolean isNew = (record.getId() == null || record.getId().trim().isEmpty());
        if (isNew) {
            record.setId(UUID.randomUUID().toString().substring(0, 8));
        }

        if (record.getTimestamp() <= 0) {
            record.setTimestamp(System.currentTimeMillis());
        }
        record.setSourceServerId(config.getServerId());
        record.setDeleted(false);

        SyncAction action = isNew ? SyncAction.CREATE : SyncAction.UPDATE;

        try {
            boolean saved = storageManager.saveWithLWW(record);
            if (saved) {
                // Kích hoạt đồng bộ sang Server đối tác (Active-Active)
                syncEngine.triggerSyncToPeer(record, action);
            }
            return storageManager.findByIdIncludeDeleted(record.getId());
        } catch (IOException e) {
            throw new RemoteException("Lỗi lưu bản ghi trên " + config.getServerId() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public boolean deleteRecord(String id, long clientTimestamp) throws RemoteException {
        if (id == null || id.trim().isEmpty()) {
            return false;
        }

        Record existing = storageManager.findByIdIncludeDeleted(id);
        if (existing == null) {
            return false;
        }

        long deleteTs = clientTimestamp > 0 ? clientTimestamp : System.currentTimeMillis();
        // Đảm bảo lệnh xóa từ client có timestamp lớn hơn bản ghi đang tồn tại nếu đồng hồ cùng máy
        if (deleteTs <= existing.getTimestamp()) {
            deleteTs = existing.getTimestamp() + 1;
        }

        Record tombstone = new Record(
                existing.getId(),
                existing.getTitle(),
                existing.getContent(),
                deleteTs,
                config.getServerId(),
                true
        );

        try {
            boolean saved = storageManager.saveWithLWW(tombstone);
            if (saved) {
                syncEngine.triggerSyncToPeer(tombstone, SyncAction.DELETE);
            }
            return saved;
        } catch (IOException e) {
            throw new RemoteException("Lỗi xóa bản ghi trên " + config.getServerId() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public String getServerInfo() throws RemoteException {
        String peerStatus = syncEngine.isPeerOnline() ? "ONLINE" : "OFFLINE (Queue Mode)";
        return String.format("%s (%s:%d) | Peer: %s",
                config.getServerId(), config.getHostIp(), config.getRegistryPort(), peerStatus);
    }
}
