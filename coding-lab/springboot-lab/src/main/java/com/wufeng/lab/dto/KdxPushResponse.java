package com.wufeng.lab.dto;

/**
 * 金蝶推台账接口的返回体（本实验里由 MockKdxController 模拟）。
 *
 * success      对方是否已成功受理
 * duplicate    对方识别出这是重复推送并按幂等处理（也算成功，但它不该出现在我们的第一道防线上）
 * code/message 对方的返回码与描述，排查问题时靠它定位
 *
 * duplicate 这个字段的意义：它是"对方替我们兜底"的信号。
 * 如果本地幂等做得好，正常情况下永远不该看到 duplicate=true。
 */
public record KdxPushResponse(boolean success, boolean duplicate, String code, String message) {
}
