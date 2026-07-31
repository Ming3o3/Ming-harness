package org.mingharness.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Redis 治理能力的应用级配置。连接参数由 Spring Data Redis 统一管理。 */
@ConfigurationProperties(prefix = "harness.redis")
public record RedisProperties(
        boolean enabled,
        long lockTtlMs
) {
    public RedisProperties {
        lockTtlMs = lockTtlMs < 1 ? 30_000 : lockTtlMs;
    }
}
