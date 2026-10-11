package com.wufeng.lab.repository;

import java.util.List;
import java.util.Map;

/**
 * 推送台账（数据库版幂等），也就是"最终兜底"那一层。
 *
 * 为什么缓存之外还要有它？因为缓存会失信：
 * TTL 到期、Redis 重启、被运维清空、内存淘汰、多机房各写各的……
 * 数据库的 UNIQUE 约束是硬约束：同一个 biz_id 只允许存在一行，两个并发 INSERT 必有一个报错。
 *
 * 状态流转：
 * IN_PROGRESS(1) 正在推，或者推完没来得及更新（崩溃、超时）
 * DONE(2)        对方确认受理
 * FAILED(3)      明确失败或对账后判定异常，需要人工看
 */
public interface PushRecordRepository {

    int IN_PROGRESS = 1;
    int DONE = 2;
    int FAILED = 3;

    /** 落一条"进行中"的记录。返回 false 表示这条 biz_id 已经存在（撞唯一索引）—— 即判定为重复。 */
    boolean insertIfAbsent(String bizId);

    void markDone(String bizId);

    void markFailed(String bizId, String reason);

    boolean exists(String bizId);

    /** 捞出"卡在进行中"的记录，交给对账任务去查单收尾。 */
    List<String> findStuck(int stuckMinutes);

    int count();

    /** 列出台账里的记录（最多 100 条），给演示接口用。 */
    List<Map<String, Object>> listAll();

    String backend();

    void clear();
}
