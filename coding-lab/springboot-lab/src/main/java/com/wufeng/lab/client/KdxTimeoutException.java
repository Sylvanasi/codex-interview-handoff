package com.wufeng.lab.client;

/**
 * 调用对方接口"超时/连不上"时抛出的异常。
 *
 * 为什么把"超时"和"对方明确返回失败"分成两个异常？因为语义完全不同：
 *   明确失败（4xx/5xx）：我知道这次没成，可以重试；
 *   超时             ：我根本不知道对方收没收到，重试可能造成重复，必须先查单。
 * 分不开这两件事，就会出现"一超时就重试，结果推了两遍"。
 */
public class KdxTimeoutException extends RuntimeException {

    public KdxTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
