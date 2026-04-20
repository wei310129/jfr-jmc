package tw.com.aidenmade.jfrjmc;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tw.com.aidenmade.jfrjmc.service.CpuBoundService;
import tw.com.aidenmade.jfrjmc.service.MemoryPressureService;
import tw.com.aidenmade.jfrjmc.service.ThreadContentionService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 整合測試：搭配 Spring Context，驗證各 Service 的 JFR 事件錄製。
 * 測試順序：建立錄製 → 呼叫 Service → 停止錄製 → 讀取並驗證事件。
 */
@SpringBootTest
class JfrServiceIntegrationTest {

    @Autowired
    private CpuBoundService cpuBoundService;

    @Autowired
    private MemoryPressureService memoryPressureService;

    @Autowired
    private ThreadContentionService threadContentionService;

    private Recording recording;
    private Path recordingPath;

    @BeforeEach
    void startRecording() throws IOException {
        recordingPath = Files.createTempFile("jfr-integration-", ".jfr");
        recording = new Recording();
        recording.enable("tw.com.aidenmade.ServiceOperation").withStackTrace();
        // 同時啟用標準 JVM 事件
        recording.enable("jdk.GarbageCollection");
        recording.enable("jdk.MonitorEnter");
        recording.enable("jdk.ThreadSleep");
        recording.start();
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

    // ── CPU 測試 ──────────────────────────────────────────────

    @Test
    @DisplayName("fibonacci(30) 應產生帶有正確 inputSize 的 JFR 事件")
    void fibonacci_shouldEmitJfrEvent() throws IOException {
        long result = cpuBoundService.fibonacci(30);

        List<RecordedEvent> events = stopAndRead("tw.com.aidenmade.ServiceOperation");

        RecordedEvent event = findEvent(events, "fibonacci");
        Assertions.assertEquals(30L, event.getLong("inputSize"));
        Assertions.assertEquals(String.valueOf(result), event.getString("result"));
    }

    @Test
    @DisplayName("findPrimes(100000) 應產生帶有質數計數的 JFR 事件")
    void findPrimes_shouldEmitJfrEvent() throws IOException {
        List<Integer> primes = cpuBoundService.findPrimes(100_000);

        List<RecordedEvent> events = stopAndRead("tw.com.aidenmade.ServiceOperation");

        RecordedEvent event = findEvent(events, "findPrimes");
        Assertions.assertEquals(100_000L, event.getLong("inputSize"));
        Assertions.assertEquals("count=" + primes.size(), event.getString("result"));
    }

    // ── 記憶體測試 ────────────────────────────────────────────

    @Test
    @DisplayName("allocateAndProcess 應產生 JFR 事件，記錄物件分配工作")
    void allocateAndProcess_shouldEmitJfrEvent() throws IOException {
        int total = memoryPressureService.allocateAndProcess(5000);

        List<RecordedEvent> events = stopAndRead("tw.com.aidenmade.ServiceOperation");

        RecordedEvent event = findEvent(events, "allocateAndProcess");
        Assertions.assertEquals(5000L, event.getLong("inputSize"));
        Assertions.assertEquals("totalChars=" + total, event.getString("result"));
    }

    @Test
    @DisplayName("allocateLargeArray(10MB) 應產生 JFR 事件，記錄大型陣列分配")
    void allocateLargeArray_shouldEmitJfrEvent() throws IOException {
        memoryPressureService.allocateLargeArray(10);

        List<RecordedEvent> events = stopAndRead("tw.com.aidenmade.ServiceOperation");

        RecordedEvent event = findEvent(events, "allocateLargeArray");
        Assertions.assertEquals(10L, event.getLong("inputSize"));
        Assertions.assertEquals("allocated=10MB", event.getString("result"));
    }

    // ── Thread 測試 ──────────────────────────────────────────

    @Test
    @DisplayName("simulateContention 應產生 JFR 事件，計數等於 threads × iterations")
    void simulateContention_shouldEmitJfrEvent() throws IOException, InterruptedException {
        int threads = 4;
        int iterations = 1000;
        int count = threadContentionService.simulateContention(threads, iterations);

        List<RecordedEvent> events = stopAndRead("tw.com.aidenmade.ServiceOperation");

        RecordedEvent event = findEvent(events, "simulateContention");
        Assertions.assertEquals((long) threads * iterations, event.getLong("inputSize"));
        Assertions.assertEquals("count=" + count, event.getString("result"));
        Assertions.assertEquals(threads * iterations, count);
    }

    @Test
    @DisplayName("simulateSlowOperation 應完成並產生 JFR 事件")
    void simulateSlowOperation_shouldEmitJfrEvent() throws IOException {
        String result = threadContentionService.simulateSlowOperation(100).join();

        List<RecordedEvent> events = stopAndRead("tw.com.aidenmade.ServiceOperation");

        RecordedEvent event = findEvent(events, "slowOperation");
        Assertions.assertEquals(100L, event.getLong("inputSize"));
        Assertions.assertEquals("completed", event.getString("result"));
        Assertions.assertEquals("completed after 100ms", result);
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

    private RecordedEvent findEvent(List<RecordedEvent> events, String operationName) {
        return events.stream()
            .filter(e -> operationName.equals(e.getString("operationName")))
            .findFirst()
            .orElseThrow(() -> new AssertionError("找不到 operationName=" + operationName + " 的事件"));
    }
}
