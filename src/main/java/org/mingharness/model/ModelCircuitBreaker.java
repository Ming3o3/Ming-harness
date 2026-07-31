package org.mingharness.model;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 单实例模型供应商熔断器：连续临时故障达到阈值后短暂打开，避免请求风暴。
 * 多实例部署仍应在网关或服务网格层配置共享熔断，本类只负责应用内快速失败。
 */
final class ModelCircuitBreaker {

    private final int failureThreshold;
    private final long openMs;
    private final Clock clock;
    private final AtomicInteger failures = new AtomicInteger();
    private final AtomicLong openUntilEpochMs = new AtomicLong();

    ModelCircuitBreaker(int failureThreshold, long openMs) {
        this(failureThreshold, openMs, Clock.systemUTC());
    }

    ModelCircuitBreaker(int failureThreshold, long openMs, Clock clock) {
        this.failureThreshold = failureThreshold;
        this.openMs = openMs;
        this.clock = clock;
    }

    boolean allowRequest() {
        long openUntil = openUntilEpochMs.get();
        if (openUntil == 0) {
            return true;
        }
        if (Instant.now(clock).toEpochMilli() >= openUntil) {
            openUntilEpochMs.compareAndSet(openUntil, 0);
            failures.set(0);
            return true;
        }
        return false;
    }

    void recordSuccess() {
        failures.set(0);
        openUntilEpochMs.set(0);
    }

    void recordFailure() {
        int current = failures.incrementAndGet();
        if (current >= failureThreshold) {
            openUntilEpochMs.set(Instant.now(clock).toEpochMilli() + openMs);
        }
    }

    int consecutiveFailures() {
        return failures.get();
    }
}
