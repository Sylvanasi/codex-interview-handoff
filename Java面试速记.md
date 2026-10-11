# Java 面试速记（考前 20 分钟版）

> 用法：只看加粗词和箭头，能顺着箭头把一句话讲完整就够了。

---

## 1. HashMap / ConcurrentHashMap

**HashMap 结构**：数组 + 链表 + 红黑树（JDK8）。

- 链表长度 **≥8 且数组长度 ≥64** → 转红黑树；**<6** 退化回链表。
- 容量永远是 **2 的幂**（`n-1 & hash` 等价取模，位运算快）。
- 扰动函数 **`h ^ (h>>>16)`**：让高位参与运算，减少碰撞。
- 默认 **初始 16、负载因子 0.75、扩容 2 倍**。
- 为什么 0.75：泊松分布下冲突概率低，是**空间与时间的折中**。
- **JDK8 扩容优化**：元素要么留在原下标，要么移到 `原下标 + oldCap`（看 hash 新增的那一位），不用重算 hash。
- `key=null` 放**下标 0**。
- 头插 vs 尾插：**JDK7 头插**（多线程扩容会成环死循环），**JDK8 尾插**。
- 线程不安全 → 并发 put 会**丢数据 / size 不准**。

**ConcurrentHashMap**

| | JDK7 | JDK8 |
|---|---|---|
| 锁粒度 | **Segment 分段锁**（继承 ReentrantLock），默认 16 段 | **CAS + synchronized 锁单个桶的头节点**，取消 Segment |
| 扩容 | 段内扩容 | **多线程协助扩容**，用 ForwardingNode 标记已迁移 |
| size | 分段累加 | **baseCount + CounterCell**（类 LongAdder，分散热点） |

- **不允许 null 键值**：并发下无法区分「不存在」和「值为 null」。
- 弱一致性迭代器：遍历时不抛 ConcurrentModificationException。

---

## 2. JVM

**内存区域**

- 线程私有：**程序计数器、虚拟机栈、本地方法栈**。
- 线程共享：**堆、方法区**（JDK8 后是**元空间 Metaspace，用本地内存**）。
- 堆分代：**新生代 Eden + 2×Survivor（8:1:1）+ 老年代**。

**对象分配**：逃逸分析 → 栈上分配 / 标量替换 → **TLAB** → Eden；**大对象直接进老年代**。

**怎么判断对象要回收**：**可达性分析**（GC Roots）。引用四种：**强、软、弱、虚**。

**回收算法**：标记-清除（碎片）、标记-复制（新生代）、标记-整理（老年代）。

**垃圾收集器**

- 新生代：Serial / ParNew / **Parallel Scavenge**（吞吐优先）。
- **CMS**：并发标记清除，4 阶段 = **初始标记 → 并发标记 → 重新标记 → 并发清除**；停顿短，但有**碎片**和 **Concurrent Mode Failure**。
- **G1（JDK9 默认）**：堆切成 **Region**，可设置**预期停顿时间**，整体标记-整理 + 局部复制。
- **ZGC**：染色指针 + 读屏障，**STW < 1ms**。

**类加载**：**加载 → 验证 → 准备 → 解析 → 初始化**（准备阶段赋零值，初始化才执行 `clinit`）。

**双亲委派**：Bootstrap → Platform → App → 自定义。好处：**安全（防篡改核心类）+ 避免重复加载**。打破场景：**SPI（线程上下文类加载器）、Tomcat、OSGi、热部署**。

**排查**

- 参数：`-Xms -Xmx -Xmn -Xss -XX:MetaspaceSize -XX:+UseG1GC`。
- 工具：`jps / jstat / jmap / jstack`、**MAT**、**arthas**。
- **CPU 飙高**：`top` → `top -Hp pid` → `jstack` 找那个线程在看什么。
- **OOM 类型**：堆溢出、栈溢出、元空间、直接内存。

---

## 3. JUC

**AQS（核心中的核心）**

- = **volatile int state + FIFO 双向队列（Node，CLH 变体）**。
- 支持**独占**和**共享**两种模式，模板方法 `tryAcquire / tryRelease / tryAcquireShared`。
- 独占：**ReentrantLock**；共享：**Semaphore / CountDownLatch / ReadWriteLock 读锁**。
- 入队后 `LockSupport.park()` 阻塞，被唤醒后重新抢锁。

