import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * 幂等推送实验室：把「重复推送」拆成 4 个关卡，一关一关看它是怎么发生的。
 *
 * 只用 JDK 的并发原语模拟 Redis 的 SET key val NX EX ttl 语义，本机不需要装 Redis。
 * 想换成真 Redis：把 SetnxStore 换成一个用 Jedis/Lettuce 的实现，
 * tryAcquire() 对应 set(key, val, SetParams.setParams().nx().ex(ttl))，其它代码一行不用改。
 */
public class PushLab {

    static int passed = 0, failed = 0;

    static void check(String name, boolean ok, String actual) {
        if (ok) { passed++; System.out.println("  ✅ " + name + "（实际：" + actual + "）"); }
        else { failed++; System.out.println("  ❌ " + name + "（实际：" + actual + "）"); }
    }

    static void note(String s) { System.out.println("     " + s); }

    /** 演示项：预期就是"坏结果"，不计入验收通过/失败。 */
    static void demo(String s) { System.out.println("  🔬 演示：" + s); }

    static void stage(String s) {
        System.out.println();
        System.out.println("================================================================");
        System.out.println(s);
        System.out.println("================================================================");
    }

    static void rule(String s) {
        System.out.println();
        System.out.println("--------------------------------------------------------------");
        System.out.println(s);
        System.out.println("--------------------------------------------------------------");
    }

    static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    // ==================== 幂等台账 ====================

    static final class Entry {
        static final int IN_PROGRESS = 1, DONE = 2;
        final int state;
        final long expireAt;
        Entry(int state, long expireAt) { this.state = state; this.expireAt = expireAt; }
    }

    interface DedupStore {
        /** 占位：抢到返回 true。语义等于 Redis 的 SETNX + TTL。 */
        boolean tryAcquire(String bizId, long ttlMillis);
        /** 推送成功后记账，把标记置为 DONE。 */
        void markDone(String bizId, long ttlMillis);
        /** 推送失败后释放占位，允许重试。 */
        void release(String bizId);
        /** 是否已成功推送过。 */
        boolean isDone(String bizId);
    }

    /** 正确姿势：一次 CAS 完成「判存在 + 占位」。 */
    static class SetnxStore implements DedupStore {
        final ConcurrentHashMap<String, Entry> map = new ConcurrentHashMap<>();

        public boolean tryAcquire(String bizId, long ttl) {
            long now = System.currentTimeMillis();
            while (true) {
                Entry old = map.get(bizId);
                if (old != null && old.expireAt > now) return false;   // 已有有效标记：进行中或已完成
                if (old == null) {
                    if (map.putIfAbsent(bizId, new Entry(Entry.IN_PROGRESS, now + ttl)) == null) return true;
                } else if (map.replace(bizId, old, new Entry(Entry.IN_PROGRESS, now + ttl))) {
                    return true;                                        // 旧标记已过期，CAS 替换成功
                }
            }
        }

        public void markDone(String bizId, long ttl) {
            map.put(bizId, new Entry(Entry.DONE, System.currentTimeMillis() + ttl));
        }

        public void release(String bizId) {
            map.computeIfPresent(bizId, (k, v) -> v.state == Entry.IN_PROGRESS ? null : v);
        }

        public boolean isDone(String bizId) {
            Entry e = map.get(bizId);
            return e != null && e.state == Entry.DONE && e.expireAt > System.currentTimeMillis();
        }
    }

    /** 错误姿势：先查后写（check-then-act）。查和写之间有一条并发的缝。 */
    static class CheckThenActStore implements DedupStore {
        final ConcurrentHashMap<String, Entry> map = new ConcurrentHashMap<>();

        public boolean tryAcquire(String bizId, long ttl) {
            Entry old = map.get(bizId);                                          // 查
            if (old != null && old.expireAt > System.currentTimeMillis()) return false;
            LockSupport.parkNanos(2_000_000L);                                   // 把这条缝放大到肉眼可见
            map.put(bizId, new Entry(Entry.IN_PROGRESS, System.currentTimeMillis() + ttl)); // 写
            return true;
        }

