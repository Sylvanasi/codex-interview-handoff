import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Java 8 的写法。这些代码在 JDK 8 上就能编译运行，是你过去四年最熟的形状。
 * 每段都对应 Jdk17Style / Jdk21Style 里的同一件事，跑起来对着看就行。
 */
public class Jdk8Style {

    // ==========================================================
    // 1. 一个不可变数据类：字段 + 构造 + getter + equals + hashCode + toString 全手写
    // ==========================================================
    public static class Contract {
        private final String bizId;
        private final double amount;

        public Contract(String bizId, double amount) {
            this.bizId = bizId;
            this.amount = amount;
        }

        public String getBizId() { return bizId; }
        public double getAmount() { return amount; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Contract other = (Contract) o;
            return Double.compare(other.amount, amount) == 0 && bizId.equals(other.bizId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(bizId, amount);
        }

        @Override
        public String toString() {
            return "Contract{bizId='" + bizId + "', amount=" + amount + "}";
        }
    }

    // ==========================================================
    // 2. 拼一段多行报文：用 + 和 \n 手工拼，缩进全乱
    // ==========================================================
    public static String buildJson(String bizId, double amount) {
        return "{\n"
                + "  \"bizId\": \"" + bizId + "\",\n"
                + "  \"amount\": " + amount + ",\n"
                + "  \"source\": \"OA\"\n"
                + "}\n";
    }

    // ==========================================================
    // 3. 根据状态码给描述：老 switch，每个分支都得写 break，忘了就穿透
    // ==========================================================
    public static String describeStatus(int status) {
        String text;
        switch (status) {
            case 1:
                text = "草稿";
                break;
            case 2:
                text = "审批中";
                break;
            case 3:
            case 4:
                text = "已归档";
                break;
            default:
                text = "未知状态";
                break;
        }
        return text;
    }

    // ==========================================================
    // 4. 处理 Object 入参：先 instanceof 判断，再显式强转
    // ==========================================================
    public static String describe(Object payload) {
        if (payload instanceof String) {
            String s = (String) payload;          // 强转这一步，类型写错了要到运行期才炸
            return "字符串，长度 " + s.length();
        } else if (payload instanceof Contract) {
            Contract c = (Contract) payload;
            return "合同对象 " + c.getBizId();
        }
        return "不认识";
    }

    // ==========================================================
    // 5. 起 1 万个并发任务：只能用固定线程池，线程数就是并发的天花板
    // ==========================================================
    public static long runManyTasks(int taskCount) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(200);
        List<String> results = new ArrayList<String>();
        long start = System.currentTimeMillis();
        for (int i = 0; i < taskCount; i++) {
            final int index = i;
            pool.submit(new Runnable() {
                @Override
                public void run() {
                    if (index % 1000 == 0) {
                        synchronized (results) {
                            results.add("task-" + index);
                        }
                    }
                }
            });
        }
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);
        return System.currentTimeMillis() - start;
    }

    // ==========================================================
    // 6. 每个任务都要等 100 毫秒（模拟一次 HTTP 调用）：这才是固定线程池真正的痛点
    //    线程都在 sleep/等网络，但池子里只有 200 个线程，后面的任务只能排队。
    //    2000 个任务 × 100ms ÷ 200 个线程 ≈ 1000ms —— 时间被"线程数"卡死。
    // ==========================================================
    public static long runBlockingTasks(int taskCount, long sleepMillis) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(200);
        long start = System.currentTimeMillis();
        for (int i = 0; i < taskCount; i++) {
            pool.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(sleepMillis);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
        }
        pool.shutdown();
        pool.awaitTermination(60, TimeUnit.SECONDS);
        return System.currentTimeMillis() - start;
    }
}
