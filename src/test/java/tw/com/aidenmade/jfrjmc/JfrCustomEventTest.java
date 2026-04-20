package tw.com.aidenmade.jfrjmc;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.*;
import tw.com.aidenmade.jfrjmc.jfr.ServiceOperationJfrEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 示範如何用程式碼啟動 JFR 錄製，並驗證自定義事件是否被記錄。
 * 不需要 Spring Context，直接測試 JFR API。
 */
class JfrCustomEventTest {

    private Recording recording;
    private Path recordingPath;

    @BeforeEach
    void startRecording() throws IOException {
        recordingPath = Files.createTempFile("jfr-custom-event-", ".jfr");
        recording = new Recording();
        recording.enable("tw.com.aidenmade.ServiceOperation").withStackTrace();
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

    @Test
    @DisplayName("自定義 JFR 事件應被正確錄製並可讀取")
    void customEventShouldBeRecorded() throws IOException {
        // 模擬一個業務操作，並發出自定義 JFR 事件
        var event = new ServiceOperationJfrEvent();
        event.operationName = "testOperation";
        event.inputSize = 42L;
        event.begin();
        // ... 模擬業務邏輯 ...
        event.result = "success";
        event.end();
        event.commit();

        List<RecordedEvent> events = stopAndReadEvents("tw.com.aidenmade.ServiceOperation");

        Assertions.assertFalse(events.isEmpty(), "應至少有一個 ServiceOperation 事件");

        RecordedEvent recorded = events.stream()
            .filter(e -> "testOperation".equals(e.getString("operationName")))
            .findFirst()
            .orElseThrow(() -> new AssertionError("找不到 testOperation 事件"));

        Assertions.assertEquals(42L, recorded.getLong("inputSize"));
        Assertions.assertEquals("success", recorded.getString("result"));
        Assertions.assertFalse(recorded.getDuration().isNegative(), "事件持續時間不應為負值");
    }

    @Test
    @DisplayName("JFR 事件應記錄正確的持續時間")
    void eventShouldRecordDuration() throws IOException {
        var event = new ServiceOperationJfrEvent();
        event.operationName = "timedOperation";
        event.inputSize = 0;
        event.begin();

        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        event.result = "done";
        event.end();
        event.commit();

        List<RecordedEvent> events = stopAndReadEvents("tw.com.aidenmade.ServiceOperation");

        RecordedEvent recorded = events.stream()
            .filter(e -> "timedOperation".equals(e.getString("operationName")))
            .findFirst()
            .orElseThrow();

        long durationMs = recorded.getDuration().toMillis();
        Assertions.assertTrue(durationMs >= 40, "持續時間應 >= 40ms，實際: " + durationMs + "ms");
    }

    @Test
    @DisplayName("多個事件應全部被錄製")
    void multipleEventsShouldBeRecorded() throws IOException {
        String[] operations = {"op-A", "op-B", "op-C"};

        for (int i = 0; i < operations.length; i++) {
            var event = new ServiceOperationJfrEvent();
            event.operationName = operations[i];
            event.inputSize = i + 1;
            event.result = "result-" + i;
            event.begin();
            event.end();
            event.commit();
        }

        List<RecordedEvent> events = stopAndReadEvents("tw.com.aidenmade.ServiceOperation");

        long matchCount = events.stream()
            .filter(e -> e.getString("operationName").startsWith("op-"))
            .count();

        Assertions.assertEquals(3, matchCount, "應錄製到 3 個 op-* 事件");
    }

    private List<RecordedEvent> stopAndReadEvents(String eventName) throws IOException {
        recording.stop();
        recording.dump(recordingPath);

        List<RecordedEvent> result = new ArrayList<>();
        try (RecordingFile file = new RecordingFile(recordingPath)) {
            while (file.hasMoreEvents()) {
                RecordedEvent event = file.readEvent();
                if (event.getEventType().getName().equals(eventName)) {
                    result.add(event);
                }
            }
        }
        return result;
    }
}
