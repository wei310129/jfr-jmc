package tw.com.aidenmade.jfrjmc.service;

import org.springframework.stereotype.Service;
import tw.com.aidenmade.jfrjmc.jfr.ServiceOperationJfrEvent;

import java.util.List;
import java.util.stream.IntStream;

@Service
public class MemoryPressureService {

    public int allocateAndProcess(int itemCount) {
        // 以統一事件模型記錄這次配置工作，方便和 CPU/Thread 類操作放在同圖比較。
        var event = new ServiceOperationJfrEvent();
        event.operationName = "allocateAndProcess";
        // inputSize 對應本次要建立的物件數量，通常與 GC 壓力呈正相關。
        event.inputSize = itemCount;
        event.begin();
        try {
            // 連續建立大量字串（含 repeat）會產生許多短命物件，容易觸發 young GC。
            List<String> items = IntStream.range(0, itemCount)
                .mapToObj(i -> "item-" + i + "-" + "x".repeat(100))
                .toList();

            int total = items.stream().mapToInt(String::length).sum();
            // result 留摘要，避免把完整資料寫進 JFR 事件。
            event.result = "totalChars=" + total;
            return total;
        } finally {
            event.end();
            event.commit();
        }
    }

    public byte[] allocateLargeArray(int megabytes) {
        // 記錄大物件配置時間，可在 JMC 對照 GC Pause 與 Heap 變化。
        var event = new ServiceOperationJfrEvent();
        event.operationName = "allocateLargeArray";
        event.inputSize = megabytes;
        event.begin();
        try {
            // 大型連續陣列可能進入 old/tenured 區域，對堆使用量影響明顯。
            byte[] data = new byte[megabytes * 1024 * 1024];
            // 逐頁觸碰可迫使實體頁面配置，讓記憶體壓力在監控上更可見。
            for (int i = 0; i < data.length; i += 4096) {
                data[i] = (byte) i;
            }
            event.result = "allocated=" + megabytes + "MB";
            return data;
        } finally {
            event.end();
            event.commit();
        }
    }
}
