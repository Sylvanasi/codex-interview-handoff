package com.wufeng.lab.dto;

/**
 * 推送给金蝶的请求体。
 *
 * bizId 就是业务单号 —— 整条链路的幂等键、对账键、排查时的检索键。
 *
 * 为什么不选自增主键或时间戳做幂等键？
 * 自增主键在不同系统里根本不是同一个值；时间戳在每次重试时都不同，拿它做幂等键等于没有幂等。
 * 只有业务单号在重试、重推、跨系统时都保持不变。
 */
public record KdxPushRequest(String bizId, String payload) {
}
