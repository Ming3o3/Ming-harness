package org.mingharness.runtime.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/** local/test 模式使用的进程内租户互斥；local-infra 会切换到 Redis 实现。 */
@Component
@ConditionalOnProperty(prefix = "harness.redis", name = "enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryTenantRunQuotaGuard implements TenantRunQuotaGuard {

    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    @Override
    public <T> T withLock(String tenantId, Supplier<T> action) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("租户不能为空");
        }
        ReentrantLock lock = locks.computeIfAbsent(tenantId, ignored -> new ReentrantLock());
        lock.lock();
        boolean deferred = TenantQuotaLockLifecycle.releaseAfterTransaction(() -> release(tenantId, lock));
        try {
            return action.get();
        } finally {
            if (!deferred) {
                release(tenantId, lock);
            }
        }
    }

    private void release(String tenantId, ReentrantLock lock) {
        lock.unlock();
        if (!lock.isLocked() && !lock.hasQueuedThreads()) {
            locks.remove(tenantId, lock);
        }
    }
}
