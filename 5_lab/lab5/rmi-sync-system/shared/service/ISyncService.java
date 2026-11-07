package shared.service;

import shared.model.Record;
import shared.model.SyncAction;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

/**
 * Remote Interface dành riêng cho 2 Server đồng bộ dữ liệu Peer-to-Peer (Active-Active).
 */
public interface ISyncService extends Remote {

    /**
     * Đồng bộ một bản ghi từ Server gửi (senderId) sang Server nhận.
     * Server nhận sẽ kiểm tra senderId để tránh vòng lặp đồng bộ vô hạn (Echo Sync)
     * và đối soát timestamp theo thuật toán Last-Write-Wins (LWW).
     *
     * @param record   Bản ghi cần đồng bộ (bao gồm cả trạng thái isDeleted nếu là lệnh xóa)
     * @param action   Loại hành động (CREATE, UPDATE, DELETE)
     * @param senderId ID của Server gửi lệnh đồng bộ
     * @return true nếu xử lý đồng bộ thành công
     */
    boolean replicateRecord(Record record, SyncAction action, String senderId) throws RemoteException;

    /**
     * Lấy toàn bộ danh sách bản ghi (bao gồm cả bản ghi đã đánh dấu xóa mềm isDeleted = true)
     * để phục vụ đối soát toàn phần khi một Server mới khởi động lại.
     */
    List<Record> getAllRecordsForSync() throws RemoteException;

    /**
     * Kiểm tra kết nối sống còn (Heartbeat) giữa 2 Server.
     *
     * @return ID của Server phản hồi
     */
    String ping() throws RemoteException;
}
