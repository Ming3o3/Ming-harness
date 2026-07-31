package org.mingharness.runtime.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证本地配额锁的并发互斥和事务完成后释放语义。 */
class InMemoryTenantRunQuotaGuardTests {

    private final InMemoryTenantRunQuotaGuard guard = new InMemoryTenantRunQuotaGuard();

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void shouldSerializeSameTenantQuotaChecks() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch firstEntered = new CountDownLatch(1);
            CountDownLatch releaseFirst = new CountDownLatch(1);
            CountDownLatch secondEntered = new CountDownLatch(1);
            Future<?> first = executor.submit(() -> guard.withLock("tenant-1", () -> {
                firstEntered.countDown();
                await(releaseFirst);
                return null;
            }));
            assertTrue(firstEntered.await(1, TimeUnit.SECONDS));
            Future<?> second = executor.submit(() -> guard.withLock("tenant-1", () -> {
                secondEntered.countDown();
                return null;
            }));
            assertFalse(secondEntered.await(100, TimeUnit.MILLISECONDS));
            releaseFirst.countDown();
            first.get(1, TimeUnit.SECONDS);
            second.get(1, TimeUnit.SECONDS);
            assertTrue(secondEntered.getCount() == 0);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldReleaseOnlyAfterTransactionCompletion() throws Exception {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        guard.withLock("tenant-2", () -> "created");

        var executor = Executors.newSingleThreadExecutor();
        try {
            CountDownLatch secondEntered = new CountDownLatch(1);
            Future<?> second = executor.submit(() -> guard.withLock("tenant-2", () -> {
                secondEntered.countDown();
                return null;
            }));
            assertFalse(secondEntered.await(100, TimeUnit.MILLISECONDS));

            List<TransactionSynchronization> synchronizations =
                    TransactionSynchronizationManager.getSynchronizations();
            synchronizations.forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
            assertTrue(secondEntered.await(1, TimeUnit.SECONDS));
            second.get(1, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("测试等待被中断", exception);
        }
    }
}
