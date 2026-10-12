# Spring Boot 实验台

## 为什么要有这个项目

你的简历上全是交付项目，**一处 Spring Boot 都没有**。8 年 Java 却没有 Boot，面试官一定会问"你们用的什么框架、怎么发布的"。这个工程就是补这个缺口用的：

1. 让"Spring Boot 项目长什么样、怎么跑、怎么发"从纸面变成肌肉记忆；
2. 把简历上那两句高风险话（`token 周期性缓存与鉴权复用`、`幂等/重复提交治理`）变成能跑、能被追问的代码；
3. 面试时你可以说："这几点我自己写了个工程验证过，用 mock 端模拟对方的超时和重复报错。"——比背概念高一个档次。

## 前置

- **JDK 17 或更高（必须）**。注意：本机现在装的是 Corretto 8，**跑不了这个工程** —— Spring Boot 3.x 最低要求 JDK 17。
  两条路，任选一条：
  1. 直接用 IntelliJ 自带的 JBR 25：`File → Project Structure → SDKs → Add SDK → JDK`，路径填
     `/Applications/IntelliJ IDEA.app/Contents/jbr/Contents/Home`；
  2. 装一个 JDK 21（LTS，长期支持版，面试答这个最稳）。

  另外：在终端里跑 `mvn test` 时，`JAVA_HOME` 也必须指向 17+ 的 JDK，否则编译一样会失败。
- Maven（IDEA 内置了，不用单独装）

## 怎么跑

推荐用 IDEA：

1. `File → Open`，选 `coding-lab/springboot-lab` 目录下的 `pom.xml`，以 Project 方式打开；
2. 等右下角 Maven 把依赖下完（第一次要一两分钟）；
3. 打开 `LabApplication`，点左边的绿色三角运行；
4. 控制台出现 `Tomcat started on port 8080` 就成了。

命令行（装了 Maven 的话）：

```bash
cd coding-lab/springboot-lab        # 在仓库根目录下执行（仓库：codex-interview-handoff）
mvn spring-boot:run
```

跑测试：

```bash
mvn test
```

> 测试用的是固定端口 8080。跑测试前先把运行中的服务停掉，不然端口冲突。

## 先读哪里（推荐顺序）

这个工程的文件有点多，按下面四遍读，最省时间：

**第一遍 · 跑起来看现象（5 分钟）**
直接跑 `LabApplication`（默认内存实现，零依赖），打开 `requests.http` 跑实验二、实验三、实验四点五。
先把"什么情况下会被拦住、拦住长什么样"看进眼里，再回头看代码。

**第二遍 · 读主链路（20 分钟，按调用顺序）**

1. `web/ContractController.java` —— 入口，每个方法只有一行，看清有哪几个演示接口。
2. `service/ContractPushService.java` —— **核心，先只读 `push()` 这一个方法**。两道闸、三种回执分支都在这儿。
3. `idempotent/IdempotentStore.java` → `RedisIdempotentStore.java` —— 第一道闸。重点看 `setIfAbsent` 和那段 Lua 释放脚本。
4. `repository/PushRecordRepository.java` → `JdbcPushRecordRepository.java` —— 第二道闸。重点看 `INSERT` 撞唯一索引后 catch `DuplicateKeyException`。
5. `client/KdxClient.java` + `web/MockKdxController.java` —— 对方那一侧。异常怎么翻译、坏行为怎么模拟。
6. `service/TokenService.java` —— 旁路：凭证缓存（跟你面试答的 token 复用是同一件事）。
7. `service/PushRecordReconciler.java` —— 最外层兜底：卡住的记录靠它查单收尾。

**第三遍 · 切成真中间件（10 分钟）**
用 `infra` profile 启动，先访问 `GET /api/infra` 自检，再重跑一遍实验。同一套代码，换成真 Redis 和真 MySQL，行为对比着看。

**第四遍 · 读测试（15 分钟）**
测试是最好的规格说明书，直接告诉你"什么情况必须发生什么"：