**synchronized 锁升级**：无锁 → 偏向锁 → 轻量级锁（CAS 自旋）→ 重量级锁。
> 记：**偏向一个人、轻量转几下、重量级才挂起**。JDK15 起偏向锁默认关闭。

**ReentrantLock vs synchronized**

- Lock 优势：**可中断、可超时、可公平、多条件队列 Condition**；缺点：**必须手动 unlock**（try-finally）。

**CAS**：比较并交换，底层 `Unsafe.compareAndSwapInt` + CPU 指令。
三大问题：**ABA（用 AtomicStampedReference）、自旋开销大、只能保证一个变量**。

**常用工具**

- **CopyOnWriteArrayList**：读无锁，写时复制，**读多写少**场景。
- **BlockingQueue**：Array / Linked / Synchronous / Priority / Delay。
- **CountDownLatch**（一次性，等别人）vs **CyclicBarrier**（可复用，互相等）vs **Semaphore**（限流）。
- **CompletableFuture**：异步编排（thenApply / thenCompose / allOf）。
- **ThreadLocal**：每个线程独立副本；**key 是弱引用，value 强引用 → 必须 remove 防内存泄漏**；底层 ThreadLocalMap。

---

## 4. 线程池（ThreadPoolExecutor）

**7 个参数**：核心线程数、最大线程数、空闲存活时间、时间单位、**工作队列**、线程工厂、**拒绝策略**。

**执行流程（背下来）**

> **核心线程 → 队列 → 非核心线程 → 拒绝**
> 核心没满直接开；核心满了进队列；队列满了才开非核心；都满了才拒绝。

**4 种拒绝策略**：Abort（默认，抛异常）、CallerRuns（调用者线程跑）、Discard（丢新的）、DiscardOldest（丢最老的）。

**队列的坑**

- **无界队列（LinkedBlockingQueue）→ maximumPoolSize 失效**，任务堆积到 OOM。
- **SynchronousQueue** → 不排队，直接交给线程，配合大 maximum。

**参数怎么定**

- CPU 密集：**N + 1**
- IO 密集：**2N**，更准确用 `N × (1 + 等待时间 / 计算时间)`
- 核心与非核心设成一样大，避免频繁创建销毁。

**内部原理**：`ctl` 一个 AtomicInteger = **高 3 位状态 + 低 29 位线程数**。
状态：**RUNNING → SHUTDOWN（不接新任务，处理完队列）→ STOP → TIDYING → TERMINATED**。

**为什么不用 Executors**：Fixed/Single 用**无界队列**、Cached/Scheduled 的 maximum 是 **Integer.MAX_VALUE** → 都会 **OOM**。→ 手动 `new ThreadPoolExecutor` 并指定**有界队列 + 有意义的线程名 + 拒绝策略**。

---

## 5. Redis

**数据类型**：String、Hash、List、Set、**ZSet（跳表）**、Bitmap、HyperLogLog、Geo、Stream。

**底层结构**：SDS、quicklist、listpack/ziplist、**skiplist**、intset、hashtable、渐进式 rehash。

**线程模型**：命令处理是**单线程**（天然无并发竞争），**6.0 起网络 IO 多线程**，依托**内存 + IO 多路复用**。

**持久化**

- **RDB**：快照，文件小恢复快，**可能丢数据**。
- **AOF**：写命令日志，`appendfsync` = always / everysec / no；有 **AOF 重写**。
- **混合持久化（4.0+）**：RDB 全量 + AOF 增量。

**过期删除**：**惰性删除 + 定期删除**。
**淘汰策略**：noeviction、allkeys-lru / lfu、volatile-lru / lfu / ttl / random。

**三大缓存问题（必答）**

| 问题 | 现象 | 方案 |
|---|---|---|
| **穿透** | 查不存在的 key，全打到 DB | **布隆过滤器**、缓存空值 |
| **击穿** | 热点 key 过期瞬间高并发 | **互斥锁重建**、逻辑过期 |
| **雪崩** | 大量 key 同时失效 / Redis 宕机 | **过期时间加随机**、多级缓存、熔断降级 |

