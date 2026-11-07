package server.storage;

import shared.model.Record;
import shared.model.SyncAction;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Quản lý hàng đợi ngoại tuyến (`data/queue/`) khi Server đối tác mất kết nối.
 * Mỗi tác vụ đồng bộ chưa gửi được sẽ được lưu thành 1 file `.sync` theo thứ tự thời gian.
 */
public class SyncQueueManager {

    public static class QueuedSyncItem implements Serializable {
        private static final long serialVersionUID = 1L;

        private final Record record;
        private final SyncAction action;
        private final long queuedAt;
        private transient Path filePath;

        public QueuedSyncItem(Record record, SyncAction action, long queuedAt) {
            this.record = record;
            this.action = action;
            this.queuedAt = queuedAt;
        }

        public Record getRecord() {
            return record;
        }

        public SyncAction getAction() {
            return action;
        }

        public long getQueuedAt() {
            return queuedAt;
        }

        public Path getFilePath() {
            return filePath;
        }

        public void setFilePath(Path filePath) {
            this.filePath = filePath;
        }
    }

    private final Path queueDirectory;
    private final AtomicLong sequenceGenerator = new AtomicLong(0);

    public SyncQueueManager(String queueDirPath) throws IOException {
        this.queueDirectory = Paths.get(queueDirPath);
        if (!Files.exists(this.queueDirectory)) {
            Files.createDirectories(this.queueDirectory);
        }
    }

    /**
     * Đẩy một bản ghi vào hàng đợi file khi Server đối tác không phản hồi.
     */
    public synchronized void enqueue(Record record, SyncAction action) {
        long now = System.currentTimeMillis();
        long seq = sequenceGenerator.incrementAndGet();
        String fileName = String.format("sync-%d-%05d.sync", now, seq);
        Path tempFile = queueDirectory.resolve(fileName + ".tmp");
        Path finalFile = queueDirectory.resolve(fileName);

        QueuedSyncItem item = new QueuedSyncItem(record, action, now);
        try (ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(Files.newOutputStream(tempFile)))) {
            oos.writeObject(item);
            oos.flush();
        } catch (IOException e) {
            System.err.println("[QUEUE-ERROR] Không thể ghi file hàng đợi: " + e.getMessage());
            return;
        }

        try {
            Files.move(tempFile, finalFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            System.out.printf("[QUEUE-ENQUEUED] Đã lưu hàng đợi ngoại tuyến: %s (ID=%s, Action=%s)%n",
                    fileName, record.getId(), action);
        } catch (IOException e) {
            System.err.println("[QUEUE-ERROR] Không thể hoàn tất file hàng đợi: " + e.getMessage());
        }
    }

    /**
     * Đọc danh sách các tác vụ đang chờ trong thư mục queue theo đúng thứ tự thời gian.
     */
    public synchronized List<QueuedSyncItem> getPendingItems() {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(queueDirectory, "sync-*.sync")) {
            for (Path entry : stream) {
                files.add(entry);
            }
        } catch (IOException e) {
            System.err.println("[QUEUE-ERROR] Lỗi đọc thư mục queue: " + e.getMessage());
            return new ArrayList<>();
        }

        files.sort(Comparator.comparing(p -> p.getFileName().toString()));

        List<QueuedSyncItem> items = new ArrayList<>();
        for (Path file : files) {
            try (ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
                Object obj = ois.readObject();
                if (obj instanceof QueuedSyncItem) {
                    QueuedSyncItem item = (QueuedSyncItem) obj;
                    item.setFilePath(file);
                    items.add(item);
                }
            } catch (Exception e) {
                System.err.println("[QUEUE-WARN] File queue lỗi, tiến hành xóa bỏ " + file.getFileName() + ": " + e.getMessage());
                removeQueueFile(file);
            }
        }
        return items;
    }

    /**
     * Xóa file khỏi thư mục queue sau khi đã đồng bộ bù thành công sang đối tác.
     */
    public synchronized void removeQueueFile(Path filePath) {
        if (filePath != null) {
            try {
                Files.deleteIfExists(filePath);
            } catch (IOException e) {
                System.err.println("[QUEUE-ERROR] Không thể xóa file queue " + filePath.getFileName() + ": " + e.getMessage());
            }
        }
    }

    public int countPendingItems() {
        int count = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(queueDirectory, "sync-*.sync")) {
            for (Path ignored : stream) {
                count++;
            }
        } catch (IOException ignored) {
        }
        return count;
    }
}
