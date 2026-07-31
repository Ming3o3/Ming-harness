package org.mingharness.runtime.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/** local/test 模式使用的进程内取消信号。 */
@Component
@ConditionalOnProperty(prefix = "harness.redis", name = "enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryRunCancellationSignal implements RunCancellationSignal {

    private final ConcurrentHashMap<String, Instant> requests = new ConcurrentHashMap<>();

    @Override
    public void request(String runId, String tenantId, Duration ttl) {
        validate(runId, tenantId, ttl);
        requests.put(key(runId, tenantId), Instant.now().plus(ttl));
    }

    @Override
    public boolean isRequested(String runId, String tenantId) {
        if (runId == null || runId.isBlank() || tenantId == null || tenantId.isBlank()) {
            return false;
        }
        String key = key(runId, tenantId);
        Instant expiresAt = requests.get(key);
        if (expiresAt == null) {
            return false;
        }
        if (!expiresAt.isAfter(Instant.now())) {
            requests.remove(key, expiresAt);
            return false;
        }
        return true;
    }

    private String key(String runId, String tenantId) {
        return runId + "\n" + tenantId;
    }

    private void validate(String runId, String tenantId, Duration ttl) {
        if (runId == null || runId.isBlank() || tenantId == null || tenantId.isBlank()
                || ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("取消信号参数不合法");
        }
    }
}
