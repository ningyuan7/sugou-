package com.macro.mall.gateway.component;

import cn.hutool.core.util.StrUtil;
import com.macro.mall.gateway.config.IgnoreUrlsConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;

/**
 * JWT 鉴权过滤器
 * 白名单路径直接放行，其余校验 Token
 */
@Slf4j
@Component
public class AuthorizationFilter implements WebFilter {

    @Autowired
    private IgnoreUrlsConfig ignoreUrlsConfig;

    private final PathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        URI uri = request.getURI();
        String path = uri.getPath();

        // 白名单路径直接放行
        List<String> ignoreUrls = ignoreUrlsConfig.getUrls();
        if (ignoreUrls != null) {
            for (String ignoreUrl : ignoreUrls) {
                if (pathMatcher.match(ignoreUrl, path)) {
                    return chain.filter(exchange);
                }
            }
        }

        // 从 Header 取 Token
        String token = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (StrUtil.isNotEmpty(token) && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        // 简单校验 Token 是否存在（完整 JWT 解析由各业务服务自己完成）
        if (StrUtil.isEmpty(token)) {
            log.warn("请求缺少Token: {}", path);
            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return response.setComplete();
        }

        // 传递 Token 到下游服务
        ServerHttpRequest mutatedRequest = request.mutate()
                .header("Authorization", "Bearer " + token)
                .build();
        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }
}
