package org.mingharness.context;

import jakarta.annotation.PreDestroy;
import org.mingharness.config.ContextChunkingProperties;
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

/** 将语义重分块安排在正文事务提交后，避免外部 embedding 调用占用写入事务。 */
@Service
public class ContextSemanticRechunkDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ContextSemanticRechunkDispatcher.class);

    private final ContextSemanticRechunker rechunker;
    private final ContextEmbeddingDispatcher embeddingDispatcher;
    private final ContextChunkingProperties chunkingProperties;
    private final ContextIndexProperties indexProperties;
    private final EmbeddingGateway embeddingGateway;
    private final ContextEmbeddingStore embeddingStore;
    private final HarnessMetrics metrics;
    private final ThreadPoolExecutor executor;

    public ContextSemanticRechunkDispatcher(ContextSemanticRechunker rechunker,
                                            ContextEmbeddingDispatcher embeddingDispatcher,
                                            ContextChunkingProperties chunkingProperties,
                                            ContextIndexProperties indexProperties,
                                            EmbeddingGateway embeddingGateway,
                                            ContextEmbeddingStore embeddingStore,
                                            HarnessMetrics metrics) {
        this.rechunker = rechunker;
        this.embeddingDispatcher = embeddingDispatcher;
        this.chunkingProperties = chunkingProperties;
        this.indexProperties = indexProperties;
        this.embeddingGateway = embeddingGateway;
        this.embeddingStore = embeddingStore;
        this.metrics = metrics;
        AtomicInteger threadNumber = new AtomicInteger();
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable,
                    "ming-harness-context-semantic-rechunker-" + threadNumber.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        this.executor = new ThreadPoolExecutor(
                indexProperties.concurrency(), indexProperties.concurrency(), 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(indexProperties.queueCapacity()), threadFactory,
                new ThreadPoolExecutor.AbortPolicy());
    }

    public boolean enabled() {
        return chunkingProperties.semanticEnabled()
                && embeddingGateway.enabled()
                && embeddingStore.supported();
    }

    public boolean enabled(String tenantId) {
        return chunkingProperties.semanticEnabled()
                && (tenantId == null ? embeddingGateway.enabled() : embeddingGateway.enabled(tenantId))
                && embeddingStore.supported();
    }

    /** 返回是否已接管该父对象的索引；未启用时由调用方直接走普通向量索引。 */
    public boolean dispatchAfterCommit(String parentType, String parentId) {
        return dispatchAfterCommit(null, parentType, parentId);
    }

    public boolean dispatchAfterCommit(String tenantId, String parentType, String parentId) {
        if (!enabled(tenantId)) return false;
        Runnable task = () -> run(tenantId, parentType, parentId);
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch(task, tenantId, parentType, parentId);
                }
            });
            return true;
        }
        dispatch(task, tenantId, parentType, parentId);
        return true;
    }

    private void dispatch(Runnable task, String tenantId, String parentType, String parentId) {
        if (!indexProperties.asyncEnabled()) {
            task.run();
            return;
        }
        try {
            executor.execute(task);
        } catch (RejectedExecutionException exception) {
            metrics.contextIndexFailure();
            log.warn("语义重分块队列已满，保留确定性子块并转入普通向量索引，parentType={}, parentId={}",
                    parentType, parentId);
            if (tenantId == null) {
                embeddingDispatcher.dispatchAfterCommit(parentType, parentId);
            } else {
                embeddingDispatcher.dispatchAfterCommit(tenantId, parentType, parentId);
            }
        }
    }

    private void run(String tenantId, String parentType, String parentId) {
        try {
            rechunker.rechunk(parentType, parentId);
        } catch (RuntimeException exception) {
            metrics.contextIndexFailure();
            log.warn("语义重分块失败，保留确定性子块并转入普通向量索引，parentType={}, parentId={}, message={}",
                    parentType, parentId, exception.getMessage());
            if (tenantId == null) {
                embeddingDispatcher.dispatchAfterCommit(parentType, parentId);
            } else {
                embeddingDispatcher.dispatchAfterCommit(tenantId, parentType, parentId);
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
