package tw.com.aidenmade.jfrjmc.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import tw.com.aidenmade.jfrjmc.service.CpuBoundService;
import tw.com.aidenmade.jfrjmc.service.MemoryPressureService;
import tw.com.aidenmade.jfrjmc.service.ThreadContentionService;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/demo")
@RequiredArgsConstructor
public class DemoController {

    private final CpuBoundService cpuBoundService;
    private final MemoryPressureService memoryPressureService;
    private final ThreadContentionService threadContentionService;

    /** CPU 密集：遞迴費氏數列，適合觀察 CPU 熱點 */
    @GetMapping("/cpu/fibonacci/{n}")
    public Map<String, Object> fibonacci(@PathVariable int n) {
        long start = System.currentTimeMillis();
        long result = cpuBoundService.fibonacci(n);
        return Map.of("n", n, "result", result, "elapsedMs", System.currentTimeMillis() - start);
    }

    /** CPU 密集：Sieve of Eratosthenes 質數篩，適合觀察陣列存取模式 */
    @GetMapping("/cpu/primes/{limit}")
    public Map<String, Object> primes(@PathVariable int limit) {
        long start = System.currentTimeMillis();
        List<Integer> primes = cpuBoundService.findPrimes(limit);
        return Map.of("limit", limit, "count", primes.size(), "elapsedMs", System.currentTimeMillis() - start);
    }

    /** 記憶體壓力：大量小物件分配，觀察 GC 行為 */
    @GetMapping("/memory/allocate")
    public Map<String, Object> allocate(@RequestParam(defaultValue = "10000") int items) {
        long start = System.currentTimeMillis();
        int total = memoryPressureService.allocateAndProcess(items);
        return Map.of("items", items, "totalChars", total, "elapsedMs", System.currentTimeMillis() - start);
    }

    /** 記憶體壓力：分配大型 byte array，觀察 heap 使用量 */
    @GetMapping("/memory/large-array")
    public Map<String, Object> largeArray(@RequestParam(defaultValue = "50") int mb) {
        long start = System.currentTimeMillis();
        byte[] data = memoryPressureService.allocateLargeArray(mb);
        return Map.of("allocatedMb", mb, "actualBytes", data.length, "elapsedMs", System.currentTimeMillis() - start);
    }

    /** Thread 爭用：多執行緒爭一把 lock，觀察 Monitor Enter 事件 */
    @GetMapping("/thread/contention")
    public Map<String, Object> contention(
            @RequestParam(defaultValue = "8") int threads,
            @RequestParam(defaultValue = "10000") int iterations) throws InterruptedException {
        long start = System.currentTimeMillis();
        int count = threadContentionService.simulateContention(threads, iterations);
        return Map.of("threads", threads, "iterations", iterations, "count", count, "elapsedMs", System.currentTimeMillis() - start);
    }

    /** I/O 等待：模擬慢速操作，觀察 Thread Sleep 事件 */
    @GetMapping("/thread/slow")
    public CompletableFuture<Map<String, Object>> slow(@RequestParam(defaultValue = "500") long delayMs) {
        long start = System.currentTimeMillis();
        return threadContentionService.simulateSlowOperation(delayMs)
            .thenApply(result -> Map.of("result", result, "elapsedMs", System.currentTimeMillis() - start));
    }
}
