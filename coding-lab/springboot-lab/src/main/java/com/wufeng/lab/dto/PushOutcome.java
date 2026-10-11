package com.wufeng.lab.dto;

/**
 * 本侧推送服务的结果，给接口调用方看。
 *
 * state 的取值，每一个都对应一种真实发生过的情形：
 *   SUCCESS            推送成功
 *   SKIPPED            本地幂等拦截，根本没打对方接口（这是最健康的状态）
 *   DUPLICATE_ACCEPTED 对方报重复但按幂等受理了（说明本地幂等漏了一环）
 *   TIMEOUT_CONFIRMED  超时后查单，确认对方已受理
 *   TIMEOUT_UNKNOWN    超时且查不到，需要人工或对账处理
 *   FAILED             对方明确返回失败
 */
public record PushOutcome(String bizId, String state, String detail) {
}
