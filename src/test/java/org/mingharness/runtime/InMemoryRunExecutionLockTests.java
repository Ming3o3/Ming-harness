package org.mingharness.runtime;

import org.junit.jupiter.api.Test;
import org.mingharness.runtime.application.InMemoryRunExecutionLock;
import org.mingharness.runtime.application.RunExecutionLock;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryRunExecutionLockTests {

    @Test
    void shouldAllowOnlyOneOwnerAndRequireOwnerTokenToRelease() {
        InMemoryRunExecutionLock lock = new InMemoryRunExecutionLock();

        var first = lock.tryAcquire("run-1", Duration.ofSeconds(10));
        var second = lock.tryAcquire("run-1", Duration.ofSeconds(10));

        assertTrue(first.isPresent());
        assertFalse(second.isPresent());

        RunExecutionLock.LockToken wrongToken = new RunExecutionLock.LockToken(
                first.orElseThrow().key(), "wrong-token");
        lock.release(wrongToken);
        assertFalse(lock.tryAcquire("run-1", Duration.ofSeconds(10)).isPresent());

        lock.release(first.orElseThrow());
        assertTrue(lock.tryAcquire("run-1", Duration.ofSeconds(10)).isPresent());
    }

    @Test
    void shouldRenewOnlyWhenTokenStillOwnsLock() {
        InMemoryRunExecutionLock lock = new InMemoryRunExecutionLock();
        var token = lock.tryAcquire("run-2", Duration.ofSeconds(1)).orElseThrow();

        assertTrue(lock.renew(token, Duration.ofSeconds(10)));
        assertFalse(lock.renew(new RunExecutionLock.LockToken(token.key(), "wrong"), Duration.ofSeconds(10)));
    }
}