        public void markDone(String bizId, long ttl) {
            map.put(bizId, new Entry(Entry.DONE, System.currentTimeMillis() + ttl));
        }

        public void release(String bizId) {
            map.computeIfPresent(bizId, (k, v) -> v.state == Entry.IN_PROGRESS ? null : v);
        }

        public boolean isDone(String bizId) {
            Entry e = map.get(bizId);
            return e != null && e.state == Entry.DONE && e.expireAt > System.currentTimeMillis();
        }
    }

    // ==================== 模拟金蝶 ====================

    enum Kind { OK, FAIL, DUPLICATE, TIMEOUT }

    static class PushResult {
        final Kind kind;
        final String msg;
        PushResult(Kind kind, String msg) { this.kind = kind; this.msg = msg; }
        static PushResult ok() { return new PushResult(Kind.OK, "推送成功"); }
        static PushResult fail(String m) { return new PushResult(Kind.FAIL, m); }
        static PushResult duplicate(String m) { return new PushResult(Kind.DUPLICATE, m); }
        static PushResult timeout() { return new PushResult(Kind.TIMEOUT, "响应超时（对方可能已经处理了）"); }
        boolean success() { return kind == Kind.OK || kind == Kind.DUPLICATE; }
        public String toString() { return kind + " / " + msg; }
    }

    static class CrashSim extends RuntimeException {
        CrashSim() { super("模拟：进程崩溃，台账没来得及记账"); }
    }

    /** 对方系统（金蝶）：可以配置成开放幂等、或者一重复就报错。 */
    static class KdxClient {
        final AtomicInteger received = new AtomicInteger();
        final List<String> receivedIds = Collections.synchronizedList(new ArrayList<String>());
        final Set<String> processed = ConcurrentHashMap.newKeySet();
        volatile boolean dedupByBizId = false;        // 对方是否按业务单号做幂等
        volatile boolean timeoutAfterSuccess = false; // 处理成功但响应超时
        volatile long latencyMillis = 0;

        PushResult push(String bizId) {
            received.incrementAndGet();
            receivedIds.add(bizId);
            sleep(latencyMillis);
            if (timeoutAfterSuccess) {
                processed.add(bizId);                 // 对方其实处理了，只是响应没回来
                return PushResult.timeout();
            }
            if (!processed.add(bizId)) {              // 同一张单第二次进来
                if (dedupByBizId) return PushResult.duplicate("对方按业务单号幂等，返回『已受理』");
                return PushResult.fail("对方报错：该单据已存在");
            }
            return PushResult.ok();
        }

        /** 对方提供的查单接口。 */
        boolean hasProcessed(String bizId) { return processed.contains(bizId); }

        int receivedCount() { return received.get(); }
    }

    // ==================== 业务侧推送服务 ====================

    static class PushService {
        final DedupStore store;
        final KdxClient client;
        final long ttlMillis;
        volatile boolean markBeforePush = true;  // true：先占位再推；false：先推再记账
        volatile boolean crashAfterPush = false; // 模拟推送成功后进程挂掉

        PushService(DedupStore store, KdxClient client, long ttlMillis) {
            this.store = store;
            this.client = client;
            this.ttlMillis = ttlMillis;
        }

        PushResult push(String bizId) {
            if (markBeforePush) {
                if (!store.tryAcquire(bizId, ttlMillis)) {
                    return PushResult.duplicate("本地台账已有标记，直接跳过，没有打对方接口");
                }
                PushResult r;
                try {
                    r = client.push(bizId);
                } catch (RuntimeException e) {
                    store.release(bizId);       // 明确失败 → 释放占位，允许重试
                    throw e;
                }
                if (crashAfterPush) throw new CrashSim();
                if (r.success()) store.markDone(bizId, ttlMillis);
                else store.release(bizId);
                return r;
            } else {
                if (store.isDone(bizId)) return PushResult.duplicate("本地台账已成功，跳过");
                PushResult r = client.push(bizId);
                if (crashAfterPush) throw new CrashSim();
                if (r.success()) store.markDone(bizId, ttlMillis);
                return r;
            }
        }
    }

