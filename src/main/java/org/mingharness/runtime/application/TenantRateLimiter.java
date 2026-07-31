package org.mingharness.runtime.application;

/** 租户 Run 创建频率限制的抽象，具体实现可以是内存或 Redis。 */
public interface TenantRateLimiter {

    /** 使用平台默认限制的兼容入口。 */
    void acquire(String tenantId);

    /** 使用当前租户生效的每分钟创建上限。 */
    default void acquire(String tenantId, int maxCreatesPerMinute) {
        acquire(tenantId);
    }
}
