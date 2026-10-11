package com.wufeng.lab.config;

import java.time.Duration;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * HTTP 调用客户端的配置。
 *
 * 【为什么必须显式设置超时？】
 * 这是面试里最容易加分、也最容易出事的一问：
 * RestTemplate 默认用 JDK 的 HttpURLConnection，读写超时默认是"无限等待"。
 * 对方挂了不回你，你的线程就一直挂在那儿；并发一上来，Tomcat 线程池（默认 200）被耗光，
 * 你自己的服务也跟着挂 —— 这就是故障扩散。
 *
 * 所以调用任何外部系统，都必须有：连接超时、读超时、重试策略、熔断降级。
 * 本实验至少把前两个配出来。
 *
 * 【面试点：RestTemplate / RestClient / WebClient 的区别】
 *   RestTemplate：同步阻塞、老牌、处于维护模式（不再加新功能但还能用）；
 *   RestClient  ：Spring 6.1 之后推荐的同步客户端，链式写法更接近 WebClient；
 *   WebClient   ：异步非阻塞，适合高并发 IO 密集场景，但要整条链路都异步才有意义。
 * 回答"我用同步调用 + 显式超时控制"完全没问题，关键是说清为什么这样够用。
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestTemplate kdxRestTemplate(RestTemplateBuilder builder, KdxProperties props) {
        return builder
                .connectTimeout(Duration.ofMillis(props.getConnectTimeoutMillis()))
                .readTimeout(Duration.ofMillis(props.getReadTimeoutMillis()))
                .build();
    }
}
