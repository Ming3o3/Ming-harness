package org.mingharness.runtime.application;

/** Worker 在步骤边界发现取消请求后使用的内部控制流异常。 */
final class RunCancellationRequestedException extends RuntimeException {

    RunCancellationRequestedException(String runId) {
        super("Run 已请求取消: " + runId);
    }
}
