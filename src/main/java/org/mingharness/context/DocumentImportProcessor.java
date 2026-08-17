package org.mingharness.context;

import org.mingharness.common.BusinessException;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.config.ContextChunkingProperties;
import org.mingharness.config.DocumentImportProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** 在后台逐段解析文档，并以有界批次物化上下文索引。 */
@Service
public class DocumentImportProcessor {

    private static final Logger log = LoggerFactory.getLogger(DocumentImportProcessor.class);
    private static final int WINDOWS_PER_BATCH = 8;

    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeDocumentFileParser fileParser;
    private final DocumentImportStorage storage;
    private final StreamingContextChunkPersister persister;
    private final ContextChunker chunker;
    private final ContextChunkingProperties chunkingProperties;
    private final DocumentImportProperties properties;
    private final SensitiveDataSanitizer sanitizer;
    private final ContextEmbeddingDispatcher embeddingDispatcher;
    private final ContextSemanticRechunkDispatcher semanticRechunkDispatcher;
    private final TransactionTemplate transactionTemplate;

    public DocumentImportProcessor(KnowledgeDocumentRepository documentRepository,
                                   KnowledgeDocumentFileParser fileParser,
                                   DocumentImportStorage storage,
                                   StreamingContextChunkPersister persister,
                                   ContextChunker chunker,
                                   ContextChunkingProperties chunkingProperties,
                                   DocumentImportProperties properties,
                                   SensitiveDataSanitizer sanitizer,
                                   ContextEmbeddingDispatcher embeddingDispatcher,
                                   ContextSemanticRechunkDispatcher semanticRechunkDispatcher,
                                   org.springframework.transaction.PlatformTransactionManager transactionManager) {
        this.documentRepository = documentRepository;
        this.fileParser = fileParser;
        this.storage = storage;
        this.persister = persister;
        this.chunker = chunker;
        this.chunkingProperties = chunkingProperties;
        this.properties = properties;
        this.sanitizer = sanitizer;
        this.embeddingDispatcher = embeddingDispatcher;
        this.semanticRechunkDispatcher = semanticRechunkDispatcher;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void process(String documentId) {
        KnowledgeDocument document = documentRepository.findById(documentId).orElse(null);
        if (document == null || document.getDeletedAt() != null || document.isReady()
                || document.getImportStatus() == DocumentImportStatus.FAILED) {
            return;
        }
        String sourcePath = document.getImportSourcePath();
        if (sourcePath == null || sourcePath.isBlank()) {
            markFailed(documentId, "异步导入文件暂存路径不存在");
            return;
        }
        try {
            Path path = Path.of(sourcePath);
            if (!Files.exists(path)) {
                throw new BusinessException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                        "DOCUMENT_IMPORT_SOURCE_MISSING", "异步导入文件已失效，请重新上传");
            }
            persister.clear(document.getTenantId(), "DOCUMENT", document.getId());
            ImportAccumulator accumulator = new ImportAccumulator(document.getTenantId(), document.getId());
            ParsedDocumentMetadata metadata = fileParser.parseSegments(path, document.getImportSourceName(), segment -> {
                String text = sanitizer.sanitize(segment.text());
                if (text == null || text.isBlank()) return;
                accumulator.accept(text);
            });
            accumulator.finish();
            if (accumulator.contentCharCount <= 0 || accumulator.chunkCount <= 0) {
                throw new BusinessException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                        "DOCUMENT_TEXT_EMPTY", "文件中没有可用于检索的文本内容");
            }
            markReady(documentId, accumulator.contentCharCount, metadata.pageCount());
            if (semanticRechunkDispatcher.enabled(document.getTenantId()) && !document.getContent().isBlank()) {
                semanticRechunkDispatcher.dispatchAfterCommit(document.getTenantId(), "DOCUMENT", documentId);
            } else {
                embeddingDispatcher.dispatchAfterCommit(document.getTenantId(), "DOCUMENT", documentId);
            }
        } catch (BusinessException exception) {
            persister.clear(document.getTenantId(), "DOCUMENT", document.getId());
            markFailed(documentId, exception.getMessage());
        } catch (Exception exception) {
            persister.clear(document.getTenantId(), "DOCUMENT", document.getId());
            log.warn("知识文档异步导入失败，documentId={}, message={}", documentId, exception.getMessage(), exception);
            markFailed(documentId, "无法解析该文件，请确认文件未损坏且未加密");
        } finally {
            storage.delete(sourcePath);
        }
    }

    private void markReady(String documentId, int contentCharCount, int pageCount) {
        transactionTemplate.executeWithoutResult(status -> documentRepository.findByIdForUpdate(documentId)
                .filter(document -> document.getDeletedAt() == null)
                .ifPresent(document -> {
                    document.markImportReady(contentCharCount, pageCount);
                    documentRepository.save(document);
                }));
    }

    private void markFailed(String documentId, String message) {
        String safeMessage = sanitizer.sanitize(message);
        transactionTemplate.executeWithoutResult(status -> documentRepository.findByIdForUpdate(documentId)
                .filter(document -> document.getDeletedAt() == null)
                .ifPresent(document -> {
                    document.markImportFailed(safeMessage);
                    documentRepository.save(document);
                }));
    }

    private final class ImportAccumulator {
        private final String tenantId;
        private final String documentId;
        private final List<StreamingContextChunkPersister.StreamingWindow> pendingWindows = new ArrayList<>();
        private final List<ContextChunkDraft> currentChunks = new ArrayList<>();
        private int currentLength;
        private int nextChunkIndex;
        private int nextWindowIndex;
        private int contentCharCount;
        private int chunkCount;

        private ImportAccumulator(String tenantId, String documentId) {
            this.tenantId = tenantId;
            this.documentId = documentId;
        }

        private void accept(String text) {
            contentCharCount += text.length();
            if (contentCharCount > properties.maxContentChars()) {
                throw new BusinessException(org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE,
                        "DOCUMENT_TEXT_TOO_LARGE",
                        "解析后的正文不能超过 " + properties.maxContentChars() + " 个字符");
            }
            for (ContextChunkDraft draft : chunker.chunk(text)) {
                if (draft.content() == null || draft.content().isBlank()) continue;
                addChunk(new ContextChunkDraft(nextChunkIndex++, draft.content()));
            }
        }

        private void addChunk(ContextChunkDraft draft) {
            int required = currentChunks.isEmpty() ? draft.content().length()
                    : currentLength + 2 + draft.content().length();
            if (!currentChunks.isEmpty() && required > chunkingProperties.parentWindowMaxChars()) {
                flushWindow();
            }
            currentChunks.add(draft);
            currentLength = currentChunks.size() == 1 ? draft.content().length() : currentLength + 2 + draft.content().length();
            chunkCount++;
        }

        private void flushWindow() {
            if (currentChunks.isEmpty()) return;
            String content = currentChunks.stream().map(ContextChunkDraft::content)
                    .reduce((left, right) -> left + "\n\n" + right).orElse("");
            pendingWindows.add(new StreamingContextChunkPersister.StreamingWindow(
                    nextWindowIndex++, content, List.copyOf(currentChunks)));
            currentChunks.clear();
            currentLength = 0;
            if (pendingWindows.size() >= WINDOWS_PER_BATCH) flushBatch();
        }

        private void flushBatch() {
            if (pendingWindows.isEmpty()) return;
            persister.append(tenantId, "DOCUMENT", documentId, List.copyOf(pendingWindows));
            pendingWindows.clear();
        }

        private void finish() {
            flushWindow();
            flushBatch();
        }
    }
}
