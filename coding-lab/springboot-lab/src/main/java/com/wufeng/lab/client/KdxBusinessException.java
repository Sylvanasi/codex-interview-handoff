package com.wufeng.lab.client;

/**
 * 对方明确返回了失败（4xx 参数错、5xx 服务错、业务校验不过）。
 *
 * 这类错误里还要再区分一次，面试时能主动说出来是加分项：
 *   4xx 参数/校验类：重试一百次结果一样，不要重试，落错误表 + 告警，人工介入；
 *   5xx 服务端类  ：对方自己挂了，可以按退避策略有限次重试。
 */
public class KdxBusinessException extends RuntimeException {

    public KdxBusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