| 测试 | 覆盖什么 |
|---|---|
| `IdempotentPushTest`（5 个） | 串行幂等、20 并发幂等、对方幂等兜底、超时查单、401 自愈 |
| `PushRecordJdbcTest`（3 个） | 唯一索引挡第二次插入、状态流转、对账扫描只捞卡住的记录 |
| `RedisIdempotentStoreIT`（4 个） | 真 Redis 上的 SETNX 语义、DONE 不释放、Lua 不误删、50 线程只有 1 个抢到 |

> 注意 `*IT` 结尾的测试默认不跑（Maven 只把 `*Test` 交给 surefire），要显式指定：
> `mvn test -Dtest=RedisIdempotentStoreIT`。这样设计是有意的 ——
> 别人 clone 代码跑 `mvn test` 时，不会因为自己机器上没装 Redis 就失败。

## 两层幂等：这个工程的核心

```
请求进来
   │
   ├─ ① IdempotentStore（Redis SETNX + TTL）      ← 挡流量：快，但会过期/会丢
   │      抢不到 → SKIPPED_BY_CACHE，不打对方接口
   │
   ├─ ② PushRecordRepository（MySQL 唯一索引）    ← 保正确性：缓存失效时的最终兜底
   │      插不进去 → BLOCKED_BY_DB，说明缓存曾经失效过（这个计数 > 0 就是告警信号）
   │
   ├─ ③ 调对方接口
   │      成功            → 两层都记 DONE
   │      对方报重复受理  → 也记 DONE，但日志告警"本地漏了一环"
   │      明确失败        → 释放缓存占位 + 台账 FAILED
   │      超时（未知状态）→ 先查单；查不到就留 IN_PROGRESS，交给 ④
   │
   └─ ④ PushRecordReconciler（定时对账）          ← 保证不漏推
          卡住超过 N 分钟的记录 → 查单 → 补记 DONE 或标记 FAILED 转人工
```

四段话对应面试四个追问：**怎么防重（①）、缓存挂了怎么办（②）、超时算成功还是失败（③）、漏推了谁发现（④）**。

## 用真 Redis + 真 MySQL 跑（infra profile）

1. Redis 起着，MySQL 起着（本机 3306 已经有了）。
2. 设密码并启动（密码走环境变量，不写进代码库）：

```bash
cd coding-lab/springboot-lab        # 在仓库根目录下执行（仓库：codex-interview-handoff）
export MYSQL_PASSWORD='你的MySQL密码'
mvn spring-boot:run -Dspring-boot.run.profiles=infra
```

   或者用 IDEA：Run/Debug Configurations → Spring Boot → `Active profiles` 填 `infra`，
   环境变量里加一条 `MYSQL_PASSWORD=你的密码`。

3. 启动后先自检：`GET http://localhost:8080/api/infra`，应该看到
   `idempotentStore = redis(prefix=oa:push:)`、`pushRecordRepository = mysql(push_record)`、
   `redisPing = PONG`、`mysql = 8.4.x / 当前库=interview_lab`。
4. 建库建表不用你手动做：连接串里带了 `createDatabaseIfNotExist=true`，表由 `schema.sql` 在启动时自动建。
5. 想回到零依赖模式：不带 `infra` profile 直接启动即可。

## 工程结构

| 文件 | 作用 |
|---|---|
| `pom.xml` | 依赖与打包方式，注释里写了每个 starter 干什么 |
| `LabApplication` | 启动类，注释里写了 `@SpringBootApplication` 的三层含义 |
| `resources/application.yml` | 配置：端口、actuator、以及 `kdx.*` 那一组 |
| `config/KdxProperties` | 把 `kdx.*` 绑成对象，说明为什么不用 `@Value` |
| `config/RestClientConfig` | RestTemplate + 超时，注释里写了不设超时会怎么挂 |
| `service/TokenService` | **凭证缓存**：单例 + synchronized + 提前刷新 |
| `idempotent/IdempotentStore` + `RedisIdempotentStore` + `InMemoryIdempotentStore` | **第一道闸**：缓存占位（真 Redis / 内存两种实现，由配置切换） |
| `repository/PushRecordRepository` + `JdbcPushRecordRepository` + `InMemoryPushRecordRepository` | **第二道闸**：数据库唯一索引兜底 |
| `service/ContractPushService` | **幂等推送主流程**：两道闸 → 推送 → 三种回执分支 |
| `service/PushRecordReconciler` | **定时对账**：卡住的记录查单收尾，保证不漏推 |
| `config/InfraConfig` | 条件装配：按 `lab.store` / `lab.repository` 决定用哪个实现；手写 DataSource 取代被排除的自动配置 |
| `web/InfraController` | 环境自检：当前连的是内存还是真中间件，一眼看清 |
| `resources/schema.sql`、`application-infra.yml` | 建表脚本 + 真中间件配置 |
| `client/KdxClient` | 调用对方：异常翻译、401 自愈、查单接口 |
| `web/MockKdxController` | 假金蝶：能模拟 401、超时、重复报错 |
| `web/ContractController`、`web/TokenController` | 演示入口 |
| `web/GlobalExceptionHandler` | 统一异常返回 |
| `test/` 下三个测试类 | 共 12 个用例：5 个业务 + 3 个数据库 + 4 个真 Redis |
| `requests.http` | IDEA 里点一下就发的请求脚本 |

