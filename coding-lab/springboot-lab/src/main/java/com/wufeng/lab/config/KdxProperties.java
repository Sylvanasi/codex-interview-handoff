package com.wufeng.lab.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 金蝶对接的配置类：把 application.yml 里 kdx.* 那一组配置，绑成一个 Java 对象。
 *
 * 【为什么用 @ConfigurationProperties，而不是到处写 @Value("${kdx.base-url}")？】
 * 1. 成组：一组相关配置聚在一个类里，改配置时知道去哪儿改；
 * 2. 类型安全：yml 里写错类型（比如把端口写成字符串）启动就报错，而不是运行时才炸；
 * 3. 可复用：需要的地方注入这一个 Bean 就行，不用在每个类里重复写七八个 @Value；
 * 4. 可校验：配合 @Validated + @NotBlank 之类的注解，能在启动时就校验必填项。
 *
 * 【面试高频追问："配置怎么区分环境？"】
 * 答：application.yml 放公共配置，application-dev.yml / application-prod.yml 放差异，
 *     启动时用 --spring.profiles.active=prod 指定；敏感信息用环境变量或配置中心，不进代码库。
 */
@ConfigurationProperties(prefix = "kdx")
public class KdxProperties {

    /** 对方系统的地址。注意：真实项目里这个地址在测试/生产环境是不一样的。 */
    private String baseUrl;

    /** 对方分配的应用标识，用于换 token。 */
    private String appKey;

    /** 对方分配的应用密钥。真实项目里这种字段不能明文写在 yml，要放环境变量或配置中心。 */
    private String appSecret;

    /** 凭证有效期（秒）。真实项目里以对方返回的 expires_in 为准，这里做成配置方便实验。 */
    private long tokenTtlSeconds = 300;

    /**
     * 提前刷新时间（秒）。
     * 为什么不等到刚好过期再换？因为"判断没过期"和"请求真正发出去"之间有时间差，
     * 卡着点用会出现"我这刚判断有效，对方一看已经过期"的尴尬，所以提前换。
     */
    private long tokenRefreshAheadSeconds = 60;

    /** 建连超时（毫秒）。连不上就别耗着，快速失败。 */
    private int connectTimeoutMillis = 1000;

    /** 读超时（毫秒）。连上了但对方不回，必须自己设上限，否则线程被挂死。 */
    private int readTimeoutMillis = 1500;

    // ---------- 下面是标准的 getter / setter，Spring Boot 靠它们做绑定 ----------

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getAppKey() { return appKey; }
    public void setAppKey(String appKey) { this.appKey = appKey; }

    public String getAppSecret() { return appSecret; }
    public void setAppSecret(String appSecret) { this.appSecret = appSecret; }

    public long getTokenTtlSeconds() { return tokenTtlSeconds; }
    public void setTokenTtlSeconds(long tokenTtlSeconds) { this.tokenTtlSeconds = tokenTtlSeconds; }

    public long getTokenRefreshAheadSeconds() { return tokenRefreshAheadSeconds; }
    public void setTokenRefreshAheadSeconds(long tokenRefreshAheadSeconds) {
        this.tokenRefreshAheadSeconds = tokenRefreshAheadSeconds;
    }

    public int getConnectTimeoutMillis() { return connectTimeoutMillis; }
    public void setConnectTimeoutMillis(int connectTimeoutMillis) { this.connectTimeoutMillis = connectTimeoutMillis; }

    public int getReadTimeoutMillis() { return readTimeoutMillis; }
    public void setReadTimeoutMillis(int readTimeoutMillis) { this.readTimeoutMillis = readTimeoutMillis; }

}
