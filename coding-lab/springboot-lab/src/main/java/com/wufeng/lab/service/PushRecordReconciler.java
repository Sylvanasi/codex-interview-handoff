package com.wufeng.lab.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.wufeng.lab.client.KdxClient;
import com.wufeng.lab.config.LabProperties;
import com.wufeng.lab.idempotent.IdempotentStore;
import com.wufeng.lab.repository.PushRecordRepository;

/**
 * 对账任务 —— 整个幂等方案里最容易漏、也最容易被面试官追问的一环。
 *
 * 它解决什么问题？
 * 超时、进程被 kill、网络闪断，都会留下"我不知道对方到底收到没有"的记录。
 * 这些记录状态是 IN_PROGRESS，既不能当成功也不能当失败，卡在那里就是业务上的僵死单。
 *
 * 它怎么做？
 * 定期扫出"进行中且超过 N 分钟没更新"的记录，调对方查单接口问清楚：
 * 对方有这张单，置 DONE（我们只是没记上，不是没推成功）；
 * 对方没有，置 FAILED，转人工或允许重新推送。
 *
 * 面试话术：
 * "跨系统一致性我不强求实时一致，只保证最终一致：正常路径靠幂等键加唯一索引，
 * 异常路径靠对账任务兜底。幂等保证不重复推送，对账保证不漏推，这是两个问题，要分开解决。"
 */
@Component
public class PushRecordReconciler {

    private static final Logger log = LoggerFactory.getLogger(PushRecordReconciler.class);

    private final PushRecordRepository repository;
    private final IdempotentStore store;
    private final KdxClient kdxClient;
    private final LabProperties props;

    public PushRecordReconciler(PushRecordRepository repository,
                                IdempotentStore store,
                                KdxClient kdxClient,
                                LabProperties props) {
        this.repository = repository;
        this.store = store;
        this.kdxClient = kdxClient;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "${lab.reconcile-interval-millis:60000}", initialDelayString = "15000")
    public void reconcile() {
        List<String> stuck = repository.findStuck((int) props.getStuckMinutes());
        if (stuck.isEmpty()) {
            log.debug("对账任务：没有卡在进行中的记录");
            return;
        }
        log.warn("对账任务：发现 {} 条超时未确认的推送记录 {}", stuck.size(), stuck);
        for (String bizId : stuck) {
            try {
                if (kdxClient.ledgerExists(bizId)) {
                    repository.markDone(bizId);
                    store.markDone(bizId, props.getDedupTtlSeconds());
                    log.info("对账结果 bizId={}：对方已受理，本地补记为 DONE", bizId);
                } else {
                    repository.markFailed(bizId, "对账：对方查无此单");
                    // 对账是系统级清理，不属于任何一次业务调用，所以用 forceRelease
                    store.forceRelease(bizId);
                    log.warn("对账结果 bizId={}：对方没有这张单，标记为 FAILED 待人工处理", bizId);
                }
            } catch (RuntimeException e) {
                // 对账时对方也不可用：什么都别做，下一轮再来。绝不能因为查不到就判定失败。
                log.error("对账查单失败 bizId={}，本轮跳过，等下一轮", bizId, e);
            }
        }
    }
}
