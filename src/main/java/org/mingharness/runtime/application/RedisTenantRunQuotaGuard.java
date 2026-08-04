package org.mingharness.runtime.application;

import org.mingharness.common.BusinessException;
import org.mingharness.config.RedisProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** 使用 Redis SET NX 为多实例活动 Run 配额检查提供组织级互斥。 */
@Component
@ConditionalOnProperty(prefix = "harness.redis", name = "enabled", havingValue = "true")
public class RedisTenantRunQuotaGuard implements TenantRunQuotaGuard {

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('DEL', KEYS[1]) else return 0 end", Long.class);

    private final StringRedisTemplate redisTemplate;
    private final RedisProperties properties;

    public RedisTenantRunQuotaGuard(StringRedisTemplate redisTemplate, RedisProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Override
    public <T> T withLock(String tenantId, Supplier<T> action) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("组织不能为空");
        }
        String key = "harness:tenant:" + tenantId + ":run-quota-lock";
        String token = UUID.randomUUID().toString();
        acquire(key, token);
        boolean deferred = TenantQuotaLockLifecycle.releaseAfterTransaction(() -> release(key, token));
        try {
            return action.get();
        } finally {
            if (!deferred) {
                release(key, token);
            }
        }
    }

    private void acquire(String key, String token) {
        long deadline = System.nanoTime() + Duration.ofMillis(properties.quotaLockWaitMs()).toNanos();
        try {
            while (true) {
                Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                        key, token, Duration.ofMillis(properties.lockTtlMs()));
                if (Boolean.TRUE.equals(acquired)) {
                    return;
                }
                if (System.nanoTime() >= deadline) {
                    throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "TENANT_QUOTA_BUSY",
                            "组织运行配额正在由其他请求更新，请稍后重试");
                }
                Thread.sleep(10);
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                    "TENANT_QUOTA_COORDINATION_INTERRUPTED", "组织运行配额协调被中断");
        } catch (Exception exception) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                    "TENANT_QUOTA_COORDINATION_UNAVAILABLE", "Redis 组织配额协调不可用，请稍后重试");
        }
    }

    private void release(String key, String token) {
        try {
            redisTemplate.execute(RELEASE_SCRIPT, List.of(key), token);
        } catch (Exception ignored) {
            // 释放失败时依赖短期 TTL 自动回收，不能影响原数据库事务结果。
        }
    }
}