## 八个实验（对着 `requests.http` 从实验零往下点）

编号和 `requests.http` 完全一致，不用记顺序，顺着数字点就行。

### 实验零 · 环境自检

`GET /api/infra`，先确认你现在连的到底是内存还是真中间件。默认启动是 memory + memory；
带 `infra` profile 启动才是 redis + mysql。

### 实验一 · 凭证缓存

连续点 5 次 `POST /api/token/warm`，再看 `GET /api/token/state`：
`refreshCount` 仍然是 **1** —— 说明换了一次之后一直在复用。

再跑 `POST /api/token/parallel?threads=50`：50 个线程同时第一次取 token，
`refreshCount` 只从 0 变成 **1**。这就是你简历里"鉴权复用"要能讲清的效果：
"判断是否过期写在锁内，所以第二个线程进来看到的是已经换好的新凭证，不会重复换取。"

### 实验二 · 对方提前吊销凭证 → 401 自愈

先推一张单（把 token 缓存住），再 `POST /mock/kdx/revoke-tokens` 让对方的凭证全部失效，
然后推一张**新单**：会带着"本地以为有效、对方认为无效"的旧 token 打过去，
拿到 401 → 清缓存 → 换一次 → 重试一次 → 成功。要点是**只重试一次**。

### 实验三 · 串行重复推送

`POST /api/contracts/HT20250001/push` 两次。第二次返回 `SKIPPED`，
对方统计 `totalReceived` 仍是 **1** —— 第二次根本没打到对方接口。

### 实验四 · 20 个线程并发推同一张单

`POST /api/contracts/HT20250002/push-parallel?threads=20`。
返回里 `upstreamCalls=1`、`skippedByLocalLedger=19`，对方 `totalReceived=1`。

这一条就是你面试时的答案："20 个并发请求，只有一个抢到占位，其余 19 个在本地就被拦下。"

### 实验五 · 两层闸：Redis 丢了，数据库兜住（★ 最值得亲手跑的一条）

三步：`push` → `POST /api/reset-cache-only`（只清缓存，保留数据库台账）→ 再 `push` 同一张单。
第三步会返回 `BLOCKED_BY_DB`。这一跑就是"你的幂等是不是只靠 Redis"这个问题的完整答案 ——
面试时可以当场演示。

### 实验六 · 把对方的"坏行为"打开

| 开关 | 现象 | 你要能说的那句话 |
|---|---|---|
| `dedupByBizId=false` + 本侧台账被清 | 对方报 409，本侧返回 `FAILED` | 本侧台账一丢，重复就真打到对方了 —— 所以台账不能是唯一防线 |
| `dedupByBizId=true` + 本侧台账被清 | 返回 `DUPLICATE_ACCEPTED` | 对方按单号幂等替我们兜住了；但日志要告警，因为本地漏了一环 |
| `timeoutAfterSuccess=true` | 1.5 秒后返回 `TIMEOUT_CONFIRMED` | 超时是未知状态，不直接重试，先调查单接口确认 |

### 实验七 · 健康检查

`GET /actuator/health` 返回 `UP`。上线后监控系统探的就是这个地址。

## 面试时怎么讲这个工程

