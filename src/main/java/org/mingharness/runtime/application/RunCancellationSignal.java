package org.mingharness.runtime.application;

import java.time.Duration;

/**
 * Worker 取消协作信号。
 *
 * <p>该信号只承担跨线程/跨实例的短期协调，Run.status 仍然是取消结果的最终事实来源。</p>
 */
public interface RunCancellationSignal {

    /** 请求 Worker 在下一个步骤边界停止执行。 */
    void request(String runId, String tenantId, Duration ttl);

    /** 查询是否存在尚未过期的取消请求。 */
    boolean isRequested(String runId, String tenantId);
}
