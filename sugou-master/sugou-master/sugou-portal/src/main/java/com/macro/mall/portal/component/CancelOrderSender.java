package com.macro.mall.portal.component;

import com.macro.mall.portal.domain.QueueEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 取消订单延迟消息发送者
 * <p>
 * 通过 RabbitMQ 死信队列（DLX）实现延迟消息：
 * 消息发送到 TTL 队列，30分钟后过期，自动转发到实际消费队列
 * Created by macro on 2018/9/14.
 */
@Component
public class CancelOrderSender {

    private static final Logger LOGGER = LoggerFactory.getLogger(CancelOrderSender.class);

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * 发送订单超时取消的延迟消息
     *
     * @param orderId    订单ID
     * @param delayTimes 延迟时间（毫秒）
     */
    public void sendMessage(Long orderId, final long delayTimes) {
        rabbitTemplate.convertAndSend(
                QueueEnum.QUEUE_TTL_ORDER_CANCEL.getExchange(),
                QueueEnum.QUEUE_TTL_ORDER_CANCEL.getRouteKey(),
                orderId,
                message -> {
                    // 消息级别 TTL（优先级高于队列级别 TTL）
                    message.getMessageProperties().setExpiration(String.valueOf(delayTimes));
                    // 消息持久化，防止 RabbitMQ 重启丢失
                    message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    return message;
                });

        LOGGER.info("发送订单超时取消延迟消息，orderId: {}, delayMs: {}", orderId, delayTimes);
    }
}