**缓存一致性**：**Cache Aside** = 先更新 DB，再**删除**缓存（不是更新）。
更强一致：**延迟双删**、**订阅 binlog（Canal）**。

**分布式锁**：`SET key 唯一value NX PX 30000`，**释放要用 Lua 保证「判断 + 删除」原子**。
**Redisson**：看门狗自动续期、可重入。RedLock 有争议。

**高可用**：主从复制 → **Sentinel 哨兵**（自动故障转移）→ **Cluster 分片（16384 slot）**。

---

## 6. 分布式事务

**理论**：**CAP**（三者不可兼得）、**BASE**（基本可用、软状态、最终一致）。

| 方案 | 一句话 | 适用 |
|---|---|---|
| **2PC / 3PC** | 协调者两阶段提交，**同步阻塞 + 协调者单点** | 强一致、短事务（XA） |
| **TCC** | **Try 预留 → Confirm 提交 → Cancel 补偿** | 金融、需要高一致 |
| **本地消息表 / 可靠消息** | 本地事务 + 消息表 + **定时补偿** | 最终一致 |
| **RocketMQ 事务消息** | 半消息 → 执行本地事务 → 回查 | 最终一致 |
| **最大努力通知** | 不断重试通知，不保证一定成功 | 支付回调 |
| **Saga** | 长事务拆成多个本地事务 + **补偿** | 长流程、允许中间态 |

**TCC 三个坑**：**幂等、防悬挂、空回滚**。

**Seata 四种模式**：**AT**（全局锁 + undo log 自动反向补偿，最常用）、TCC、Saga、XA。

---

## 7. Spring

**IoC / DI**：容器管对象生命周期和依赖；注入方式：构造器（推荐）/ setter / 字段。

**Bean 生命周期（背这条链）**

> 实例化 → 属性填充 → Aware 接口 → **BeanPostProcessor.before** → `@PostConstruct` / InitializingBean → **BeanPostProcessor.after（AOP 代理在这里生成）** → 使用 → `@PreDestroy` / DisposableBean

**循环依赖 → 三级缓存**（高频）

- 一级 `singletonObjects`：成品。
- 二级 `earlySingletonObjects`：半成品（提前暴露的引用）。
- 三级 `singletonFactories`：ObjectFactory，**用于提前暴露 AOP 代理**。
- 只能解决**单例 + setter/字段注入**；**构造器注入的循环依赖解决不了**（对象还没造出来，没机会暴露）。

**AOP**：动态代理 —— **有接口用 JDK Proxy，没接口用 CGLIB**。核心概念：切面、通知（Before/After/Around）、切点。

**@Transactional 本质就是 AOP**。失效场景（面试常问）：

> **自调用、private/非 public 方法、异常被自己 catch、抛检查异常、多线程、类没被代理**。

**Spring MVC**：DispatcherServlet → HandlerMapping → HandlerAdapter → 返回 ModelAndView → ViewResolver → 渲染。

**Spring Boot 自动配置**：`@SpringBootApplication` = `@SpringBootConfiguration` + `@ComponentScan` + **`@EnableAutoConfiguration`**；靠 `AutoConfiguration.imports`（旧版 spring.factories）+ **`@Conditional` 条件装配**。

---

## 考前 60 秒再扫一遍

- HashMap：**2 的幂、0.75、8 转红黑树、JDK8 尾插、扩容留原位或 +oldCap**。
- CHM：**JDK7 Segment 分段锁 → JDK8 CAS + synchronized 锁桶**。
- JVM：**堆 / 方法区共享，栈 / PC 私有；GC Roots；CMS 四阶段；G1 Region**。
- AQS：**state + CLH 队列，独占 / 共享**。
- 线程池：**核心 → 队列 → 非核心 → 拒绝**；别用 Executors。
- Redis：**穿透 / 击穿 / 雪崩**；**先更新库再删缓存**；**SET NX PX + Lua**。
- 分布式事务：**2PC / TCC / 可靠消息 / Saga**，TCC 记**幂等、悬挂、空回滚**。
- Spring：**三级缓存**、**AOP 两种代理**、**事务失效场景**。
