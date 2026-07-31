package org.mingharness.runtime.application;

import java.util.function.Supplier;

/** 为活动 Run 配额检查提供租户级短期互斥，避免多实例并发创建越过数据库计数上限。 */
public interface TenantRunQuotaGuard {

    <T> T withLock(String tenantId, Supplier<T> action);
}
