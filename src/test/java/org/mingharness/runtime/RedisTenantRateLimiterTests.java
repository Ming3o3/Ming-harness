package org.mingharness.runtime;

import org.junit.jupiter.api.Test;
import org.mingharness.common.BusinessException;
import org.mingharness.runtime.application.RedisTenantRateLimiter;
import org.mingharness.runtime.application.RuntimeLimits;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisTenantRateLimiterTests {

    @Test
    void shouldRejectWhenAtomicRedisCounterExceedsTenantLimit() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(), anyList(), anyString())).thenReturn(61L);
        RedisTenantRateLimiter limiter = new RedisTenantRateLimiter(redisTemplate, limits(60));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> limiter.acquire("tenant-1"));

        assertEquals("TENANT_RATE_LIMITED", exception.getCode());
    }

    @Test
    void shouldFailClosedWhenRedisIsUnavailable() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(), anyList(), anyString()))
                .thenThrow(new IllegalStateException("连接失败"));
        RedisTenantRateLimiter limiter = new RedisTenantRateLimiter(redisTemplate, limits(60));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> limiter.acquire("tenant-1"));

        assertEquals("RATE_LIMIT_STORE_UNAVAILABLE", exception.getCode());
    }

    private RuntimeLimits limits(int maxCreatesPerMinute) {
        return new RuntimeLimits(20, 20, 10_000, BigDecimal.ONE, 30_000,
                4_000, maxCreatesPerMinute, 120_000, 3);
    }
}
