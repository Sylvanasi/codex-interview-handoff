package com.wufeng.lab.web;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wufeng.lab.service.TokenService;

/**
 * 凭证缓存的演示接口。
 *
 * 要证明的两件事：
 * 1. 连续调用多次，真实换取次数只有 1 次 —— 缓存生效；
 * 2. 50 个线程同时第一次调用，真实换取次数仍然只有 1 次 —— synchronized 让它们排队，
 *    而且"是否过期"的判断在锁内，所以不会出现第二个线程又换一次。
 */
@RestController
@RequestMapping("/api/token")
public class TokenController {

    private final TokenService tokenService;

    public TokenController(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @GetMapping("/state")
    public Map<String, Object> state() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("state", tokenService.describeState());
        result.put("refreshCount", tokenService.refreshCount());
        return result;
    }

    @PostMapping("/warm")
    public Map<String, Object> warm() {
        tokenService.getToken();
        return state();
    }

    @PostMapping("/clear")
    public Map<String, Object> clear() {
        tokenService.invalidate();
        return state();
    }

    /** 并发获取 token：看真实换取次数是不是 1。 */
    @PostMapping("/parallel")
    public Map<String, Object> parallel(@RequestParam(defaultValue = "50") int threads) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
//        ThreadPoolExecutor pool = new ThreadPoolExecutor(threads, threads, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), new ThreadPoolExecutor.CallerRunsPolicy());

        CyclicBarrier barrier = new CyclicBarrier(threads);
        CountDownLatch done = new CountDownLatch(threads);
        Set<String> distinctTokens = ConcurrentHashMap.newKeySet();
        int before = tokenService.refreshCount();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    barrier.await(5, TimeUnit.SECONDS);
                    distinctTokens.add(tokenService.getToken());
                } catch (Exception ignored) {
                    // 演示接口，忽略单个线程的异常
                } finally {
                    done.countDown();
                }
            });
        }
        done.await(20, TimeUnit.SECONDS);
        pool.shutdownNow();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("threads", threads);
        result.put("refreshCountBefore", before);
        result.put("refreshCountAfter", tokenService.refreshCount());
        result.put("distinctTokenCount", distinctTokens.size());
        result.put("state", tokenService.describeState());
        return result;
    }

}
