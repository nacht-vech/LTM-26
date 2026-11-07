package shared.model;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;

/**
 * Thực thể dữ liệu chính được đồng bộ giữa các Client và 2 Server.
 * Hỗ trợ giải quyết xung đột theo thuật toán Last-Write-Wins (LWW) dựa trên timestamp
 * và tie-breaker bằng sourceServerId.
 */
public class Record implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;               // UUID hoặc khóa chính duy nhất
    private String title;            // Tiêu đề bản ghi
    private String content;          // Nội dung chi tiết
    private long timestamp;          // System.currentTimeMillis() lúc tạo/cập nhật
    private String sourceServerId;   // "SERVER_1" hoặc "SERVER_2" (nơi tiếp nhận đầu tiên)
    private boolean isDeleted;       // Soft delete (tombstone) để đồng bộ lệnh xóa chính xác

    public Record() {
    }

    public Record(String id, String title, String content, long timestamp, String sourceServerId, boolean isDeleted) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.timestamp = timestamp;
        this.sourceServerId = sourceServerId;
        this.isDeleted = isDeleted;
    }

    /**
     * Kiểm tra xem bản ghi này (incoming) có mới hơn bản ghi hiện có (existing)
     * theo quy tắc Last-Write-Wins (LWW) hay không.
     *
     * @param existing Bản ghi đang lưu trên máy
     * @return true nếu bản ghi này cần được ghi đè lên bản ghi existing
     */
    public boolean isNewerThan(Record existing) {
        if (existing == null) {
            return true;
        }
        if (this.timestamp > existing.getTimestamp()) {
            return true;
        }
        if (this.timestamp < existing.getTimestamp()) {
            return false;
        }
        // Tie-breaker: Nếu trùng mili-giây, so sánh chuỗi sourceServerId để đảm bảo kết quả nhất quán trên cả 2 server
        String myServer = this.sourceServerId != null ? this.sourceServerId : "";
        String otherServer = existing.getSourceServerId() != null ? existing.getSourceServerId() : "";
        return myServer.compareTo(otherServer) >= 0;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getSourceServerId() {
        return sourceServerId;
    }

    public void setSourceServerId(String sourceServerId) {
        this.sourceServerId = sourceServerId;
    }

    public boolean isDeleted() {
        return isDeleted;
    }

    public void setDeleted(boolean deleted) {
        isDeleted = deleted;
    }

    public String getFormattedTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        return sdf.format(new Date(timestamp));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Record record = (Record) o;
        return Objects.equals(id, record.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format("Record{id='%s', title='%s', timestamp=%d (%s), source='%s', deleted=%s}",
                id, title, timestamp, getFormattedTimestamp(), sourceServerId, isDeleted);
    }
}
