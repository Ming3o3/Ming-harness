package org.mingharness.context;

import org.mingharness.common.BusinessException;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.context.api.CreateDocumentRequest;
import org.mingharness.context.api.CreateMemoryRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    public ContextService(KnowledgeDocumentRepository documentRepository,
                          MemoryEntryRepository memoryRepository,
                          ContextChunkRepository chunkRepository,
                          ContextParentWindowRepository parentWindowRepository,
                          ContextChunkWriter chunkWriter,
                          ContextEmbeddingDispatcher embeddingDispatcher,
                          ContextSemanticRechunkDispatcher semanticRechunkDispatcher,
                          SensitiveDataSanitizer sanitizer,
                          KnowledgeDocumentFileParser fileParser) {
        this.documentRepository = documentRepository;
        this.memoryRepository = memoryRepository;
        this.chunkRepository = chunkRepository;
        this.parentWindowRepository = parentWindowRepository;
        this.chunkWriter = chunkWriter;
        this.embeddingDispatcher = embeddingDispatcher;
        this.semanticRechunkDispatcher = semanticRechunkDispatcher;
        this.sanitizer = sanitizer;
        this.fileParser = fileParser;
    }

    @Transactional
    public KnowledgeDocument createDocument(String tenantId, String userId, CreateDocumentRequest request) {
        return persistDocument(tenantId, userId, request.title(), request.content(),
                request.sensitivity(), request.allowedUsers());
    }

    /** 解析上传的 PDF/DOCX 后复用同一套权限、切块和 embedding 索引流程。 */
    @Transactional
    public KnowledgeDocument createDocumentFromUpload(String tenantId, String userId,
                                                       MultipartFile file, String title,
                                                       String sensitivity, String allowedUsers) {
        ParsedKnowledgeDocument parsed = fileParser.parse(file);
        String requestedTitle = title == null || title.isBlank() ? titleFromFile(parsed.originalName()) : title;
        return persistDocument(tenantId, userId, requestedTitle, parsed.text(), sensitivity, allowedUsers);
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
