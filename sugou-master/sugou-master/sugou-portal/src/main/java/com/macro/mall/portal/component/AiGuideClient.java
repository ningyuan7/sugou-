package com.macro.mall.portal.component;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * AI 导购服务客户端
 * 通过 Resilience4j 实现三层防护：
 * - 熔断降级：失败率超阈值自动熔断，半开状态探测恢复
 * - 接口超时：普通对话 65s / 流式对话 120s 分级超时
 * - 失败重试：针对网络异常最多重试 3 次，间隔 1s
 */
@Slf4j
@Component
public class AiGuideClient {

    @Value("${ai.guide.base-url}")
    private String baseUrl;

    @Value("${ai.guide.chat.endpoint}")
    private String chatEndpoint;

    @Value("${ai.guide.chat.stream-endpoint}")
    private String streamEndpoint;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 普通对话（熔断 + 重试 + 超时）
     *
     * @param message   用户消息
     * @param sessionId 会话ID（可为 null）
     * @return AI 回复内容
     */
    @CircuitBreaker(name = "ai-guide-chat", fallbackMethod = "chatFallback")
    @Retry(name = "ai-guide-chat")
    @TimeLimiter(name = "ai-guide-chat")
    public CompletableFuture<String> chat(String message, String sessionId) {
        String url = baseUrl + chatEndpoint;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = new HashMap<>();
        body.put("message", message);
        body.put("session_id", sessionId != null ? sessionId : "");

        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
        return CompletableFuture.completedFuture(response.getBody());
    }

    /**
     * 流式对话（熔断 + 超时，不重试以避免重复流）
     *
     * @param message   用户消息
     * @param sessionId 会话ID（可为 null）
     * @return 流式响应内容
     */
    @CircuitBreaker(name = "ai-guide-stream", fallbackMethod = "chatFallback")
    @TimeLimiter(name = "ai-guide-stream")
    public CompletableFuture<String> chatStream(String message, String sessionId) {
        String url = baseUrl + streamEndpoint;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = new HashMap<>();
        body.put("message", message);
        body.put("session_id", sessionId != null ? sessionId : "");

        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
        return CompletableFuture.completedFuture(response.getBody());
    }

    /**
     * 降级兜底方法
     * 当 AI 服务熔断或超时时返回友好提示，而非直接抛异常
     */
    private CompletableFuture<String> chatFallback(Throwable t) {
        log.warn("AI导购服务降级，原因: {}", t.getMessage());
        return CompletableFuture.completedFuture(
                "{\"reply\": \"AI导购服务暂时不可用，请稍后再试\", \"fallback\": true}"
        );
    }
}
