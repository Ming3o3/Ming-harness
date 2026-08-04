package org.mingharness.runtime.application;

import org.mingharness.common.BusinessException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** 使用 Redis Lua 脚本实现跨实例原子限流。 */
@Component
@ConditionalOnProperty(prefix = "harness.redis", name = "enabled", havingValue = "true")
public class RedisTenantRateLimiter implements TenantRateLimiter {

    private static final DefaultRedisScript<Long> INCREMENT_SCRIPT = new DefaultRedisScript<>(
            "local count = redis.call('INCR', KEYS[1]) "
                    + "if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end "
                    + "return count", Long.class);

    private final StringRedisTemplate redisTemplate;
    private final RuntimeLimits limits;

    public RedisTenantRateLimiter(StringRedisTemplate redisTemplate, RuntimeLimits limits) {
        this.redisTemplate = redisTemplate;
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
        String bucket = String.valueOf(Instant.now().getEpochSecond() / 60);
        String key = "harness:tenant:" + tenantId + ":run-create:" + bucket;
        try {
            Long count = redisTemplate.execute(INCREMENT_SCRIPT, List.of(key),
                    String.valueOf(Duration.ofMinutes(2).toMillis()));
            if (count != null && count > maxCreatesPerMinute) {
                throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "TENANT_RATE_LIMITED",
                        "组织创建 Run 的频率超过限制");
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "RATE_LIMIT_STORE_UNAVAILABLE",
                    "Redis 限流存储不可用，请稍后重试");
        }
    }
}
