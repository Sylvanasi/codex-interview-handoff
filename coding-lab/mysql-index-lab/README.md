# MySQL 索引实验室（Docker 版）

## 前置

Docker Desktop 装好并启动（`docker ps` 能跑通即可）。不需要本机装 MySQL。

## 一键跑

```bash
cd "/Users/wufeng/Documents/ChatGPT/面试/coding-lab/mysql-index-lab"
./run.sh
```

第一次会拉 `mysql:8.0` 镜像，之后就是秒级。

## 场景

合同台账表 `contract_ledger`，50 万行。门户列表页的查询是"按部门 + 状态 + 时间范围筛，再按签署时间倒序分页取 20 条"——这是 OA 系统里最典型、也最容易慢的一类查询。

索引列顺序的依据：`dept_id`、`status` 是**等值条件**放前面，`sign_date` 既是**范围**又是**排序**放最后。这条判断就是"最左匹配 + 等值在前范围在后"的实际用法。

## 对照着看什么

不要只看时间，重点看 `EXPLAIN` 的四个字段：

| 字段 | 加索引前 | 加索引后 | 说明 |
|---|---|---|---|
| `type` | `ALL` | `ref` / `range` | 访问类型。`ALL` 是全表扫描，`range` 是范围扫描，`ref` 是等值匹配。生产上至少要 `range` |
| `key` | `NULL` | `idx_ledger_dept_status_date` | 实际用到的索引。`NULL` 表示没走索引 |
| `rows` | 约 50 万 | 几百 | 预估扫描行数，这是最能说明问题的数字 |
| `Extra` | 含 `Using filesort` | `filesort` 消失 | `Using filesort` = 额外排序开销；`Using index` = 覆盖索引没有回表 |

`EXPLAIN ANALYZE`（MySQL 8.0.18+）会把**实际**扫描行数和耗时打出来，比 `EXPLAIN` 的预估值更可信，两个都留着对比。

## 用图形工具连上看

IntelliJ IDEA 自带的 Database 面板、DBeaver、Navicat 都行：

| 项 | 值 |
|---|---|
| Host | `127.0.0.1` |
| Port | `3307` |
| User | `root` |
| Password | `interview123` |
| Database | `interview_lab` |

用图形工具自己手敲 `EXPLAIN` 再跑一遍，比看脚本输出记得牢。

## 三道作业

1. **把 `05-counter-examples.sql` 里的反例逐条跑一遍**，用一句话说清每条为什么失效（提示：函数运算、`LIKE '%x'`、缺最左列，三个原因各不相同）。
2. **把索引列顺序改错一次**：删掉现有索引，建 `(sign_date, dept_id, status)`，再跑 `04-after.sql` 看 `rows` 和 `type` 变成什么。然后回答：为什么这次没生效？
3. **加第二个索引**：如果业务上还要按"经办人 + 状态"查，你会在现有索引上加列，还是新建一个索引？为什么？（提示：索引不是越多越好，想想写入成本）

## 常见问题

| 现象 | 处理 |
|---|---|
| `Bind for 0.0.0.0:3307 failed` | 3307 被占用了，改 `docker-compose.yml` 里的 `"3307:3306"` 为 `"3308:3306"` |
| `EXPLAIN ANALYZE` 报语法错误 | 镜像版本低于 8.0.18，把镜像换成 `mysql:8.4` |
| 容器起来了但连不上 | `docker logs interview-mysql` 看日志；首次启动初始化要十几秒 |
| 想重造数据 | 直接重跑 `./run.sh`（会 `DROP TABLE` 重建） |
| 彻底清理 | `docker compose down -v`（连数据卷一起删） |
| Apple 芯片拉镜像慢 | 正常，`mysql:8.0` 有 arm64 版本，等下载完 |

## 跑完之后

把 `./run.sh` 的输出（尤其 `EXPLAIN` 那几段）发我，我给你写两条东西：面试时能直接讲的描述，以及补进简历的措辞。

> 提醒：实验环境跑出来的数字属于"我练习过"，不等于项目经历。真要写进项目经历，见 `索引与慢SQL-面试过一遍.md` 第 3 节的三条路径。
