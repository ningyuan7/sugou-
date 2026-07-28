package com.macro.mall.portal.config;

import com.macro.mall.portal.domain.QueueEnum;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 死信队列（DLX）配置
 * <p>
 * 消息流转路径：
 * <pre>
 *   生产者 → TTL交换机 → TTL延迟队列(30分钟) → 死信交换机 → 实际消费队列 → 消费者
 *                                                                   ↓ (消费失败重试N次后)
 *                                                             最终死信队列(DLX) → 人工兜底
 * </pre>
 * Created by macro on 2018/9/14.
 */
@Configuration
public class RabbitMqConfig {

    // ==================== 交换机定义 ====================

    /**
     * 订单实际消费队列所绑定的交换机（死信交换机）
     */
    @Bean
    DirectExchange orderDirect() {
        return ExchangeBuilder
                .directExchange(QueueEnum.QUEUE_ORDER_CANCEL.getExchange())
                .durable(true)
                .build();
    }

    /**
     * 订单TTL延迟队列所绑定的交换机
     */
    @Bean
    DirectExchange orderTtlDirect() {
        return ExchangeBuilder
                .directExchange(QueueEnum.QUEUE_TTL_ORDER_CANCEL.getExchange())
                .durable(true)
                .build();
    }

    /**
     * 最终死信交换机（消费失败N次后的兜底）
     */
    @Bean
    DirectExchange orderDlxDirect() {
        return ExchangeBuilder
                .directExchange(QueueEnum.QUEUE_ORDER_CANCEL_DLX.getExchange())
                .durable(true)
                .build();
    }

    // ==================== 队列定义 ====================

    /**
     * 订单实际消费队列
     * 绑定死信交换机，消费失败重试耗尽后转入最终死信队列
     */
    @Bean
    public Queue orderQueue() {
        return QueueBuilder
                .durable(QueueEnum.QUEUE_ORDER_CANCEL.getName())
                // 消费失败后转入最终死信交换机（人工兜底）
                .withArgument("x-dead-letter-exchange", QueueEnum.QUEUE_ORDER_CANCEL_DLX.getExchange())
                .withArgument("x-dead-letter-routing-key", QueueEnum.QUEUE_ORDER_CANCEL_DLX.getRouteKey())
                .build();
    }

    /**
     * 订单TTL延迟队列（死信队列的核心）
     * 消息在此队列过期后，自动转发到实际消费队列
     */
    @Bean
    public Queue orderTtlQueue() {
        return QueueBuilder
                .durable(QueueEnum.QUEUE_TTL_ORDER_CANCEL.getName())
                // 队列级别默认TTL：30分钟（消息级别TTL优先）
                .withArgument("x-message-ttl", 30 * 60 * 1000)
                // 消息过期后转发到的死信交换机
                .withArgument("x-dead-letter-exchange", QueueEnum.QUEUE_ORDER_CANCEL.getExchange())
                // 消息过期后使用的路由key
                .withArgument("x-dead-letter-routing-key", QueueEnum.QUEUE_ORDER_CANCEL.getRouteKey())
                .build();
    }

    /**
     * 最终死信队列（人工兜底）
     * 当实际消费队列中的消息被拒绝/重试耗尽后，消息进入此队列
     * 需要人工介入或日志告警
     */
    @Bean
    public Queue orderDlxQueue() {
        return QueueBuilder
                .durable(QueueEnum.QUEUE_ORDER_CANCEL_DLX.getName())
                .build();
    }

    // ==================== 绑定关系 ====================

    /**
     * 将实际消费队列绑定到死信交换机
     */
    @Bean
    Binding orderBinding(DirectExchange orderDirect, Queue orderQueue) {
        return BindingBuilder
                .bind(orderQueue)
                .to(orderDirect)
                .with(QueueEnum.QUEUE_ORDER_CANCEL.getRouteKey());
    }

    /**
     * 将TTL延迟队列绑定到TTL交换机
     */
    @Bean
    Binding orderTtlBinding(DirectExchange orderTtlDirect, Queue orderTtlQueue) {
        return BindingBuilder
                .bind(orderTtlQueue)
                .to(orderTtlDirect)
                .with(QueueEnum.QUEUE_TTL_ORDER_CANCEL.getRouteKey());
    }

    /**
     * 将最终死信队列绑定到最终死信交换机
     */
    @Bean
    Binding orderDlxBinding(DirectExchange orderDlxDirect, Queue orderDlxQueue) {
        return BindingBuilder
                .bind(orderDlxQueue)
                .to(orderDlxDirect)
                .with(QueueEnum.QUEUE_ORDER_CANCEL_DLX.getRouteKey());
    }

    // ==================== 监听器配置（手动ACK） ====================

    /**
     * 配置手动ACK模式，确保消费失败不丢消息
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        // 手动确认模式
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        // 每次拉取1条消息，公平调度
        factory.setPrefetchCount(1);
        // Spring AMQP 3.x 方法名变更：setConcurrency → setConcurrentConsumers
        // setMaxConcurrency → setMaxConcurrentConsumers，参数由 String 改为 Integer
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(3);
        return factory;
    }
}
