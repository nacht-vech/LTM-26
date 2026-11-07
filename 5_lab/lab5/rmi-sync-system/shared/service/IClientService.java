package shared.service;

import shared.model.Record;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

/**
 * Remote Interface phục vụ các yêu cầu từ Client (chạy trên Máy ảo VM) gọi tới Server.
 */
public interface IClientService extends Remote {

    /**
     * Lấy danh sách toàn bộ các bản ghi chưa bị xóa (isDeleted == false).
     */
    List<Record> getAllRecords() throws RemoteException;

    /**
     * Tìm kiếm một bản ghi theo ID.
     */
    Record getRecordById(String id) throws RemoteException;

    /**
     * Thêm mới hoặc cập nhật bản ghi từ Client.
     * Server nhận request sẽ gắn sourceServerId của mình nếu chưa có, áp dụng LWW lưu cục bộ,
     * sau đó tự động đồng bộ sang Server đối tác (hoặc đẩy vào queue nếu đối tác offline).
     */
    Record saveRecord(Record record) throws RemoteException;

    /**
     * Xóa mềm (Soft Delete) một bản ghi theo ID với timestamp từ Client.
     * Đảm bảo lệnh xóa cũng được đồng bộ theo chuẩn Last-Write-Wins sang Server đối tác.
     */
    boolean deleteRecord(String id, long clientTimestamp) throws RemoteException;

    /**
     * Trả về định danh của Server hiện tại (ví dụ: "SERVER_1" hoặc "SERVER_2") để hiển thị trên UI.
     */
    String getServerInfo() throws RemoteException;
}
