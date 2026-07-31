package org.mingharness.runtime.application;

import java.time.Duration;
import java.util.Optional;

/** Run 执行协调锁，避免队列重复投递产生重复副作用。 */
public interface RunExecutionLock {

    Optional<LockToken> tryAcquire(String runId, Duration lease);

    boolean renew(LockToken token, Duration lease);

    void release(LockToken token);

    record LockToken(String key, String value) {
    }
}
