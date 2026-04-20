package tw.com.aidenmade.jfrjmc.service;

import org.springframework.stereotype.Service;
import tw.com.aidenmade.jfrjmc.jfr.ServiceOperationJfrEvent;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ThreadContentionService {

    private final Object sharedLock = new Object();
    private final AtomicInteger counter = new AtomicInteger(0);

    public int simulateContention(int threadCount, int iterationsPerThread) throws InterruptedException {
        var event = new ServiceOperationJfrEvent();
        event.operationName = "simulateContention";
        event.inputSize = (long) threadCount * iterationsPerThread;
        event.begin();
        try {
            counter.set(0);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);

            for (int t = 0; t < threadCount; t++) {
                executor.submit(() -> {
                    for (int i = 0; i < iterationsPerThread; i++) {
                        synchronized (sharedLock) {
                            counter.incrementAndGet();
                        }
                    }
                    latch.countDown();
                });
            }

            latch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            int result = counter.get();
            event.result = "count=" + result;
            return result;
        } finally {
            event.end();
            event.commit();
        }
    }

    public CompletableFuture<String> simulateSlowOperation(long delayMs) {
        return CompletableFuture.supplyAsync(() -> {
            var event = new ServiceOperationJfrEvent();
            event.operationName = "slowOperation";
            event.inputSize = delayMs;
            event.begin();
            try {
                Thread.sleep(delayMs);
                event.result = "completed";
                return "completed after " + delayMs + "ms";
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                event.result = "interrupted";
                return "interrupted";
            } finally {
                event.end();
                event.commit();
            }
        });
    }
}
