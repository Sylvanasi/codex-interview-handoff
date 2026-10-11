package com.wufeng.lab.service;

import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.wufeng.lab.config.KdxProperties;
import com.wufeng.lab.dto.TokenView;

/**
 * 凭证（token）缓存服务 —— 把简历里那句"token 周期性缓存与鉴权复用"变成能跑的代码。
 *
 * 【要解决什么问题】
 * 每次调对方接口都要先换 token，换 token 是一次额外的 HTTP 往返。
 * 每次业务调用都换一次，延迟翻倍、对方也可能限流。
 * 做法：换一次、缓存起来、在有效期内复用。
 *
 * 【三个必须能答出来的细节】
 * 1. 缓存放哪里？
 *    这里是进程内的字段（等价于你项目里的单例静态变量）。边界是：
 *    多实例部署时每个节点各存一份、各换一次；如果对方对同一 appKey 的并发换取有限制，
 *    就要把 token 挪到 Redis 共享，并用分布式锁保证同一时刻只有一个节点去换。
 * 2. 过期时间怎么定？
 *    用对方返回的 expires_in 减去一段安全余量（refreshAheadSeconds）。
 *    不要卡着最后一秒用，也不要写死一个拍脑袋的值。
 * 3. 并发怎么办？
 *    整个 getToken() 加 synchronized：同一时刻只有一个线程能进方法体，其它线程在外面排队。
 *    关键在于"是否过期"这个判断写在锁内部，所以第二个线程进来时看到的已经是新 token，
 *    不会重复换取。（判断写在锁外面，就会退化成写错的双重检查，照样重复换。）
 */
@Service
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);

    private final RestTemplate restTemplate;
    private final KdxProperties props;

    /** 缓存的 token。读写都在 synchronized 方法里，所以不需要加 volatile。 */
    private String cachedToken;

    /** 本地认为的失效时刻（毫秒时间戳），已经扣掉了安全余量。 */
    private long expireAtMillis;

    /** 真正向对方换取过几次 —— 这是验证"缓存有没有生效"的关键指标。 */
    private final AtomicInteger refreshCount = new AtomicInteger();

    public TokenService(RestTemplate kdxRestTemplate, KdxProperties props) {
        this.restTemplate = kdxRestTemplate;
        this.props = props;
    }

    /**
     * 拿一个可用的 token：有效就用缓存，无效就换一次。
     * synchronized 加在方法上，锁对象是 this，也就是这个单例 Bean。
     */
    public synchronized String getToken() {
        long now = System.currentTimeMillis();

        // ① 缓存还在有效期内：直接复用。绝大多数请求走的是这个分支
        if (cachedToken != null && now < expireAtMillis) {
            log.debug("复用缓存的 token，还剩 {} 毫秒", expireAtMillis - now);
            return cachedToken;
        }

        // ② 没有缓存或已过期：换取一次。整段在锁内，不会有两个线程同时换
        log.info("token 不存在或已过期，向对方换取新凭证（这将是第 {} 次真实换取）", refreshCount.get() + 1);
        TokenView view = restTemplate.postForObject(
                props.getBaseUrl() + "/token?appKey={appKey}&appSecret={appSecret}",
                null,
                TokenView.class,
                props.getAppKey(),
                props.getAppSecret());

        if (view == null || view.token() == null || view.token().isBlank()) {
            throw new IllegalStateException("对方没有返回有效 token，需要告警");
        }

        long ttl = view.expiresIn() > 0 ? view.expiresIn() : props.getTokenTtlSeconds();
        long validFor = Math.max(1, ttl - props.getTokenRefreshAheadSeconds());

        this.cachedToken = view.token();
        this.expireAtMillis = System.currentTimeMillis() + validFor * 1000L;
        this.refreshCount.incrementAndGet();

        log.info("换取成功，token={}，本地有效期 {} 秒（对方给 {} 秒，提前 {} 秒刷新）",
                mask(cachedToken), validFor, ttl, props.getTokenRefreshAheadSeconds());
        return cachedToken;
    }

    /**
     * 主动丢弃缓存。
     * 什么时候用？业务接口返回"鉴权失败"时 —— 说明对方认为这个 token 已经无效了
     * （对方重启、主动吊销、时钟漂移都可能造成）。这时清掉本地缓存，下次重新换。
     * 完整做法还要配合"原请求重试一次"，但只重试一次，防止打转。
     */
    public synchronized void invalidate() {
        log.warn("本地 token 缓存被主动清除，下次调用会重新换取");
        this.cachedToken = null;
        this.expireAtMillis = 0;
    }

    /** 给演示接口看内部状态：有没有缓存、还剩多久、真实换取过几次。 */
    public synchronized String describeState() {
        long now = System.currentTimeMillis();
        if (cachedToken == null) {
            return "当前没有缓存 token；累计真实换取次数=" + refreshCount.get();
        }
        return "当前 token=" + mask(cachedToken)
                + "，剩余有效 " + Math.max(0, expireAtMillis - now) + " 毫秒"
                + "，累计真实换取次数=" + refreshCount.get();
    }

    public int refreshCount() {
        return refreshCount.get();
    }

    /** 日志里不打完整凭证，这是最基本的安全习惯。 */
    private String mask(String token) {
        if (token == null || token.length() <= 8) {
            return "***";
        }
        return token.substring(0, 4) + "****" + token.substring(token.length() - 4);
    }
}
