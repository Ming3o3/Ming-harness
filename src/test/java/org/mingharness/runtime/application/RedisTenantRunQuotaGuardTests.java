package org.mingharness.runtime.application;

import org.junit.jupiter.api.Test;
import org.mingharness.common.BusinessException;
import org.mingharness.config.RedisProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证 Redis 租户配额锁的 key 约定和故障快速失败行为。 */
class RedisTenantRunQuotaGuardTests {

    @Test
    void shouldAcquireAndReleaseTenantScopedLock() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        RedisTenantRunQuotaGuard guard = new RedisTenantRunQuotaGuard(
                redisTemplate, new RedisProperties(true, 30_000, 0));

        assertEquals("ok", guard.withLock("tenant-1", () -> "ok"));
        verify(valueOperations).setIfAbsent(eq("harness:tenant:tenant-1:run-quota-lock"),
                anyString(), any(Duration.class));
        verify(redisTemplate).execute(any(), eq(java.util.List.of("harness:tenant:tenant-1:run-quota-lock")),
                anyString());
    }

    @Test
    void shouldFailClosedWhenRedisIsUnavailable() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new IllegalStateException("Redis 连接失败"));
        RedisTenantRunQuotaGuard guard = new RedisTenantRunQuotaGuard(
                redisTemplate, new RedisProperties(true, 30_000, 0));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> guard.withLock("tenant-1", () -> "unreachable"));
        assertEquals("TENANT_QUOTA_COORDINATION_UNAVAILABLE", exception.getCode());
    }
}
