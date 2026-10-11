package com.wufeng.lab.client;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import com.wufeng.lab.config.KdxProperties;
import com.wufeng.lab.dto.KdxPushRequest;
import com.wufeng.lab.dto.KdxPushResponse;
import com.wufeng.lab.service.TokenService;

/**
 * 调用金蝶云星瀚的客户端：把"怎么调对方"这件事收在一个类里。
 *
 * 【设计要点】
 * 1. 收口：所有对方接口的细节（URL、鉴权头、超时、异常翻译）都在这里，
 *    Service 层只关心业务语义。以后对方换域名、改鉴权，只改这一个类。
 * 2. 异常翻译：把 RestTemplate 抛的"技术异常"翻译成"业务能理解的异常"。
 *    ResourceAccessException（超时/连不上）→ KdxTimeoutException
 *    RestClientResponseException（对方回了 4xx/5xx）→ KdxBusinessException
 *    这个翻译动作非常关键：上层要靠这个区分"能重试"和"不能重试"。
 * 3. 401 自愈：拿缓存 token 调用时如果对方说"鉴权失败"，清缓存、换一次、只重试一次。
 *    为什么只重试一次？因为如果是密钥错、配置错，无限重试只会把对方打爆。
 */
@Component
public class KdxClient {

    private static final Logger log = LoggerFactory.getLogger(KdxClient.class);

    private final RestTemplate restTemplate;
    private final KdxProperties props;
    private final TokenService tokenService;

    public KdxClient(RestTemplate kdxRestTemplate, KdxProperties props, TokenService tokenService) {
        this.restTemplate = kdxRestTemplate;
        this.props = props;
        this.tokenService = tokenService;
    }

    /**
     * 推送合同台账给金蝶。
     * 注意：这个方法本身不做幂等，幂等是上层 ContractPushService 的职责 —— 职责分开才好测。
     */
    public KdxPushResponse pushLedger(String bizId, String payload) {
        String token = tokenService.getToken();
        try {
            return doPush(bizId, payload, token);
        } catch (HttpClientErrorException e) {
            // 这里用状态码判断，而不是捕获某个具体的异常子类：
            // 不同 Spring 版本对 401 抛出的异常类不完全一致，看状态码永远是对的。
            if (e.getStatusCode().value() == HttpStatus.UNAUTHORIZED.value()) {
                // 对方不认我们缓存的 token：清缓存 → 换一次 → 原请求重试一次（只一次）
                log.warn("对方返回 401，清掉本地 token 缓存后重试一次，bizId={}", bizId);
                tokenService.invalidate();
                try {
                    return doPush(bizId, payload, tokenService.getToken());
                } catch (HttpClientErrorException retryError) {
                    // 换完还是 401：那就不是缓存的问题，多半是密钥错或权限没开通，别再重试
                    throw new KdxBusinessException(
                            "清缓存重试后仍鉴权失败，疑似密钥或权限问题：" + retryError.getStatusCode(), retryError);
                }
            }
            throw new KdxBusinessException(
                    "对方返回 " + e.getStatusCode() + "，响应体=" + e.getResponseBodyAsString(), e);
        } catch (RestClientResponseException e) {
            // 对方明确回了 4xx/5xx：这次确定没成，把状态码和响应体带出去给人看
            throw new KdxBusinessException(
                    "对方返回 " + e.getStatusCode() + "，响应体=" + e.getResponseBodyAsString(), e);
        } catch (ResourceAccessException e) {
            // 超时或连不上：属于"未知状态"，必须和上面的明确失败区分开
            throw new KdxTimeoutException("调用对方超时或连接失败：" + e.getMessage(), e);
        }
    }

    private KdxPushResponse doPush(String bizId, String payload, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        HttpEntity<KdxPushRequest> entity = new HttpEntity<>(new KdxPushRequest(bizId, payload), headers);

        ResponseEntity<KdxPushResponse> response = restTemplate.postForEntity(
                props.getBaseUrl() + "/ledger", entity, KdxPushResponse.class);

        KdxPushResponse body = response.getBody();
        if (body == null) {
            throw new KdxBusinessException("对方返回了空响应体，无法判断结果", null);
        }
        log.debug("推送完成 bizId={}，对方返回 success={} duplicate={}", bizId, body.success(), body.duplicate());
        return body;
    }

    /**
     * 查单接口：问对方"这张单你到底收到没有"。
     * 超时之后不用猜，调它一下就有答案 —— 这是"未知状态先查单"的落地方式。
     */
    public boolean ledgerExists(String bizId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenService.getToken());
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    props.getBaseUrl() + "/ledger/{bizId}",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    Map.class,
                    bizId);
            Map body = response.getBody();
            Object exists = body == null ? null : body.get("exists");
            return Boolean.TRUE.equals(exists);
        } catch (RestClientResponseException e) {
            throw new KdxBusinessException("查单接口返回 " + e.getStatusCode(), e);
        } catch (ResourceAccessException e) {
            throw new KdxTimeoutException("查单接口调用失败：" + e.getMessage(), e);
        }
    }
}
