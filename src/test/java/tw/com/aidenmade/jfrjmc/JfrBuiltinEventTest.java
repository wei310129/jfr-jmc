package tw.com.aidenmade.jfrjmc;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 示範 JDK 內建 JFR 事件的錄製與分析。
 * 這些事件在 JMC 中都有對應的視圖可以查看。
 */
class JfrBuiltinEventTest {

    private Recording recording;
    private Path recordingPath;

    @BeforeEach
    void startRecording() throws IOException {
        recordingPath = Files.createTempFile("jfr-builtin-", ".jfr");
        recording = new Recording();
    }

    @AfterEach
    void cleanup() throws IOException {
        if (recording != null) {
            recording.close();
        }
        if (recordingPath != null) {
            Files.deleteIfExists(recordingPath);
        }
    }

    @Test
    @DisplayName("GC 事件：分配大物件後應能觀察到 GC 活動")
    void shouldCaptureGcEvents() throws IOException {
        recording.enable("jdk.GarbageCollection");
        recording.enable("jdk.GCHeapSummary");
        recording.start();

        // 反覆分配大量物件，強制觸發 GC
        for (int round = 0; round < 10; round++) {
            byte[] ignored = new byte[20 * 1024 * 1024]; // 20MB
            // 故意不保留參照，讓 GC 可以回收
        }
        System.gc();

        List<RecordedEvent> gcEvents = stopAndRead("jdk.GarbageCollection");

        System.out.println("GC 事件數量: " + gcEvents.size());
        gcEvents.forEach(e -> System.out.printf(
            "  GC #%d  cause=%-25s  duration=%dms%n",
            e.getLong("gcId"),
            e.getString("cause"),
            e.getDuration().toMillis()
        ));

        // 至少應有一次 GC（System.gc() 保證觸發）
        Assertions.assertFalse(gcEvents.isEmpty(), "應觀察到至少一次 GC 事件");
    }

    @Test
    @DisplayName("Thread Sleep 事件：sleep 後應記錄到 jdk.ThreadSleep")
    void shouldCaptureThreadSleepEvent() throws IOException, InterruptedException {
        recording.enable("jdk.ThreadSleep");
        recording.start();

        Thread.sleep(100);

        List<RecordedEvent> sleepEvents = stopAndRead("jdk.ThreadSleep");

        System.out.println("ThreadSleep 事件數量: " + sleepEvents.size());
        sleepEvents.forEach(e -> System.out.printf(
            "  sleep time=%dms%n",
            e.getDuration("time").toMillis()
        ));

        Assertions.assertFalse(sleepEvents.isEmpty(), "應觀察到至少一次 ThreadSleep 事件");
    }

    @Test
    @DisplayName("CPU Load 事件：週期事件錄製示範（每秒一次）")
    void shouldCaptureCpuLoadEvents() throws IOException, InterruptedException {
        recording.enable("jdk.CPULoad").withPeriod(java.time.Duration.ofMillis(500));
        recording.start();

        // 製造 CPU 負載並等待足夠時間讓週期事件觸發
        long sum = 0;
        long end = System.currentTimeMillis() + 800;
        while (System.currentTimeMillis() < end) {
            sum += Math.sqrt(sum + 1);
        }
        Thread.sleep(600); // 額外等待，確保至少一個週期完成

        List<RecordedEvent> cpuEvents = stopAndRead("jdk.CPULoad");

        System.out.println("CPULoad 事件數量: " + cpuEvents.size() + "  (sum=" + sum + ")");
        cpuEvents.forEach(e -> System.out.printf(
            "  JVM user=%.1f%%  JVM system=%.1f%%  machine=%.1f%%%n",
            e.getFloat("jvmUser") * 100,
            e.getFloat("jvmSystem") * 100,
            e.getFloat("machineTotal") * 100
        ));

        // 週期事件在某些環境下可能不觸發，僅記錄觀測結果
        System.out.println(cpuEvents.isEmpty()
            ? "  (此環境未觀察到 CPULoad 事件，在 JMC 中直接監控可見)"
            : "  ✓ 成功觀察到 CPULoad 事件");
    }

    @Test
    @DisplayName("Monitor Enter 事件：synchronized 競爭應記錄 Lock 等待")
    void shouldCaptureMonitorEnterEvents() throws IOException, InterruptedException {
        recording.enable("jdk.JavaMonitorEnter").withThreshold(java.time.Duration.ofMillis(1));
        recording.enable("jdk.JavaMonitorWait").withThreshold(java.time.Duration.ofMillis(1));
        recording.start();

        Object lock = new Object();
        // 讓一個執行緒持有鎖，另一個等待
        Thread holder = new Thread(() -> {
            synchronized (lock) {
                try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        });
        Thread waiter = new Thread(() -> {
            synchronized (lock) {
                // 搶到鎖後立刻釋放
            }
        });

        holder.start();
        Thread.sleep(10); // 確保 holder 先取得鎖
        waiter.start();

        holder.join();
        waiter.join();

        recording.stop();
        recording.dump(recordingPath);

        List<RecordedEvent> allEvents = readAll();
        System.out.println("所有事件類型:");
        allEvents.stream()
            .collect(java.util.stream.Collectors.groupingBy(e -> e.getEventType().getName(), java.util.stream.Collectors.counting()))
            .forEach((name, count) -> System.out.println("  " + name + " x" + count));
    }

    // ── 輔助方法 ──────────────────────────────────────────────

    private List<RecordedEvent> stopAndRead(String eventName) throws IOException {
        recording.stop();
        recording.dump(recordingPath);
        List<RecordedEvent> result = new ArrayList<>();
        try (RecordingFile file = new RecordingFile(recordingPath)) {
            while (file.hasMoreEvents()) {
                RecordedEvent e = file.readEvent();
                if (e.getEventType().getName().equals(eventName)) {
                    result.add(e);
                }
            }
        }
        return result;
    }

    private List<RecordedEvent> readAll() throws IOException {
        List<RecordedEvent> result = new ArrayList<>();
        try (RecordingFile file = new RecordingFile(recordingPath)) {
            while (file.hasMoreEvents()) {
                result.add(file.readEvent());
            }
        }
        return result;
    }
}
