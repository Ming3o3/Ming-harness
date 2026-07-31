package org.mingharness.messaging;

import org.mingharness.observability.HarnessMetrics;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.util.backoff.BackOff;

import java.time.Duration;

/** 在不改变 Spring Rabbit 重试判定的前提下记录实际发生的重试次数。 */
final class RabbitRetryMetricsPolicy implements RetryPolicy {

    private final RetryPolicy delegate;
    private final HarnessMetrics metrics;

    RabbitRetryMetricsPolicy(RetryPolicy delegate, HarnessMetrics metrics) {
        this.delegate = delegate;
        this.metrics = metrics;
    }

    @Override
    public boolean shouldRetry(Throwable throwable) {
        boolean retry = delegate.shouldRetry(throwable);
        if (retry) {
            metrics.rabbitRetry();
        }
        return retry;
    }

    @Override
    public Duration getTimeout() {
        return delegate.getTimeout();
    }

    @Override
    public BackOff getBackOff() {
        return delegate.getBackOff();
    }
}
