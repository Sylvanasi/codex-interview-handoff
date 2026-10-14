package com.wufeng.lab;

import com.wufeng.lab.pending.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.assertThat;

class PendingBudgetTest {
    private ThreadPoolExecutor pool(int size, int queue, CountingAbortPolicy policy) {
        return new ThreadPoolExecutor(size, size, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queue), new NamedThreadFactory("test-pending-"), policy);
    }
    private PendingAggregationService service(ThreadPoolExecutor pool, CountingAbortPolicy policy,
                                              RestTemplate client, long budget) {
        PendingProperties props = new PendingProperties();
        props.setTimeoutMillis(budget);
        return new PendingAggregationService(pool, client, props, policy);
    }
    @SuppressWarnings("unchecked")
    private List<String> states(Map<String,Object> result) {
        return ((List<Map<String,Object>>)result.get("systems")).stream()
                .map(s -> (String)s.get("state")).toList();
    }
    @Test void 慢任务排在最前仍保留后面已完成结果() throws Exception {
        CountingAbortPolicy policy = new CountingAbortPolicy();
        ThreadPoolExecutor pool = pool(4, 4, policy);
        CountDownLatch fastDone = new CountDownLatch(3);
        RestTemplate client = new RestTemplate() {
            @Override public <T> T getForObject(String url, Class<T> type, Object... args) {
                if (args[0].equals("OA")) {
                    try { fastDone.await(); Thread.sleep(5000); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                } else fastDone.countDown();
                return type.cast(new PendingItem[0]);
            }
        };
        try {
            assertThat(states(service(pool, policy, client, 300).aggregate(true)))
                    .containsExactly("TIMEOUT", "OK", "OK", "OK");
        } finally { pool.shutdownNow(); assertThat(pool.awaitTermination(2, TimeUnit.SECONDS)).isTrue(); }
    }
    @Test void 饱和立即局部拒绝且不在调用线程跑任务() throws Exception {
        CountingAbortPolicy policy = new CountingAbortPolicy();
        ThreadPoolExecutor pool = pool(1, 1, policy);
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        pool.submit(() -> { entered.countDown(); try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } });
        assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
        pool.submit(() -> {});
        AtomicInteger calls = new AtomicInteger();
        RestTemplate client = new RestTemplate() {
            @Override public <T> T getForObject(String url, Class<T> type, Object... args) {
                calls.incrementAndGet(); return type.cast(new PendingItem[0]);
            }
        };
        try {
            assertThat(states(service(pool, policy, client, 250).aggregate(true)))
                    .containsExactly("REJECTED", "REJECTED", "REJECTED", "REJECTED");
            assertThat(policy.rejectedCount()).isEqualTo(4);
            assertThat(calls.get()).isZero();
        } finally { release.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(2, TimeUnit.SECONDS)).isTrue(); }
    }
    @Test void 调用线程中断会取消剩余任务并保留中断标志() throws Exception {
        CountingAbortPolicy policy = new CountingAbortPolicy();
        ThreadPoolExecutor pool = pool(4, 4, policy);
        CountDownLatch started = new CountDownLatch(4);
        RestTemplate client = new RestTemplate() {
            @Override public <T> T getForObject(String url, Class<T> type, Object... args) {
                started.countDown();
                try { Thread.sleep(5000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                return type.cast(new PendingItem[0]);
            }
        };
        AtomicBoolean interrupted = new AtomicBoolean();
        AtomicReference<Map<String,Object>> result = new AtomicReference<>();
        Thread caller = new Thread(() -> {
            result.set(service(pool, policy, client, 10000).aggregate(true));
            interrupted.set(Thread.currentThread().isInterrupted());
        });
        try {
            caller.start(); assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            caller.interrupt(); caller.join(2000);
            assertThat(caller.isAlive()).isFalse();
            assertThat(interrupted.get()).isTrue();
            assertThat(states(result.get())).containsOnly("INTERRUPTED");
        } finally { caller.interrupt(); pool.shutdownNow(); assertThat(pool.awaitTermination(2, TimeUnit.SECONDS)).isTrue(); }
    }
}
