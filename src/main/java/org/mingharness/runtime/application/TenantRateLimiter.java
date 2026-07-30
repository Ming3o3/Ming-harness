package org.mingharness.runtime.application;

import org.mingharness.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/** 单实例令牌窗口限流；生产多实例部署时应替换为 Redis 等共享实现。 */
@Component
public class TenantRateLimiter {

    private final RuntimeLimits limits;
    private final ConcurrentHashMap<String, Deque<Instant>> windows = new ConcurrentHashMap<>();

    public TenantRateLimiter(RuntimeLimits limits) {
        this.limits = limits;
    }

    public void acquire(String tenantId) {
        Instant now = Instant.now();
        Deque<Instant> window = windows.computeIfAbsent(tenantId, ignored -> new ArrayDeque<>());
        synchronized (window) {
            Instant threshold = now.minus(Duration.ofMinutes(1));
            while (!window.isEmpty() && window.peekFirst().isBefore(threshold)) {
                window.removeFirst();
            }
            if (window.size() >= limits.maxCreatesPerMinute()) {
                throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "TENANT_RATE_LIMITED",
                        "租户创建 Run 的频率超过限制");
            }
            window.addLast(now);
        }
    }
}