    static void runConcurrently(PushService service, String bizId, int threads) {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        final CyclicBarrier barrier = new CyclicBarrier(threads);
        final CountDownLatch done = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    barrier.await(5, TimeUnit.SECONDS);
                    service.push(bizId);
                } catch (RuntimeException ignored) {
                    // 重复推送被对方拒绝时抛出来，这里不关心
                } catch (Exception ignored) {
                } finally {
                    done.countDown();
                }
            });
        }
        try { done.await(20, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        pool.shutdownNow();
    }

    // ==================== 四个关卡 ====================

    static void stage1() {
        stage("关卡 1 · 并发窗口：20 个线程同时推同一张单据");
        final String bizId = "HT20250001";

        rule("1.1 错误姿势：先查后写（check-then-act）");
        KdxClient c1 = new KdxClient();
        PushService s1 = new PushService(new CheckThenActStore(), c1, 60_000);
        runConcurrently(s1, bizId, 20);
        demo("20 个并发请求，对方收到了 " + c1.receivedCount() + " 次"
                + (c1.receivedCount() > 1 ? " —— 这就是重复推送" : "（这次没撞上窗口，多跑几遍一定撞得上）"));
        note("查和写之间那条缝，就是重复推送的来源。");

        rule("1.2 正确姿势：SETNX 原子占位");
        KdxClient c2 = new KdxClient();
        PushService s2 = new PushService(new SetnxStore(), c2, 60_000);
        runConcurrently(s2, bizId, 20);
        check("20 个并发请求，对方只应该收到 1 次", c2.receivedCount() == 1, "收到 " + c2.receivedCount() + " 次");
        note("只有 1 个请求抢到占位，其余 19 个在本地就被拦下，根本没打对方接口。");
    }

    static void stage2() {
        stage("关卡 2 · 顺序：先占位再推 vs 先推再记账");
        final String bizId = "HT20250002";

        rule("2.1 错误顺序：先推再记账，推成功之后进程挂了");
        KdxClient c1 = new KdxClient();
        PushService s1 = new PushService(new SetnxStore(), c1, 60_000);
        s1.markBeforePush = false;
        s1.crashAfterPush = true;
        try { s1.push(bizId); } catch (RuntimeException e) { note("第一次：" + e.getMessage()); }
        s1.crashAfterPush = false;
        PushResult r1 = s1.push(bizId);
        check("重试会再推一次（对方收到 2 次）", c1.receivedCount() == 2, "对方收到 " + c1.receivedCount() + " 次，第二次结果：" + r1);

        rule("2.2 正确顺序：先占位再推，同样推成功之后进程挂了");
        KdxClient c2 = new KdxClient();
        PushService s2 = new PushService(new SetnxStore(), c2, 60_000);
        s2.crashAfterPush = true;
        try { s2.push(bizId); } catch (RuntimeException e) { note("第一次：" + e.getMessage()); }
        s2.crashAfterPush = false;
        PushResult r2 = s2.push(bizId);
        check("重试被本地占位拦住（对方只收到 1 次）", c2.receivedCount() == 1, "对方收到 " + c2.receivedCount() + " 次，第二次结果：" + r2);
        note("代价：这一次其实推成功了，但台账没记上 DONE，只能靠对账/查单把状态校准回来。");
        note("结论：宁可漏记、不可重推；不确定的状态交给对账，而不是直接重试。");
    }

    static void stage3() {
        stage("关卡 3 · 最难的场景：响应超时，但对方其实已经处理成功");
        final String bizId = "HT20250003";

        rule("3.0 没有任何兜底，直接重试");
        KdxClient c1 = new KdxClient();
        c1.timeoutAfterSuccess = true;
        PushService s1 = new PushService(new SetnxStore(), c1, 60_000);
        note("第一次：" + s1.push(bizId));
        c1.timeoutAfterSuccess = false;
        PushResult second = s1.push(bizId);
        check("重试会真的打到对方（对方报重复）", second.kind == Kind.FAIL, "第二次结果：" + second);
        note("这就是「靠对方报错才发现重复」的路子 —— 被动、难排查、还可能把对方搞脏。");

        rule("3.1 兜底 A：对方按业务单号幂等");
        KdxClient c2 = new KdxClient();
        c2.timeoutAfterSuccess = true;
        c2.dedupByBizId = true;
        PushService s2 = new PushService(new SetnxStore(), c2, 60_000);
        s2.push(bizId);
        c2.timeoutAfterSuccess = false;
        PushResult r2 = s2.push(bizId);
        check("重试拿到『已受理』，可以直接当成功处理", r2.kind == Kind.DUPLICATE && r2.success(), "第二次结果：" + r2);

        rule("3.2 兜底 B：超时后不重试，先调查单接口");
        KdxClient c3 = new KdxClient();
        c3.timeoutAfterSuccess = true;
        PushService s3 = new PushService(new SetnxStore(), c3, 60_000);
        s3.push(bizId);
        boolean alreadyThere = c3.hasProcessed(bizId);
        check("查单接口确认对方已受理，本地置 DONE 即可", alreadyThere, "hasProcessed=" + alreadyThere);
        note("超时是「未知状态」：未知状态要先查，不要直接重试。");
    }

    static void stage4() {
        stage("关卡 4 · TTL 到期之后：缓存拦不住，谁来兜底");
        final String bizId = "HT20250004";

        KdxClient c = new KdxClient();
        PushService s = new PushService(new SetnxStore(), c, 200);   // TTL 只有 200ms
        s.push(bizId);
        note("等 300ms，让 TTL 过期……");
        sleep(300);
        s.push(bizId);
        check("TTL 过期后，同一张单会被再推一次", c.receivedCount() == 2, "对方收到 " + c.receivedCount() + " 次");

        rule("最终兜底：数据库唯一约束");
        Set<String> dbUniqueKey = ConcurrentHashMap.newKeySet();
        boolean firstInsert = dbUniqueKey.add(bizId + ":pushed");
        boolean secondInsert = dbUniqueKey.add(bizId + ":pushed");
        check("同一张单只能插入一次推送记录", firstInsert && !secondInsert, "第一次写入=" + firstInsert + "，第二次写入=" + secondInsert);
        note("分工：Redis 是快速拦截层（挡掉绝大多数重复，挡住并发窗口），");
        note("      DB 唯一约束/幂等状态表是最终兜底（哪怕缓存重启、TTL 过期、Redis 丢了也不重复）。");
        note("      两边都要有，只靠其中一边都会被面试官追穿。");
    }

    public static void main(String[] args) {
        System.out.println("幂等推送实验室 · 只用 JDK，不需要 Redis");
        System.out.println("场景：OA 审批完成后，把合同台账推送给金蝶。");
        stage1();
        stage2();
        stage3();
        stage4();
        stage("验收结果");
        System.out.println("  通过 " + passed + " 项 / 失败 " + failed + " 项");
        System.out.println();
        System.out.println("  面试上要能一口气说出的三句话：");
        System.out.println("  1. 幂等键选业务单号（不能选自增主键或时间戳），落在 Redis 用 SETNX 占位 + TTL。");
        System.out.println("  2. 顺序是先占位、再推送、成功了才记 DONE；失败一定要释放占位，否则会漏推。");
        System.out.println("  3. 超时是未知状态，先查单再决定；Redis 只是快速拦截层，最终兜底靠 DB 唯一约束。");
    }
}
