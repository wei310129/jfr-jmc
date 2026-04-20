package tw.com.aidenmade.jfrjmc.service;

import org.springframework.stereotype.Service;
import tw.com.aidenmade.jfrjmc.jfr.ServiceOperationJfrEvent;

import java.util.ArrayList;
import java.util.List;

@Service
public class CpuBoundService {

    public long fibonacci(int n) {
        var event = new ServiceOperationJfrEvent();
        event.operationName = "fibonacci";
        event.inputSize = n;
        event.begin();
        try {
            long result = fib(n);
            event.result = String.valueOf(result);
            return result;
        } finally {
            event.end();
            event.commit();
        }
    }

    public List<Integer> findPrimes(int limit) {
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
        if (n <= 1) return n;
        return fib(n - 1) + fib(n - 2);
    }

    private List<Integer> sieve(int limit) {
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
