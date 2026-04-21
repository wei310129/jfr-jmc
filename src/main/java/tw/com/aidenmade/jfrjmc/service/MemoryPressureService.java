package tw.com.aidenmade.jfrjmc.service;

import org.springframework.stereotype.Service;
import tw.com.aidenmade.jfrjmc.jfr.ServiceOperationJfrEvent;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class MemoryPressureService {

    public int allocateAndProcess(int itemCount) {
        var event = new ServiceOperationJfrEvent();
        event.operationName = "allocateAndProcess";
        event.inputSize = itemCount;
        event.begin();
        try {
            List<String> items = IntStream.range(0, itemCount)
                .mapToObj(i -> "item-" + i + "-" + "x".repeat(100))
                .toList();

            int total = items.stream().mapToInt(String::length).sum();
            event.result = "totalChars=" + total;
            return total;
        } finally {
            event.end();
            event.commit();
        }
    }

    public byte[] allocateLargeArray(int megabytes) {
        var event = new ServiceOperationJfrEvent();
        event.operationName = "allocateLargeArray";
        event.inputSize = megabytes;
        event.begin();
        try {
            byte[] data = new byte[megabytes * 1024 * 1024];
            // Touch every OS page to trigger actual physical allocation
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
