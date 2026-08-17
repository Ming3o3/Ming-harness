package org.mingharness.context;

import org.mingharness.common.BusinessException;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.context.api.CreateDocumentRequest;
import org.mingharness.context.api.CreateMemoryRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;

@Service
public class ContextService {

    private final KnowledgeDocumentRepository documentRepository;
    private final MemoryEntryRepository memoryRepository;
    private final ContextChunkRepository chunkRepository;
    private final ContextParentWindowRepository parentWindowRepository;
    private final ContextChunkWriter chunkWriter;
    private final ContextEmbeddingDispatcher embeddingDispatcher;
    private final ContextSemanticRechunkDispatcher semanticRechunkDispatcher;
    private final SensitiveDataSanitizer sanitizer;
    private final KnowledgeDocumentFileParser fileParser;
    private final DocumentImportStorage documentImportStorage;
    private final DocumentImportDispatcher documentImportDispatcher;
    private final TransactionTemplate transactionTemplate;

    public ContextService(KnowledgeDocumentRepository documentRepository,
                          MemoryEntryRepository memoryRepository,
                          ContextChunkRepository chunkRepository,
                          ContextParentWindowRepository parentWindowRepository,
                          ContextChunkWriter chunkWriter,
                          ContextEmbeddingDispatcher embeddingDispatcher,
                          ContextSemanticRechunkDispatcher semanticRechunkDispatcher,
                          SensitiveDataSanitizer sanitizer,
                          KnowledgeDocumentFileParser fileParser,
                          DocumentImportStorage documentImportStorage,
                          DocumentImportDispatcher documentImportDispatcher,
                          PlatformTransactionManager transactionManager) {
        this.documentRepository = documentRepository;
        this.memoryRepository = memoryRepository;
        this.chunkRepository = chunkRepository;
        this.parentWindowRepository = parentWindowRepository;
        this.chunkWriter = chunkWriter;
        this.embeddingDispatcher = embeddingDispatcher;
        this.semanticRechunkDispatcher = semanticRechunkDispatcher;
        this.sanitizer = sanitizer;
        this.fileParser = fileParser;
        this.documentImportStorage = documentImportStorage;
        this.documentImportDispatcher = documentImportDispatcher;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional
    public KnowledgeDocument createDocument(String tenantId, String userId, CreateDocumentRequest request) {
        return persistDocument(tenantId, userId, request.title(), request.content(),
                request.sensitivity(), request.allowedUsers());
    }

    /** 创建上传任务；正文解析和索引在后台按片段完成。 */
    public KnowledgeDocument createDocumentFromUpload(String tenantId, String userId,
                                                       MultipartFile file, String title,
                                                       String sensitivity, String allowedUsers) {
        String originalName = fileParser.validateUpload(file);
        String requestedTitle = title == null || title.isBlank() ? titleFromFile(originalName) : title;
        String sourcePath = documentImportStorage.stage(file).toString();
        try {
            KnowledgeDocument document = transactionTemplate.execute(status ->
                    persistPendingDocument(tenantId, userId, requestedTitle, sensitivity, allowedUsers,
                            sourcePath, originalName));
            if (document == null) {
                documentImportStorage.delete(sourcePath);
                throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                        "DOCUMENT_IMPORT_QUEUE_FULL", "当前文档导入任务较多，请稍后重试");
            }
            if (!documentImportDispatcher.dispatch(document.getId())) {
                transactionTemplate.executeWithoutResult(status -> documentRepository.findByIdForUpdate(document.getId())
                        .ifPresent(pending -> {
                            pending.markImportFailed("当前文档导入任务较多，请稍后重试");
                            documentRepository.save(pending);
                        }));
                documentImportStorage.delete(sourcePath);
                throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                        "DOCUMENT_IMPORT_QUEUE_FULL", "当前文档导入任务较多，请稍后重试");
            }
            return document;
        } catch (RuntimeException exception) {
            documentImportStorage.delete(sourcePath);
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public List<KnowledgeDocument> listDocuments(String tenantId, String userId) {
        return documentRepository.findTop100ByTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc(tenantId).stream()
                .filter(document -> document.isVisibleTo(userId))
                .toList();
    }

    @Transactional
    public void deleteDocument(String tenantId, String userId, String documentId) {
        KnowledgeDocument document = documentRepository.findById(documentId).orElseThrow(() ->
                new BusinessException(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", "文档不存在: " + documentId));
        assertTenant(document.getTenantId(), tenantId);
        if (!document.getOwnerUserId().equals(userId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "DOCUMENT_ACCESS_DENIED", "只有文档所有者可以删除文档");
        }
        document.markDeleted();
        documentRepository.save(document);
        documentImportStorage.delete(document.getImportSourcePath());
        markChunksDeleted("DOCUMENT", document.getId());
        markParentWindowsDeleted(tenantId, "DOCUMENT", document.getId());
    }

    @Transactional
    public MemoryEntry createMemory(String tenantId, String userId, CreateMemoryRequest request) {
        if (sanitizer.containsSensitiveData(request.content())) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "SENSITIVE_MEMORY_REJECTED",
                    "疑似密钥或凭证的信息不能写入长期记忆");
        }
        MemoryEntry memory = memoryRepository.save(new MemoryEntry(tenantId, userId, sanitizer.sanitize(request.memoryType()),
                sanitizer.sanitize(request.content()), sanitizer.sanitize(request.sourceRunId()), request.expiresAt()));
        writeChunksAndDispatch("MEMORY", memory.getId(), tenantId,
                memory.getMemoryType() + "\n" + memory.getContent());
        return memory;
    }

    @Transactional(readOnly = true)
    public List<MemoryEntry> listMemories(String tenantId, String userId) {
        Instant now = Instant.now();
        return memoryRepository.findTop100ByTenantIdAndUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(tenantId, userId)
                .stream().filter(memory -> memory.isActive(now)).toList();
    }

    @Transactional
    public void deleteMemory(String tenantId, String userId, String memoryId) {
        MemoryEntry memory = memoryRepository.findById(memoryId).orElseThrow(() ->
                new BusinessException(HttpStatus.NOT_FOUND, "MEMORY_NOT_FOUND", "记忆不存在: " + memoryId));
        assertTenant(memory.getTenantId(), tenantId);
        if (!memory.getUserId().equals(userId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "MEMORY_ACCESS_DENIED", "只能删除自己的长期记忆");
        }
        memory.markDeleted();
        memoryRepository.save(memory);
        markChunksDeleted("MEMORY", memory.getId());
        markParentWindowsDeleted(tenantId, "MEMORY", memory.getId());
    }

    private void assertTenant(String actualTenantId, String expectedTenantId) {
        if (expectedTenantId == null || !expectedTenantId.equals(actualTenantId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "TENANT_ACCESS_DENIED", "无权访问其他组织的上下文数据");
        }
    }

    private KnowledgeDocument persistDocument(String tenantId, String userId, String title, String content,
                                              String sensitivity, String allowedUsers) {
        String normalizedTitle = sanitizer.sanitize(title == null ? "" : title.trim());
        String normalizedContent = sanitizer.sanitize(content == null ? "" : content);
        if (normalizedTitle.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "DOCUMENT_TITLE_REQUIRED", "文档标题不能为空");
        }
        if (normalizedTitle.length() > 200) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "DOCUMENT_TITLE_TOO_LONG", "文档标题不能超过 200 个字符");
        }
        if (normalizedContent.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "DOCUMENT_CONTENT_REQUIRED", "文档内容不能为空");
        }
        KnowledgeDocument document = documentRepository.save(new KnowledgeDocument(tenantId, userId,
                normalizedTitle, normalizedContent, sanitizer.sanitize(sensitivity), sanitizer.sanitize(allowedUsers)));
        writeChunksAndDispatch("DOCUMENT", document.getId(), tenantId,
                document.getTitle() + "\n" + document.getContent());
        return document;
    }

    private KnowledgeDocument persistPendingDocument(String tenantId, String userId, String title,
                                                     String sensitivity, String allowedUsers,
                                                     String sourcePath, String sourceName) {
        String normalizedTitle = sanitizer.sanitize(title == null ? "" : title.trim());
        if (normalizedTitle.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "DOCUMENT_TITLE_REQUIRED", "文档标题不能为空");
        }
        if (normalizedTitle.length() > 200) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "DOCUMENT_TITLE_TOO_LONG", "文档标题不能超过 200 个字符");
        }
        return documentRepository.save(new KnowledgeDocument(tenantId, userId, normalizedTitle,
                sanitizer.sanitize(sensitivity), sanitizer.sanitize(allowedUsers), sourcePath, sourceName,
                DocumentImportStatus.PROCESSING));
    }

    private static String titleFromFile(String fileName) {
        String name = fileName == null || fileName.isBlank() ? "导入文档" : fileName;
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private void markChunksDeleted(String parentType, String parentId) {
        List<ContextChunk> chunks = chunkRepository
                .findByParentTypeAndParentIdAndDeletedAtIsNull(parentType, parentId);
        chunks.forEach(ContextChunk::markDeleted);
        if (!chunks.isEmpty()) {
            chunkRepository.saveAll(chunks);
        }
    }

    private void writeChunksAndDispatch(String parentType, String parentId,
                                        String tenantId, String content) {
        if (semanticRechunkDispatcher.enabled(tenantId)) {
            chunkWriter.replaceDeterministic(tenantId, parentType, parentId, content);
            if (!semanticRechunkDispatcher.dispatchAfterCommit(tenantId, parentType, parentId)) {
                embeddingDispatcher.dispatchAfterCommit(tenantId, parentType, parentId);
            }
            return;
        }
        chunkWriter.replace(tenantId, parentType, parentId, content);
        embeddingDispatcher.dispatchAfterCommit(tenantId, parentType, parentId);
    }

    private void markParentWindowsDeleted(String tenantId, String parentType, String parentId) {
        List<ContextParentWindow> windows = parentWindowRepository
                .findByTenantIdAndParentTypeAndParentIdAndDeletedAtIsNullOrderByWindowIndexAsc(
                        tenantId, parentType, parentId);
        windows.forEach(ContextParentWindow::markDeleted);
        if (!windows.isEmpty()) {
            parentWindowRepository.saveAll(windows);
        }
    }

}
