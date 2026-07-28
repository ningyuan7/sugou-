package com.macro.mall.portal.component;

import com.macro.mall.common.service.RedisService;
import com.macro.mall.portal.service.OmsPortalOrderService;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 取消订单消息的消费者
 * <p>
 * 防重复消费的四层保障：
 * 1. Redis setIfAbsent 幂等记录（同一订单30分钟内只处理一次）
 * 2. cancelOrder 内部校验订单状态（只取消 status=0 待支付的订单）
 * 3. 手动ACK确保消息不丢失
 * 4. 消费失败重试耗尽后进入最终死信队列（人工兜底）
 * <p>
 * Created by macro on 2018/9/14.
 */
@Component
public class CancelOrderReceiver {

    private static final Logger LOGGER = LoggerFactory.getLogger(CancelOrderReceiver.class);

    /**
     * Redis幂等记录Key前缀
     */
    private static final String ORDER_CANCEL_RECORD = "record:order:cancel:";

    /**
     * 幂等记录过期时间（秒），防止Redis内存无限增长
     */
    private static final long IDEMPOTENT_EXPIRE = 1800; // 30分钟

    @Autowired
    private OmsPortalOrderService portalOrderService;

    @Autowired
    private RedisService redisService;

    /**
     * 监听实际消费队列，处理订单超时取消
     *
     * @param orderId 订单ID
     * @param message 原始消息
     * @param channel 信道（用于手动ACK/NACK）
     */
    @RabbitListener(queues = "mall.order.cancel", containerFactory = "rabbitListenerContainerFactory")
    public void handle(Long orderId, Message message, Channel channel) {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        try {
            LOGGER.info("收到订单超时取消消息，orderId: {}", orderId);

            // ============ 第一层：Redis 幂等性校验 ============
            String recordKey = ORDER_CANCEL_RECORD + orderId;
            Boolean isFirstTime = redisService.setIfAbsent(recordKey, "1", IDEMPOTENT_EXPIRE);
            if (Boolean.FALSE.equals(isFirstTime)) {
                LOGGER.warn("订单{}取消消息已处理过（Redis幂等），直接ACK", orderId);
                channel.basicAck(deliveryTag, false);
                return;
            }

            // ============ 第二层：执行业务（cancelOrder内部有状态校验） ============
            portalOrderService.cancelOrder(orderId);

            // ============ 成功 → 手动ACK ============
            channel.basicAck(deliveryTag, false);
            LOGGER.info("订单{}超时取消处理成功", orderId);

        } catch (Exception e) {
            LOGGER.error("取消订单{}处理失败", orderId, e);
            try {
                // requeue=false：不重新入队，消息进入该队列配置的死信交换机（最终死信队列）
                // 避免无限重试导致消息积压
                channel.basicNack(deliveryTag, false, false);
            } catch (IOException ex) {
                LOGGER.error("消息NACK失败，orderId: {}", orderId, ex);
            }
        }
    }
}
