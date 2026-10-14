package com.wufeng.meeting.web;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestControllerAdvice
public class ApiExceptionHandler {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(ApiExceptionHandler.class);
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String,String>> handle(Exception error) {
        // Lazy Bean失败会包装TODO异常，展开后仍给学习者明确提示。
        for(Throwable cause=error;cause!=null;cause=cause.getCause()) {
            if(cause instanceof UnsupportedOperationException) return ResponseEntity.status(501).body(Map.of("error","TODO_NOT_IMPLEMENTED","message",cause.getMessage()));
        }
        if(error instanceof IllegalArgumentException) return ResponseEntity.badRequest().body(Map.of("error","INVALID_ARGUMENT","message",error.getMessage()));
        if(error instanceof InterruptedException) {Thread.currentThread().interrupt();return ResponseEntity.status(503).body(Map.of("error","INTERRUPTED","message","本次请求已中断并取消剩余任务"));}
        log.error("会议实验执行失败",error);
        // 不把连接串/数据库账号/原始SQL异常暴露在HTTP响应。
        return ResponseEntity.internalServerError().body(Map.of("error","LAB_ERROR","message","实验执行失败，请查看服务端日志"));
    }
}
