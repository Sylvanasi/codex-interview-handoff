package com.wufeng.meeting.config;

import com.wufeng.meeting.service.LearningSteps;
import org.springframework.context.annotation.*;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class NotificationConfig {
    @Bean
    public AtomicInteger rejectedCount() {
        return new AtomicInteger();
    }

    // Lazy让未完成TODO-3时应用也能启动。只有访问并发接口才创建线程池。
    @Bean(destroyMethod = "shutdown")
    @Lazy
    public ThreadPoolExecutor notificationPool(LearningSteps steps, AtomicInteger rejectedCount) {
        return steps.createPool(8, 8, 512, rejectedCount);
    }
}