> "我自己搭了个 Spring Boot 工程来验证跨系统推送的幂等。里面有一个我自己写的 mock 端模拟对方的接口，可以打开几种坏行为：返回 401、响应超时、同一单号报重复。
> 我验证下来有三个结论：第一，先查后写在并发下会重复推，必须用 SETNX 那种原子占位；第二，顺序必须是先占位再推送，先推后记账只要中间崩一次就会重复；第三，超时属于未知状态，直接重试是赌运气，要先查单。
> 另外我在工程里配了连接超时和读超时 —— 不配的话对方挂了会把我的线程全挂住，这是故障扩散的典型。"

## 这个工程还能继续加什么

- 把本地台账从 `ConcurrentHashMap` 换成真的 Redis（`setIfAbsent` + TTL），对比行为差异；
- 加一张 `push_record` 表 + 唯一索引，把"最终兜底"也做出来；
- 加 `@Scheduled` 定时对账任务，扫描 `IN_PROGRESS` 超时的记录去查单；
- 接上 MySQL（用 `coding-lab/mysql-index-lab` 那个容器），把推送记录真的落库。


---

## 模块二 · 统一待办聚合（JUC 线程池 + 超时降级）

位置：`src/main/java/com/wufeng/lab/pending/`。这一块补的是简历上"Java 并发 / JUC"的落点。

### 它解决什么问题

门户首页要展示 4 个业务系统（OA / ERP / HR / FIN）的待办。

- 串行调用：4 次 HTTP 首尾相接，总耗时 = 各上游之和，任一上游抖动整页跟着卡；
- 并发调用：总耗时 ≈ 最慢的那个上游，单个上游故障只降级它自己。

### 文件说明

| 文件 | 作用 |
|---|---|
| `PendingConfig` | **7 个参数手写 `ThreadPoolExecutor`** + 专用 `RestTemplate`（独立超时） |
| `PendingAggregationService` | 主角：并发提交 → 按整页时间预算收结果 → 单个上游降级 |
| `PendingController` | `/api/pending/aggregate`、`/api/pending/pool` |
| `MockPendingUpstreamController` | 假上游，可切 `OK / SLOW / FAIL / STUCK` 四种行为 |
| `NamedThreadFactory` | 给线程起名（`pending-1`），排查时能认出来 |
| `CountingCallerRunsPolicy` | 自定义拒绝策略：CallerRuns + 拒绝次数计数 |
| `PendingProperties` | `pending.*` 配置绑定 |

### 怎么跑

启动工程后，对着 `requests.http` 的**实验八**从 8.1 往下点。重点看两个数字：
`totalCostMillis`（整页耗时）和 `degradedSystems`（降了哪几个）。

### 四个必须能答出来的点

1. **线程池参数怎么定**：核心线程数按上游数量给（`N * (1 + 等待/计算)`，纯 IO 等待）；队列必须有界，否则 `maximumPoolSize` 失效、任务堆到 OOM。
2. **超时怎么算**：用"整页时间预算"（统一的 deadline），不是每个上游各等一遍 `timeout` —— 否则 4 个上游最坏就是 4 × timeout，"整页 250ms"就是假的。
3. **超时之后那个任务停了吗**：`future.cancel(true)` 只是发中断信号；`RestTemplate` 阻塞在 socket 读上，中断不保证立刻生效，它可能还要等到读超时才退出。（`PendingAggregationService` 里 `fetch()` 的注释写了原因。）
4. **一个上游挂了怎么办**：只降级该上游，其余照常返回，绝不整页失败。这是"可用性优先"的取舍。

### 面试时怎么讲

> "我在自己的 Spring Boot 工程里做了一次统一待办聚合的改造实验：门户要拉 4 个系统的待办，原来是串行，光上游就 400 多毫秒。
> 我用手写的 `ThreadPoolExecutor` 做并发，核心线程数按上游数量给 4、队列用有界的 `ArrayBlockingQueue`、拒绝策略用 `CallerRunsPolicy` 并加了拒绝计数。
> 超时我用的是整页时间预算，而不是每个上游各等一次 —— 这点很多人写错。超时之后只降级那一个系统，其余照常展示。
> 我还专门验证了 `cancel(true)` 并不能立刻停掉阻塞在 socket 上的任务，所以那个线程要等到读超时才会真正退出。"
