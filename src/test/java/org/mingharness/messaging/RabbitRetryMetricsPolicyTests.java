package org.mingharness.messaging;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mingharness.observability.HarnessMetrics;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.core.retry.RetryPolicy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证 Rabbit 重试和死信会被独立计数。 */
class RabbitRetryMetricsPolicyTests {

    @Test
    void shouldCountOnlyActualRetries() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RetryPolicy delegate = mock(RetryPolicy.class);
        when(delegate.shouldRetry(any(Throwable.class))).thenReturn(true);
        HarnessMetrics metrics = new HarnessMetrics(registry);
        RabbitRetryMetricsPolicy policy = new RabbitRetryMetricsPolicy(delegate, metrics);

        policy.shouldRetry(new IllegalStateException("临时错误"));
        assertEquals(1.0, registry.get("harness.rabbit.retries").counter().count());
    }

    @Test
    void shouldCountDeadLetterBeforeDelegatingReject() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        HarnessMetrics metrics = new HarnessMetrics(registry);
        MessageRecoverer delegate = mock(MessageRecoverer.class);
        RabbitDeadLetterRecoverer recoverer = new RabbitDeadLetterRecoverer(metrics, delegate);
        Message message = new Message(new byte[0]);
        IllegalStateException failure = new IllegalStateException("处理失败");

        recoverer.recover(message, failure);

        assertEquals(1.0, registry.get("harness.rabbit.dead_letters").counter().count());
        verify(delegate).recover(message, failure);
    }
}
