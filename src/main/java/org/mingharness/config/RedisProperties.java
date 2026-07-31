package org.mingharness.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Redis 治理能力的应用级配置。连接参数由 Spring Data Redis 统一管理。 */
@ConfigurationProperties(prefix = "harness.redis")
public record RedisProperties(
        boolean enabled,
        long lockTtlMs,
        long quotaLockWaitMs
) {
    public RedisProperties {
        // 执行锁和配额锁都必须覆盖一次数据库事务，过短租约会重新引入并发竞态。
        lockTtlMs = lockTtlMs < 1_000 ? 30_000 : lockTtlMs;
        quotaLockWaitMs = quotaLockWaitMs < 1 ? 1_000 : Math.min(quotaLockWaitMs, 30_000);
    }
}
