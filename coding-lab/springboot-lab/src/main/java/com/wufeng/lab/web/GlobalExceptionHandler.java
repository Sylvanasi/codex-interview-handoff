package com.wufeng.lab.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * 全局异常处理。
 *
 * 面试点（"你的接口出错时返回什么？"）：
 * 1. 不要让原始异常堆栈直接暴露给调用方 —— 既有安全风险，调用方也读不懂；
 * 2. 统一返回结构（code + message），前端和上游系统才好按 code 做判断；
 * 3. 日志打全（含堆栈），响应只给要点 —— 排查靠日志，不靠响应体。
 *
 * @RestControllerAdvice 会拦截所有 Controller 抛出的异常，按类型匹配处理方法。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleBadRequest(IllegalArgumentException e) {
        log.warn("参数问题：{}", e.getMessage());
        return body(400, e.getMessage());
    }

    /**
     * 必须显式放行 ResponseStatusException，否则会被下面的 Exception 兜底吞掉。
     *
     * 这是个真实的坑：我们自己在 MockKdxController 里用 ResponseStatusException 抛 401，
     * 但如果这里只写一个 @ExceptionHandler(Exception.class) 兜底，Spring 会优先用这个 advice，
     * 401 就变成 500 了 —— 调用方看到的永远是 500，根本没法针对 401 做"清缓存重试"。
     * 教训：写全局异常处理时，先按"具体异常 → 框架异常 → 兜底 Exception"的顺序把分支补全。
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException e) {
        log.warn("按请求状态返回 {}：{}", e.getStatusCode().value(), e.getReason());
        return ResponseEntity.status(e.getStatusCode())
                .body(body(e.getStatusCode().value(), e.getReason()));
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleOther(Exception e) {
        log.error("未预料到的异常", e);
        return body(500, "服务内部错误，请联系管理员并提供请求时间");
    }

    private Map<String, Object> body(int code, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", code);
        result.put("message", message);
        return result;
    }
}
