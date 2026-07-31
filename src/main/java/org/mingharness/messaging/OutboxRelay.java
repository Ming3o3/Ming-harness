package org.mingharness.messaging;

import tools.jackson.databind.ObjectMapper;
import org.mingharness.config.MessagingProperties;
import org.mingharness.observability.HarnessMetrics;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 定时把已提交的 Outbox 事件发布到 RabbitMQ。 */
@Component
@ConditionalOnProperty(prefix = "harness.messaging", name = "enabled", havingValue = "true")
public class OutboxRelay {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final MessagingProperties properties;
    private final OutboxClaimService claimService;
    private final RabbitQueueDepthMonitor queueDepthMonitor;
    private final HarnessMetrics metrics;

    public OutboxRelay(RabbitTemplate rabbitTemplate,
                       ObjectMapper objectMapper,
                       MessagingProperties properties,
                       OutboxClaimService claimService,
                       RabbitQueueDepthMonitor queueDepthMonitor,
                       HarnessMetrics metrics) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.claimService = claimService;
        this.queueDepthMonitor = queueDepthMonitor;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${harness.messaging.outbox-poll-ms:1000}")
    public void publishPending() {
        var capacity = queueDepthMonitor.availableCapacity();
        if (capacity.isEmpty()) {
            // RabbitMQ 状态未知时保留 Outbox，等待下一轮安全重试。
            metrics.pendingOutbox(0);
            return;
        }
        if (capacity.getAsInt() == 0) {
            metrics.rabbitBackpressure();
            metrics.pendingOutbox(0);
            return;
        }
        var claims = claimService.claimPending(capacity.getAsInt());
        metrics.pendingOutbox(claims.size());
        for (OutboxClaim claim : claims) {
            try {
                RunExecutionMessage message = objectMapper.readValue(claim.payload(), RunExecutionMessage.class);
                Boolean confirmed = rabbitTemplate.invoke(operations -> {
                    operations.convertAndSend(properties.exchange(), properties.routingKey(), message);
                    return operations.waitForConfirms(5_000);
                });
                if (!Boolean.TRUE.equals(confirmed)) {
                    throw new IllegalStateException("RabbitMQ 未确认执行消息");
                }
                if (claimService.markPublished(claim.eventId())) {
                    metrics.outboxPublished();
                }
            } catch (Exception exception) {
                if (claimService.markFailed(claim.eventId(), exception.getMessage())) {
                    metrics.outboxFailed();
                }
            }
        }
    }
}
