import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * JDK 21 的写法：21 是 LTS，也是现在 Spring Boot 3.x 的主流运行版本。
 * 这里放的是真正"只有 21 才有"的东西 —— 虚拟线程、switch 模式匹配、顺序集合。
 */
public class Jdk21Style {

    public record Contract(String bizId, double amount) {
    }

    sealed interface Payload permits TextPayload, ContractPayload {
    }

    record TextPayload(String text) implements Payload {
    }

    record ContractPayload(Contract contract) implements Payload {
    }

    // ==========================================================
    // 1. switch 模式匹配 + 记录解构（21 正式）：连 getter 都不用调
    //    因为 Payload 是 sealed，编译器知道只有这两种实现，所以可以不写 default。
    // ==========================================================
    public static String describePayload(Payload payload) {
        return switch (payload) {
            case TextPayload(String text) -> "文本载荷，长度 " + text.length();
            case ContractPayload(Contract(String bizId, double amount)) ->
                    "合同载荷 " + bizId + "，金额 " + amount;
        };
    }

    // ==========================================================
    // 2. 顺序集合（21）：getFirst / getLast / reversed 变成接口上的标准方法
    //    Java 8 时代想拿最后一个元素得写 list.get(list.size() - 1)。
    // ==========================================================
    public static String showSequenced() {
        List<String> steps = new ArrayList<>(List.of("接收附件", "存库", "推送金蝶"));
        return "第一步=" + steps.getFirst()
                + "，最后一步=" + steps.getLast()
                + "，倒过来=" + steps.reversed();
    }

    // ==========================================================
    // 3. 虚拟线程（21 正式）：不再需要"线程数就是并发上限"这个假设
    //    固定线程池 200 个线程跑 1 万个任务要排队；虚拟线程是一任务一线程，几乎零成本。
    //    注意：虚拟线程只解决"线程等 IO 时被浪费"的问题，不解决 CPU 密集和数据库连接池排队。
    // ==========================================================
    public static long runManyVirtualTasks(int taskCount) throws InterruptedException {
        List<String> results = new ArrayList<>();
        long start = System.currentTimeMillis();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < taskCount; i++) {
                final int index = i;
                pool.submit(() -> {
                    if (index % 1000 == 0) {
                        synchronized (results) {
                            results.add("vtask-" + index);
                        }
                    }
                });
            }
        }   // try-with-resources 关闭时会等待所有任务结束
        return System.currentTimeMillis() - start;
    }

    // ==========================================================
    // 5. 同样的"每个任务等 100 毫秒"，换成虚拟线程
    //    虚拟线程在阻塞时会把底层平台线程让出去，所以 2000 个任务几乎是同时在等，
    //    总耗时 ≈ 100ms，而不是固定线程池的 1000ms。这才是虚拟线程值钱的地方。
    // ==========================================================
    public static long runBlockingVirtualTasks(int taskCount, long sleepMillis) throws InterruptedException {
        long start = System.currentTimeMillis();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < taskCount; i++) {
                pool.submit(() -> {
                    try {
                        Thread.sleep(sleepMillis);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
        }
        return System.currentTimeMillis() - start;
    }

    // ==========================================================
    // 4. 一次线程批量的写法（21 新增）：Thread.ofVirtual() / Thread.ofPlatform()
    // ==========================================================
    public static String threadFactoryDiff() throws InterruptedException {
        Thread virtual = Thread.ofVirtual().name("oa-task-", 1).unstarted(() -> { });
        Thread platform = Thread.ofPlatform().name("pool-task-", 1).unstarted(() -> { });
        virtual.start();
        platform.start();
        virtual.join();
        platform.join();
        return "虚拟线程=" + virtual.isVirtual() + "，平台线程=" + platform.isVirtual();
    }

    /** 时间工具类补充：8 之后新增的 Duration/Instant 支持更完整，这里只留个提醒。 */
    public static void unused(TimeUnit unit) {
        // 占位：提醒你 21 里 TimeUnit 还与虚拟线程调度配合，但业务代码仍应优先用 Duration
    }
}
