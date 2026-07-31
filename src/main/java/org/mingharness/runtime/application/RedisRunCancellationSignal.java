package org.mingharness.runtime.application;

import org.mingharness.common.BusinessException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** 使用 Redis 在多个 Worker 实例之间传播短期取消信号。 */
@Component
@ConditionalOnProperty(prefix = "harness.redis", name = "enabled", havingValue = "true")
public class RedisRunCancellationSignal implements RunCancellationSignal {

    private final StringRedisTemplate redisTemplate;

    public RedisRunCancellationSignal(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void request(String runId, String tenantId, Duration ttl) {
        validate(runId, tenantId, ttl);
        try {
            redisTemplate.opsForValue().set(key(runId, tenantId), "1", ttl);
        } catch (Exception exception) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                    "CANCELLATION_SIGNAL_UNAVAILABLE", "Redis 取消协调不可用，请稍后重试");
        }
    }

    @Override
    public boolean isRequested(String runId, String tenantId) {
        if (runId == null || runId.isBlank() || tenantId == null || tenantId.isBlank()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key(runId, tenantId)));
        } catch (Exception exception) {
            // Worker 无法确认取消信号时必须交给消息重试，不能继续执行潜在副作用。
            throw new TransientInfrastructureException("Redis 取消协调不可用", exception);
        }
    }

    private String key(String runId, String tenantId) {
        return "harness:run:" + runId + ":tenant:" + tenantId + ":cancel-requested";
    }

    private void validate(String runId, String tenantId, Duration ttl) {
        if (runId == null || runId.isBlank() || tenantId == null || tenantId.isBlank()
                || ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("取消信号参数不合法");
        }
    }
}
