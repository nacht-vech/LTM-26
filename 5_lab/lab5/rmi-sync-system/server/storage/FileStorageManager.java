package server.storage;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import shared.model.Record;

/**
 * Quản lý đọc/ghi dữ liệu bản ghi xuống hệ thống thư mục/file cục bộ (`data/db/`).
 * Đảm bảo:
 * 1. An toàn đa luồng với khóa ReentrantReadWriteLock theo từng recordId.
 * 2. Ghi file nguyên tử (Atomic Move từ file .tmp sang .dat) tránh hỏng file khi mất điện/tắt đột ngột.
 * 3. Tích hợp sẵn quy tắc phân giải xung đột Last-Write-Wins (LWW).
 */
public class FileStorageManager {
    private final Path dbDirectory;
    private final ConcurrentHashMap<String, ReentrantReadWriteLock> recordLocks = new ConcurrentHashMap<>();

    public FileStorageManager(String dbDirPath) throws IOException {
        this.dbDirectory = Paths.get(dbDirPath);
        if (!Files.exists(this.dbDirectory)) {
            Files.createDirectories(this.dbDirectory);
        }
    }

    private ReentrantReadWriteLock getLock(String recordId) {
        return recordLocks.computeIfAbsent(recordId, k -> new ReentrantReadWriteLock());
    }

    private Path getRecordFilePath(String recordId) {
        String safeId = recordId.replaceAll("[^a-zA-Z0-9_-]", "_");
        return dbDirectory.resolve("rec-" + safeId + ".dat");
    }

    private Path getTempFilePath(String recordId) {
        String safeId = recordId.replaceAll("[^a-zA-Z0-9_-]", "_");
        return dbDirectory.resolve("rec-" + safeId + ".dat.tmp");
    }

    /**
     * Lưu hoặc cập nhật bản ghi theo quy tắc Last-Write-Wins (LWW).
     *
     * @param incoming Bản ghi mới nhận được
     * @return true nếu bản ghi được chấp nhận và lưu xuống ổ cứng;
     *         false nếu bị từ chối do bản ghi hiện có trên máy mới hơn.
     */
    public boolean saveWithLWW(Record incoming) throws IOException {
        if (incoming == null || incoming.getId() == null || incoming.getId().trim().isEmpty()) {
            throw new IllegalArgumentException("Record hoặc Record ID không hợp lệ.");
        }

        ReentrantReadWriteLock.WriteLock writeLock = getLock(incoming.getId()).writeLock();
        writeLock.lock();
        try {
            Record existing = readRecordInternal(incoming.getId());
            if (existing != null && !incoming.isNewerThan(existing)) {
                System.out.printf("[LWW-REJECT] Bỏ qua bản ghi ID=%s (incoming ts=%d <= existing ts=%d)%n",
                        incoming.getId(), incoming.getTimestamp(), existing.getTimestamp());
                return false;
            }

            writeRecordAtomic(incoming);
            System.out.printf("[LWW-ACCEPT] Đã lưu bản ghi ID=%s, Title='%s', ts=%d, deleted=%s, source=%s%n",
                    incoming.getId(), incoming.getTitle(), incoming.getTimestamp(),
                    incoming.isDeleted(), incoming.getSourceServerId());
            return true;
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Đọc một bản ghi theo ID (trả về cả bản ghi đã đánh dấu xóa mềm isDeleted=true).
     */
    public Record findByIdIncludeDeleted(String recordId) {
        ReentrantReadWriteLock.ReadLock readLock = getLock(recordId).readLock();
        readLock.lock();
        try {
            return readRecordInternal(recordId);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Đọc một bản ghi đang hoạt động (isDeleted == false) cho Client.
     */
    public Record findByIdActive(String recordId) {
        Record rec = findByIdIncludeDeleted(recordId);
        if (rec != null && !rec.isDeleted()) {
            return rec;
        }
        return null;
    }

    /**
     * Lấy danh sách các bản ghi đang hoạt động (isDeleted == false) để hiển thị trên Client.
     */
    public List<Record> findAllActive() {
        List<Record> all = findAllIncludeDeleted();
        List<Record> activeList = new ArrayList<>();
        for (Record r : all) {
            if (!r.isDeleted()) {
                activeList.add(r);
            }
        }
        activeList.sort(Comparator.comparingLong(Record::getTimestamp).reversed());
        return activeList;
    }

    /**
     * Lấy toàn bộ bản ghi (kể cả isDeleted == true) để đồng bộ giữa 2 Server.
     */
    public List<Record> findAllIncludeDeleted() {
        List<Record> result = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dbDirectory, "rec-*.dat")) {
            for (Path entry : stream) {
                try (ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(Files.newInputStream(entry)))) {
                    Object obj = ois.readObject();
                    if (obj instanceof Record rec) {
                        result.add(rec);
                    }
                } catch (Exception e) {
                    System.err.println("[STORAGE-WARN] Lỗi đọc file " + entry.getFileName() + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("[STORAGE-ERROR] Không thể quét thư mục DB: " + e.getMessage());
        }
        result.sort(Comparator.comparingLong(Record::getTimestamp));
        return result;
    }

    /**
     * Đọc trực tiếp từ đĩa (hàm nội bộ, gọi bên trong vùng đã có Lock).
     */
    private Record readRecordInternal(String recordId) {
        Path filePath = getRecordFilePath(recordId);
        if (!Files.exists(filePath)) {
            return null;
        }
        try (ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(Files.newInputStream(filePath)))) {
            return (Record) ois.readObject();
        } catch (Exception e) {
            System.err.println("[STORAGE-ERROR] Lỗi đọc bản ghi " + recordId + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Ghi dữ liệu ra file tạm (.tmp) sau đó di chuyển nguyên tử (ATOMIC_MOVE) đè lên file chính (.dat).
     */
    private void writeRecordAtomic(Record record) throws IOException {
        Path tempPath = getTempFilePath(record.getId());
        Path targetPath = getRecordFilePath(record.getId());

        try (ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(Files.newOutputStream(tempPath)))) {
            oos.writeObject(record);
            oos.flush();
        }

        try {
            Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
