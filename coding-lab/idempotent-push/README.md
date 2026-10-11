# 幂等推送实验室（Idempotent Push Lab）

场景来源：深圳巴士集团 · OA 合同管理与金蝶云星瀚集成 ——  OA 审批完成后要把合同台账推给金蝶。推送重试、超时重发、用户重复点提交，都会让同一张单进对方系统两次。

这个实验室只用 JDK，**不需要装 Redis**：`SetnxStore` 用 `ConcurrentHashMap` 的 CAS 精确模拟了 Redis `SET key val NX EX ttl` 的语义。要换成真 Redis，只改一个类的实现（见文末）。

## 怎么跑

```bash
cd "/Users/wufeng/Documents/ChatGPT/面试/coding-lab/idempotent-push"
./run.sh
```

本机没装 JDK，脚本默认用 IntelliJ 自带的 JBR（JDK 25）；自己装了 JDK 就 `export JAVA_HOME=/path/to/jdk` 再跑。整跑不到 5 秒。

## 四个关卡，每关都在回答一个面试追问

| 关卡 | 看什么 | 面试官问的是 |
|---|---|---|
| 1 并发窗口 | 20 个线程同时推同一张单，先查后写会重复推 20 次，SETNX 只推 1 次 | "你不是说加了判断吗，怎么还会重复？" |
| 2 顺序 | 先推再记账，推成功后崩溃 → 重试又推一次；先占位再推 → 重试被拦住 | "如果推送成功了但你这边的标记没写成功呢？" |
| 3 超时 | 对方处理成功但响应超时，直接重试会撞重复；两条兜底：对方按单号幂等、本侧查单确认 | "超时了你怎么知道对方到底收没收到？" |
| 4 TTL | TTL 到期后缓存拦不住，第二次照样推过去；最终兜底是 DB 唯一约束 | "Redis 里那条记录没了呢？" |

## 三句话（背下来，面试直接说）

1. 幂等键选**业务单号**（合同编号 / 采购单号），不能选自增主键或时间戳；落在 Redis 用 `SETNX` 占位 + TTL。
2. 顺序是**先占位、再推送、成功了才记 DONE**；明确失败一定要释放占位，否则会漏推。
3. **超时是未知状态**，先查单再决定要不要重试；Redis 只是快速拦截层，最终兜底靠 DB 唯一约束或幂等状态表。

## 作业

1. 把 `关卡 1.1` 的 `CheckThenActStore` 改成"查完再查一次"（双重检查），跑一遍看重复次数有没有变 —— 说清为什么它仍然挡不住。
2. 把 `关卡 2.2` 的 TTL 从 60 秒改成 3 秒，让第一次推送后占位过期，再重试一次，看对方收到几次。回答：TTL 该按什么定？
3. 给 `PushService` 加一个"3 次指数退避重试"，只在**明确失败**（FAIL）时重试，TIMEOUT 不重试。跑通后回答：为什么 TIMEOUT 不重试？

## 换成真 Redis 时要改的地方

```java
// tryAcquire：SETNX + TTL，一条命令搞定，不能拆成 exists + set
String ok = jedis.set(key, "IN_PROGRESS", SetParams.setParams().nx().ex(ttlSeconds));
return "OK".equals(ok);
// markDone / release：改成写 DONE 标记 / DEL
```

面试话术里要主动点破一句话：**"先 exists 再 set 等于人工制造了并发窗口，必须用 SETNX 这种原子指令。"**
