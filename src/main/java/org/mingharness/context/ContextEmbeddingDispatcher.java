package org.mingharness.context;

import jakarta.annotation.PreDestroy;
import org.mingharness.config.ContextIndexProperties;
import org.mingharness.observability.HarnessMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 在正文事务提交后调度上下文 embedding。
 *
 * <p>向量供应商属于外部依赖，不能让网络等待、重试或供应商故障占住正文写入事务。
 * 队列采用有界策略；提交压力过高时不丢失正文，只留下未向量化 chunk 供重建任务继续处理。</p>
 */
@Service
public class ContextEmbeddingDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ContextEmbeddingDispatcher.class);

    private final ContextEmbeddingIndexer indexer;
    private final HarnessMetrics metrics;
    private final ContextIndexProperties properties;
    private final ThreadPoolExecutor executor;

    public ContextEmbeddingDispatcher(ContextEmbeddingIndexer indexer,
                                      HarnessMetrics metrics,
                                      ContextIndexProperties properties) {
        this.indexer = indexer;
        this.metrics = metrics;
        this.properties = properties;
        AtomicInteger threadNumber = new AtomicInteger();
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable,
                    "ming-harness-context-indexer-" + threadNumber.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        this.executor = new ThreadPoolExecutor(
                properties.concurrency(), properties.concurrency(), 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(properties.queueCapacity()), threadFactory,
                new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * 在当前事务提交后调度一个父对象的全部有效子块。
     *
     * <p>没有事务时直接按配置执行，方便重建服务和独立调用复用。</p>
     */
    public void dispatchAfterCommit(String parentType, String parentId) {
        if (!indexer.ready()) return;
        Runnable task = () -> index(parentType, parentId);
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch(task);
                }
            });
            return;
        }
        dispatch(task);
    }

    private void dispatch(Runnable task) {
        if (!properties.asyncEnabled()) {
            task.run();
            return;
        }
        try {
            executor.execute(task);
        } catch (RejectedExecutionException exception) {
            // 数据库中的 embedded_at 仍为空，重建接口可以继续消费，不阻断正文请求。
            metrics.contextIndexFailure();
            log.warn("上下文向量索引队列已满，等待后续重建，queueSize={}, queueCapacity={}",
                    executor.getQueue().size(), properties.queueCapacity());
        }
    }

    private void index(String parentType, String parentId) {
        try {
            indexer.indexParent(parentType, parentId);
        } catch (EmbeddingGatewayException | ContextEmbeddingStoreException exception) {
            metrics.contextIndexFailure();
            log.warn("上下文向量索引暂时失败，parentType={}, parentId={}, message={}",
                    parentType, parentId, exception.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
