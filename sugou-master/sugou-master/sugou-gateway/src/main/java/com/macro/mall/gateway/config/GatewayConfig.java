package com.macro.mall.gateway.config;

import com.macro.mall.gateway.component.AuthorizationFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.cors.reactive.CorsUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 网关全局配置：路由 + CORS + 鉴权过滤器
 */
@Slf4j
@Configuration
public class GatewayConfig {

    /**
     * 路由配置（Java DSL 方式，避免 Spring Boot 3.5.x 配置属性重命名问题）
     */
    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
            .route("mall-portal", r -> r.path("/api/**")
                .filters(f -> f.stripPrefix(1))
                .uri("http://localhost:8085"))
            .route("mall-admin", r -> r.path("/admin/**")
                .filters(f -> f.stripPrefix(1))
                .uri("http://localhost:8080"))
            .route("ai-agent", r -> r.path("/ai/**")
                .filters(f -> f.stripPrefix(1))
                .uri("http://localhost:8000"))
            .build();
    }

    @Bean
    public AuthorizationFilter authorizationFilter() {
        return new AuthorizationFilter();
    }

    /**
     * CORS WebFlux 过滤器（兜底跨域）
     */
    @Bean
    public WebFilter corsFilter() {
        return (ServerWebExchange exchange, WebFilterChain chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            if (CorsUtils.isCorsRequest(request)) {
                ServerHttpResponse response = exchange.getResponse();
                HttpHeaders headers = response.getHeaders();
                headers.add("Access-Control-Allow-Origin", "*");
                headers.add("Access-Control-Allow-Methods", "*");
                headers.add("Access-Control-Allow-Headers", "*");
                headers.add("Access-Control-Max-Age", "3600");
                if (request.getMethod() == HttpMethod.OPTIONS) {
                    response.setStatusCode(HttpStatus.OK);
                    return Mono.empty();
                }
            }
            return chain.filter(exchange);
        };
    }
}