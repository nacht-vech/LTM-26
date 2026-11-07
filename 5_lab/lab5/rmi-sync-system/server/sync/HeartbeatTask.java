package server.sync;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Tiến trình chạy nền định kỳ (mặc định mỗi 3 giây) để kiểm tra trạng thái sống còn
 * của Server đối tác và kích hoạt đẩy hàng đợi ngoại tuyến (Offline Queue).
 */
public class HeartbeatTask {
    private final PeerSyncEngine syncEngine;
    private final int intervalSeconds;
    private final ScheduledExecutorService scheduler;

    public HeartbeatTask(PeerSyncEngine syncEngine, int intervalSeconds) {
        this.syncEngine = syncEngine;
        this.intervalSeconds = intervalSeconds;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Peer-Heartbeat-Thread");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        scheduler.scheduleWithFixedDelay(
                syncEngine::checkHeartbeatAndFlushQueue,
                2,
                intervalSeconds,
                TimeUnit.SECONDS
        );
        System.out.printf("[HEARTBEAT-START] Đã bật tiến trình Heartbeat định kỳ mỗi %d giây.%n", intervalSeconds);
    }

    public void stop() {
        scheduler.shutdownNow();
    }
}
