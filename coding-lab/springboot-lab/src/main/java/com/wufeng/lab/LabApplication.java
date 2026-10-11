package com.wufeng.lab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 启动类。
 *
 * 【三个必答点】
 *
 * 1. @SpringBootApplication 是三合一的组合注解：
 *    - @SpringBootConfiguration：本质是 @Configuration，表示这是个配置类；
 *    - @EnableAutoConfiguration：自动配置的总开关。它会扫描 classpath 上的
 *      META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports，
 *      发现你引了 spring-boot-starter-web，就自动给你配好内嵌 Tomcat、DispatcherServlet、
 *      Jackson 消息转换器。这就是"约定优于配置"。
 *    - @ComponentScan：从当前包往下扫 @Component / @Service / @Controller / @Configuration。
 *
 * 2. 启动类为什么要放在最外层的包里？
 *    因为 @ComponentScan 默认扫的是"启动类所在包及其子包"。
 *    如果你把启动类放到 com.wufeng.lab.web 下，com.wufeng.lab.service 里的 @Service
 *    就永远扫不到，启动时报"找不到 Bean"——这是新手最常见的坑之一。
 *
 * 3. @ConfigurationPropertiesScan 是干什么的？
 *    让 @ConfigurationProperties 标注的配置类自动注册成 Bean，
 *    省掉在每个配置类上写 @EnableConfigurationProperties 或者 @Component。
 */
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
@ConfigurationPropertiesScan
@EnableScheduling
public class LabApplication {

    public static void main(String[] args) {
        SpringApplication.run(LabApplication.class, args);
    }
}
