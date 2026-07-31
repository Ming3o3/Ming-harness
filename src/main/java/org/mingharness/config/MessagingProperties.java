package org.mingharness.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** RabbitMQ 拓扑和 Outbox Relay 配置。 */
@ConfigurationProperties(prefix = "harness.messaging")
public record MessagingProperties(
        boolean enabled,
        String exchange,
        String queue,
        String routingKey,
        String deadLetterExchange,
        String deadLetterQueue,
        int maxAttempts,
        long outboxPollMs,
        long outboxClaimLeaseMs,
        int consumerConcurrency,
        int maxConsumerConcurrency,
        int prefetch,
        int maxQueueDepth,
        long queueMetricsPollMs
) {
    public MessagingProperties {
        exchange = blankOrDefault(exchange, "harness.runtime");
        queue = blankOrDefault(queue, "harness.run.execute");
        routingKey = blankOrDefault(routingKey, "run.execute");
        deadLetterExchange = blankOrDefault(deadLetterExchange, "harness.runtime.dlx");
        deadLetterQueue = blankOrDefault(deadLetterQueue, "harness.run.execute.dlq");
        maxAttempts = maxAttempts < 1 ? 3 : maxAttempts;
        outboxPollMs = outboxPollMs < 100 ? 1_000 : outboxPollMs;
        outboxClaimLeaseMs = outboxClaimLeaseMs < 1_000 ? 30_000 : outboxClaimLeaseMs;
        consumerConcurrency = consumerConcurrency < 1 ? 1 : consumerConcurrency;
        maxConsumerConcurrency = maxConsumerConcurrency < consumerConcurrency
                ? consumerConcurrency : maxConsumerConcurrency;
        prefetch = prefetch < 1 ? 1 : prefetch;
        maxQueueDepth = maxQueueDepth < 1 ? 1_000 : maxQueueDepth;
        queueMetricsPollMs = queueMetricsPollMs < 1_000 ? 5_000 : queueMetricsPollMs;
    }

    private static String blankOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
