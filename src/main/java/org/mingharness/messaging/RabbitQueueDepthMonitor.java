package org.mingharness.messaging;

import org.mingharness.config.MessagingProperties;
import org.mingharness.observability.HarnessMetrics;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.OptionalInt;

/**
 * 读取执行队列深度并计算可发布容量。
 *
 * <p>队列深度读取失败时返回空值，发布端必须暂停抢占 Outbox，避免在无法确认
 * RabbitMQ 状态时继续无界堆积消息。</p>
 */
@Component
@ConditionalOnProperty(prefix = "harness.messaging", name = "enabled", havingValue = "true")
public class RabbitQueueDepthMonitor {

    private final AmqpAdmin rabbitAdmin;
    private final MessagingProperties properties;
    private final HarnessMetrics metrics;

    public RabbitQueueDepthMonitor(AmqpAdmin rabbitAdmin,
                                   MessagingProperties properties,
                                   HarnessMetrics metrics) {
        this.rabbitAdmin = rabbitAdmin;
        this.properties = properties;
        this.metrics = metrics;
    }

    /** 定时刷新指标，即使当前没有 Outbox 事件也能观察队列状态。 */
    @Scheduled(fixedDelayString = "${harness.messaging.queue-metrics-poll-ms:5000}")
    public void refreshMetrics() {
        refresh();
    }

    /**
     * 读取当前队列深度并返回在上限内还能发布的消息数。
     *
     * @return 可用容量；RabbitMQ 不可用或队列不存在时返回空值
     */
    public OptionalInt availableCapacity() {
        return refresh();
    }

    private OptionalInt refresh() {
        try {
            QueueInformation queue = rabbitAdmin.getQueueInfo(properties.queue());
            if (queue == null) {
                throw new IllegalStateException("执行队列不存在: " + properties.queue());
            }
            long depth = Math.max(0L, queue.getMessageCount());
            long capacity = Math.max(0L, (long) properties.maxQueueDepth() - depth);
            metrics.rabbitQueueDepth(depth);
            metrics.rabbitQueueCapacity(capacity);
            return OptionalInt.of((int) Math.min(Integer.MAX_VALUE, capacity));
        } catch (RuntimeException exception) {
            // 监控读取故障不应让定时线程退出，也不允许 Relay 在未知状态下继续发布。
            metrics.rabbitQueueDepth(-1);
            metrics.rabbitQueueCapacity(-1);
            metrics.rabbitQueuePollFailed();
            return OptionalInt.empty();
        }
    }
}
