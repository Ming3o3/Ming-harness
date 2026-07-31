package org.mingharness.messaging;

import tools.jackson.databind.ObjectMapper;
import org.mingharness.config.MessagingProperties;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.observability.HarnessMetrics;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** 定时把已提交的 Outbox 事件发布到 RabbitMQ。 */
@Component
@ConditionalOnProperty(prefix = "harness.messaging", name = "enabled", havingValue = "true")
public class OutboxRelay {

    private final OutboxEventRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final MessagingProperties properties;
    private final HarnessMetrics metrics;
    private final SensitiveDataSanitizer sanitizer;

    public OutboxRelay(OutboxEventRepository repository,
                       RabbitTemplate rabbitTemplate,
                       ObjectMapper objectMapper,
                       MessagingProperties properties,
                       HarnessMetrics metrics,
                       SensitiveDataSanitizer sanitizer) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.metrics = metrics;
        this.sanitizer = sanitizer;
    }

    @Scheduled(fixedDelayString = "${harness.messaging.outbox-poll-ms:1000}")
    @Transactional
    public void publishPending() {
        var events = repository
                .findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                        OutboxStatus.PENDING, Instant.now());
        metrics.pendingOutbox(events.size());
        for (OutboxEvent event : events) {
            try {
                RunExecutionMessage message = objectMapper.readValue(event.getPayload(), RunExecutionMessage.class);
                Boolean confirmed = rabbitTemplate.invoke(operations -> {
                    operations.convertAndSend(properties.exchange(), properties.routingKey(), message);
                    return operations.waitForConfirms(5_000);
                });
                if (!Boolean.TRUE.equals(confirmed)) {
                    throw new IllegalStateException("RabbitMQ 未确认执行消息");
                }
                event.markPublished();
                repository.save(event);
                metrics.outboxPublished();
            } catch (Exception exception) {
                event.markFailed(sanitizer.sanitize(exception.getMessage()), properties.maxAttempts());
                repository.save(event);
                metrics.outboxFailed();
            }
        }
    }
}
