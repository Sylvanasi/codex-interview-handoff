/**
 * JDK 8 → 17 → 21 对照实验。
 *
 * 目的不是背特性列表，而是让你亲手看到两件事：
 *   1. 同一件事，21 的写法比 8 短多少、错的机会少多少；
 *   2. 虚拟线程和固定线程池的差别到底在哪。
 *
 * 跑法：cd coding-lab/jdk8-vs-21 && ./run.sh
 */
public class JdkLab {

    public static void main(String[] args) throws Exception {
        line("当前 JDK：" + System.getProperty("java.version") + "（本工程需要 21+）");

        line("① 数据结构：一个不可变 DTO");
        Jdk8Style.Contract oldContract = new Jdk8Style.Contract("HT-001", 120000.0);
        System.out.println("    Java 8  ：" + oldContract);
        Jdk17Style.Contract newContract = new Jdk17Style.Contract("HT-001", 120000.0);
        System.out.println("    Java 17 ：" + newContract);
        System.out.println("    → 同样一份数据，8 要写 30 行样板代码，17 只用 1 行 record");

        line("② 拼多行报文");
        System.out.println("    Java 8  ：\n" + Jdk8Style.buildJson("HT-001", 120000.0));
        System.out.println("    Java 17 ：\n" + Jdk17Style.buildJson("HT-001", 120000.0));
        System.out.println("    → 文本块把换行和缩进原样保留，不再写 \\n 和 + 拼接");

        line("③ 根据状态码给描述");
        System.out.println("    Java 8  ：status=3 → " + Jdk8Style.describeStatus(3));
        System.out.println("    Java 17 ：status=3 → " + Jdk17Style.describeStatus(3));
        System.out.println("    → switch 表达式不需要 break，而且漏分支编译不过");

        line("④ 处理一个 Object 类型的入参");
        System.out.println("    Java 8  ：" + Jdk8Style.describe("OA-2025-0001"));
        System.out.println("    Java 17 ：" + Jdk17Style.describe("OA-2025-0001"));
        System.out.println("    → instanceof 模式匹配把判断和强转合成一步");

        line("⑤ 只有 21 才有的：switch 模式匹配 + 记录解构");
        System.out.println("    文本载荷 → " + Jdk21Style.describePayload(new Jdk21Style.TextPayload("合同附件")));
        System.out.println("    合同载荷 → " + Jdk21Style.describePayload(
                new Jdk21Style.ContractPayload(new Jdk21Style.Contract("HT-009", 88888.0))));

        line("⑥ 只有 21 才有的：顺序集合");
        System.out.println("    " + Jdk21Style.showSequenced());
        System.out.println("    → Java 8 拿最后一个元素要写 list.get(list.size() - 1)");

        line("⑦ 一万个空转任务：固定线程池 vs 虚拟线程（差距不明显，因为任务太轻）");
        int taskCount = 10000;
        long platformCost = Jdk8Style.runManyTasks(taskCount);
        System.out.println("    固定线程池 200 个线程：" + platformCost + " ms");
        long virtualCost = Jdk21Style.runManyVirtualTasks(taskCount);
        System.out.println("    虚拟线程一任务一线程：" + virtualCost + " ms");
        System.out.println("    " + Jdk21Style.threadFactoryDiff());

        line("⑧ 关键对比：2000 个任务，每个都要等 100 毫秒（模拟一次 HTTP 调用）");
        int blockingTasks = 2000;
        long blockingSleep = 100;
        long fixedPoolCost = Jdk8Style.runBlockingTasks(blockingTasks, blockingSleep);
        System.out.println("    固定线程池 200 个线程：" + fixedPoolCost + " ms");
        System.out.println("      算术：2000 个任务 × 100ms ÷ 200 个线程 ≈ 1000ms —— 时间被线程数卡死");
        long virtualPoolCost = Jdk21Style.runBlockingVirtualTasks(blockingTasks, blockingSleep);
        System.out.println("    虚拟线程一任务一线程：" + virtualPoolCost + " ms");
        System.out.println("      算术：2000 个任务几乎同时在等，总耗时 ≈ 一次等待的时间");
        System.out.println("    → 虚拟线程解决的是“线程在等 IO 时被白白占住”，不是让 CPU 变快。");
        System.out.println("      数据库连接池只有 20 个连接的话，虚拟线程照样要排队 —— 这是它的边界。");

        line("面试要点（背这三句就够答一轮）");
        System.out.println("    1. Java 8 → 17/21 的收益：语法更短（record / 文本块 / switch 表达式 / 模式匹配）、");
        System.out.println("       GC 更稳（G1 默认、ZGC 低停顿）、生态要求（Spring Boot 3 只支持 17+）。");
        System.out.println("    2. 迁移成本：javax.* 变 jakarta.*、JDK 内置的 JAXB/JS 引擎被移除要单独引依赖、");
        System.out.println("       强封装内部 API 导致反射类库要加 --add-opens。");
        System.out.println("    3. 虚拟线程只解决“线程等 IO 被浪费”，不解决 CPU 密集，也不解决数据库连接池排队 —— ");
        System.out.println("       说清这个边界，比只会说“性能高”高一档。");
    }

    private static void line(String text) {
        System.out.println();
        System.out.println("================================================================");
        System.out.println(text);
        System.out.println("================================================================");
    }
}
