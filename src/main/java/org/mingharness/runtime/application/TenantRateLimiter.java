package org.mingharness.runtime.application;

/** 租户 Run 创建频率限制的抽象，具体实现可以是内存或 Redis。 */
public interface TenantRateLimiter {

    void acquire(String tenantId);
}
