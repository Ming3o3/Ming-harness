package org.mingharness.runtime.application;

import org.mingharness.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/** 单实例演示和测试使用的内存限流实现。 */
@Component
@ConditionalOnProperty(prefix = "harness.redis", name = "enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryTenantRateLimiter implements TenantRateLimiter {

    private final RuntimeLimits limits;
    private final ConcurrentHashMap<String, Deque<Instant>> windows = new ConcurrentHashMap<>();

    public InMemoryTenantRateLimiter(RuntimeLimits limits) {
        this.limits = limits;
    }

    @Override
    public void acquire(String tenantId) {
        acquire(tenantId, limits.maxCreatesPerMinute());
    }

    @Override
    public void acquire(String tenantId, int maxCreatesPerMinute) {
        if (maxCreatesPerMinute < 1) {
            throw new IllegalArgumentException("组织每分钟创建上限必须大于 0");
        }
        Instant now = Instant.now();
        Deque<Instant> window = windows.computeIfAbsent(tenantId, ignored -> new ArrayDeque<>());
        synchronized (window) {
            Instant threshold = now.minus(Duration.ofMinutes(1));
            while (!window.isEmpty() && window.peekFirst().isBefore(threshold)) {
                window.removeFirst();
            }
            if (window.size() >= maxCreatesPerMinute) {
                throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "TENANT_RATE_LIMITED",
                        "组织创建 Run 的频率超过限制");
            }
            window.addLast(now);
        }
    }
}
