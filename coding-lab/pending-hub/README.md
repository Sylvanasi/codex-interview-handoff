# 统一待办聚合实验室（Pending Hub Lab）

场景来源：简历里「康佳集团 · 统一待办聚合门户组件开发」——基于 ThreadPoolExecutor 并发拉取多个业务系统待办并聚合展示。

## 场景（面试时你要能把这套话讲出来）

集团门户首页要展示 4 个业务系统的待办：OA、ERP、HR、FIN。

原来的实现是串行调用：OA 120ms → ERP 150ms → HR 90ms → FIN 110ms，光上游就 ~470ms，加上网络和序列化，用户体感 1s 以上；任何一个系统抖动，整个首页跟着一起卡。

改造目标：

- 4 个上游并发拉取，整体耗时 ≈ 最慢的那一个
- 单个上游超过 250ms 直接判超时，不允许拖住整页
- 任一上游报错或超时，只降级该系统的数据，其它系统照常展示
- 每个上游都要有耗时埋点，出问题能定位是谁慢
- （可选）同一用户 5 秒内重复进入门户，不重复打上游

## 文件说明

| 文件 | 说明 |
|---|---|
| `src/PendingAggregator.java` | **你写这个**，其它文件都不用改 |
| `src/SerialPendingAggregator.java` | 已实现的串行基线，用来对比 |
| `src/HubLab.java` | 自动验收脚本 |
| `src/SimulatedUpstream.java` | 模拟上游：可配置耗时、抛异常、卡死 |
| `src/PendingItem.java`、`src/HubSnapshot.java`、`src/UpstreamClient.java` | 数据结构与接口，只读 |
| `src/Check.java` | 极简断言工具，不依赖 JUnit |

## 怎么跑

```bash
cd "/Users/wufeng/Documents/ChatGPT/面试/coding-lab/pending-hub"
./run.sh
```

本机没装 JDK，脚本默认用 IntelliJ 自带的 JBR（JDK 25）。你自己装了 JDK 就 `export JAVA_HOME=/path/to/jdk` 再跑。

也可以直接用 IntelliJ IDEA 打开这个目录，右键 `HubLab` 运行。

## 关卡

### 第 0 关（热身）：先把线程池的地基垫上

不要直接写 `PendingAggregator`。先做这三个小练习，每个都是十几行代码，做完再进第 2 关：

1. 在 `HubLab` 里临时加一个 `main` 测试方法，用 `Executors.newFixedThreadPool(4)` 把 `healthyClients()` 的 4 个上游并发跑完，把 8 条待办打印出来并算出总耗时（应该 < 200ms）。这一步只验证你理解了"提交任务 → 拿 Future → get 结果"。
2. 把 `Executors.newFixedThreadPool(4)` 换成手写的 `new ThreadPoolExecutor(...)`，参数自己定，然后回答：核心线程数为什么是 4？队列为什么要有界？拒绝策略选哪个、什么时候会触发？
3. 给第 2 步加上 250ms 超时（先用 `Future.get(timeout, unit)` 试一次），然后回答：超时返回后，那个还在跑的上游任务会不会被中断？如果上游是"卡死 1.2 秒"的 HR，你的进程要多久才能退出？

这三个问题答不上来就先去搜，别急着抄 `PendingAggregator` 的答案——第 2 关要用的就是这套东西。

### 第 1 关（读）：看懂串行基线为什么慢

`HubLab` 会打印串行耗时。不用写代码，但你要能算出"4 个上游串行 = 470ms，并发 = 最慢那个 150ms"。

### 第 2 关（必做）：在 `PendingAggregator.aggregate` 里实现并发聚合

契约写在 `PendingAggregator` 的类注释里，验收标准由 `HubLab` 自动判定：

- 健康场景：8 条待办、无降级系统、整体耗时 < 300ms、4 个上游都有耗时埋点、待办顺序与上游注册顺序一致
- 故障场景：ERP 抛异常 + HR 卡死超时 → 只有这两个被降级，OA/FIN 的 4 条待办正常返回，整体耗时 < 500ms，顺序稳定

### 第 3 关（可选）：同一用户 5s 内不重复打上游

只缓存成功结果；含降级的那次不缓存（降级是临时故障，缓存它会把故障放大到 5 秒）。

验收：第二次请求耗时 < 80ms 且上游调用次数不增加；降级结果第二次仍然打上游。

## 交给我 review 时给我这四样

1. `PendingAggregator` 的完整源码
2. `./run.sh` 的完整输出
3. 你对这三个问题的回答：
   - 线程池的核心线程数、最大线程数、队列容量、拒绝策略怎么定的？为什么不是别的数？
   - `Future.get(timeout)` / `orTimeout` / `completeOnTimeout` 你选了哪个？超时之后上游的那个线程会被取消吗？
   - 如果上游从 4 个变成 40 个，你的实现会先出什么问题？
4. 你踩到的最坑的一个点是什么

## 提醒

- 别搜现成答案。这题的价值就在于你自己踩一遍坑，踩过之后面试被问到才有画面感。
- 如果跑完终端停了几秒才退出，想想是不是超时之后上游任务还在跑——这本身就是一个面试追问点。
- 这个目录将来可以直接升级成一个 Spring Boot 工程：`PendingAggregator` 变成 `@Service`，上游调用换成 `RestTemplate`/`WebClient`，Redis 缓存换成 `StringRedisTemplate`。你的简历缺 Spring Boot，从这儿补最快。
