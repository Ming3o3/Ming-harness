package org.mingharness.runtime.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 使用 Redis SET NX 和 token 校验实现分布式 Run 执行锁。 */
@Component
@ConditionalOnProperty(prefix = "harness.redis", name = "enabled", havingValue = "true")
public class RedisRunExecutionLock implements RunExecutionLock {

    private static final DefaultRedisScript<Long> RENEW_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('PEXPIRE', KEYS[1], ARGV[2]) else return 0 end", Long.class);
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('DEL', KEYS[1]) else return 0 end", Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisRunExecutionLock(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Optional<LockToken> tryAcquire(String runId, Duration lease) {
        String key = "harness:run:lock:" + runId;
        String value = UUID.randomUUID().toString();
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, value, lease);
            return Boolean.TRUE.equals(acquired) ? Optional.of(new LockToken(key, value)) : Optional.empty();
        } catch (Exception exception) {
            throw new IllegalStateException("Redis 执行锁不可用", exception);
        }
    }

    @Override
    public boolean renew(LockToken token, Duration lease) {
        Long result = redisTemplate.execute(RENEW_SCRIPT, List.of(token.key()), token.value(),
                String.valueOf(lease.toMillis()));
        return result != null && result > 0;
    }

    @Override
    public void release(LockToken token) {
        redisTemplate.execute(RELEASE_SCRIPT, List.of(token.key()), token.value());
    }
}
