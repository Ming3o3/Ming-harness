package org.mingharness.runtime.application;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 将租户锁释放延后到数据库事务完成，避免提交前释放造成计数竞态。 */
final class TenantQuotaLockLifecycle {

    private TenantQuotaLockLifecycle() {
    }

    static boolean releaseAfterTransaction(Runnable release) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()
                || !TransactionSynchronizationManager.isActualTransactionActive()) {
            return false;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                release.run();
            }
        });
        return true;
    }
}
