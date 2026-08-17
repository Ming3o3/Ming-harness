package org.mingharness.context;

import jakarta.annotation.PreDestroy;
import org.mingharness.config.DocumentImportProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** 异步文档导入队列；应用重启后会重新接管仍处于 PROCESSING 的任务。 */
@Service
public class DocumentImportDispatcher {

    private static final Logger log = LoggerFactory.getLogger(DocumentImportDispatcher.class);

    private final DocumentImportProcessor processor;
    private final KnowledgeDocumentRepository documentRepository;
    private final DocumentImportStorage storage;
    private final ThreadPoolExecutor executor;

    public DocumentImportDispatcher(DocumentImportProcessor processor,
                                    KnowledgeDocumentRepository documentRepository,
                                    DocumentImportStorage storage,
                                    DocumentImportProperties properties) {
        this.processor = processor;
        this.documentRepository = documentRepository;
        this.storage = storage;
        AtomicInteger threadNumber = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable,
                    "ming-harness-document-importer-" + threadNumber.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        this.executor = new ThreadPoolExecutor(
                properties.workerCount(), properties.workerCount(), 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(properties.queueCapacity()), factory,
                new ThreadPoolExecutor.AbortPolicy());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverPendingImports() {
        var pending = documentRepository.findByImportStatusAndImportSourcePathIsNotNull(DocumentImportStatus.PROCESSING);
        storage.cleanupUnreferenced(pending.stream().map(KnowledgeDocument::getImportSourcePath).toList());
        pending
                .forEach(document -> dispatch(document.getId()));
    }

    public boolean dispatch(String documentId) {
        try {
            executor.execute(() -> processor.process(documentId));
            return true;
        } catch (RejectedExecutionException exception) {
            log.warn("知识文档异步导入队列已满，documentId={}", documentId);
            return false;
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
