package com.macro.mall.portal.domain;

import lombok.Getter;

/**
 * 消息队列枚举类
 * Created by macro on 2018/9/14.
 */
@Getter
public enum QueueEnum {
    /**
     * 订单超时取消实际消费队列
     */
    QUEUE_ORDER_CANCEL("mall.order.direct", "mall.order.cancel", "mall.order.cancel"),
    /**
     * 订单超时取消TTL延迟队列（死信队列）
     */
    QUEUE_TTL_ORDER_CANCEL("mall.order.direct.ttl", "mall.order.cancel.ttl", "mall.order.cancel.ttl"),
    /**
     * 订单超时取消最终死信队列（消费失败N次后的兜底）
     */
    QUEUE_ORDER_CANCEL_DLX("mall.order.direct.dlx", "mall.order.cancel.dlx", "mall.order.cancel.dlx");

    /**
     * 交换名称
     */
    private final String exchange;
    /**
     * 队列名称
     */
    private final String name;
    /**
     * 路由键
     */
    private final String routeKey;

    QueueEnum(String exchange, String name, String routeKey) {
        this.exchange = exchange;
        this.name = name;
        this.routeKey = routeKey;
    }
}
