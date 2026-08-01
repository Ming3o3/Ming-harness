package org.mingharness.runtime.application;

import jakarta.annotation.PreDestroy;
import org.mingharness.messaging.RunExecutionMessage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * H2 本地桌面模式的进程内异步 Worker。
 *
 * <p>它复用正式 Rabbit Worker 的状态机，而不是在 HTTP 请求线程中直接调用模型。提交事务完成后
 * 才开始处理，页面可以先收到用户消息与 PENDING 助手气泡，再通过既有 SSE 接收执行快照。</p>
 */
@Component
public class LocalRunDispatcher {

    private final ObjectProvider<RunService> runServiceProvider;
    private final ExecutorService executor;

    public LocalRunDispatcher(ObjectProvider<RunService> runServiceProvider,
                              @Value("${harness.local-execution.concurrency:2}") int concurrency) {
        this.runServiceProvider = runServiceProvider;
        int workerCount = Math.max(1, Math.min(8, concurrency));
        AtomicInteger workerIndex = new AtomicInteger();
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable,
                    "ming-harness-local-worker-" + workerIndex.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        this.executor = Executors.newFixedThreadPool(workerCount, threadFactory);
    }

    public void dispatch(RunExecutionMessage message) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatchAfterCommit(message);
                }
            });
            return;
        }
        dispatchAfterCommit(message);
    }

    private void dispatchAfterCommit(RunExecutionMessage message) {
        executor.execute(() -> runServiceProvider.getObject().executeFromWorker(message));
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
