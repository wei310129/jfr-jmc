package tw.com.aidenmade.jfrjmc.service;

import org.springframework.stereotype.Service;
import tw.com.aidenmade.jfrjmc.jfr.ServiceOperationJfrEvent;

import java.util.ArrayList;
import java.util.List;

@Service
public class CpuBoundService {

    public long fibonacci(int n) {
        // 以 JFR 事件包住整段業務時間，便於在 JMC 直接看單次呼叫成本。
        var event = new ServiceOperationJfrEvent();
        event.operationName = "fibonacci";
        // inputSize 記錄輸入規模，後續可比較 n 變大時 duration 的成長曲線。
        event.inputSize = n;
        event.begin();
        try {
            long result = fib(n);
            // result 只放摘要值，避免把大量資料塞入事件。
            event.result = String.valueOf(result);
            return result;
        } finally {
            event.end();
            event.commit();
        }
    }

    public List<Integer> findPrimes(int limit) {
        // 與 fibonacci 相同模式：統一事件生命週期，讓不同 service 可共用查詢維度。
        var event = new ServiceOperationJfrEvent();
        event.operationName = "findPrimes";
        event.inputSize = limit;
        event.begin();
        try {
            List<Integer> primes = sieve(limit);
            event.result = "count=" + primes.size();
            return primes;
        } finally {
            event.end();
            event.commit();
        }
    }

    private long fib(int n) {
        // 遞迴呼叫會放大 CPU 計算量，是觀察 hot method / stack trace 的典型範例。
        if (n <= 1) return n;
        return fib(n - 1) + fib(n - 2);
    }

    private List<Integer> sieve(int limit) {
        // 這裡有陣列與清單配置，但主要負載仍是迴圈計算與記憶體存取，不是短命物件洪峰。
        boolean[] composite = new boolean[limit + 1];
        List<Integer> primes = new ArrayList<>();
        for (int i = 2; i <= limit; i++) {
            if (!composite[i]) {
                primes.add(i);
                for (long j = (long) i * i; j <= limit; j += i) {
                    composite[(int) j] = true;
                }
            }
        }
        return primes;
    }
}
