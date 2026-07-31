package org.mingharness.runtime.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 本地同步模式使用的轻量执行锁。 */
@Component
@ConditionalOnProperty(prefix = "harness.redis", name = "enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryRunExecutionLock implements RunExecutionLock {

    private final Map<String, LockState> locks = new ConcurrentHashMap<>();

    @Override
    public Optional<LockToken> tryAcquire(String runId, Duration lease) {
        String key = "harness:run:lock:" + runId;
        String value = UUID.randomUUID().toString();
        LockState candidate = new LockState(value, Instant.now().plus(lease));
        LockState selected = locks.compute(key, (ignored, current) ->
                current == null || current.expiresAt().isBefore(Instant.now()) ? candidate : current);
        return selected == candidate ? Optional.of(new LockToken(key, value)) : Optional.empty();
    }

    @Override
    public boolean renew(LockToken token, Duration lease) {
        return locks.computeIfPresent(token.key(), (ignored, current) ->
                current.value().equals(token.value())
                        ? new LockState(token.value(), Instant.now().plus(lease)) : current) != null;
    }

    @Override
    public void release(LockToken token) {
        locks.computeIfPresent(token.key(), (ignored, current) ->
                current.value().equals(token.value()) ? null : current);
    }

    private record LockState(String value, Instant expiresAt) {
    }
}
