package org.mingharness.context;

import org.mingharness.common.BusinessException;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.context.api.CreateDocumentRequest;
import org.mingharness.context.api.CreateMemoryRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

@Service
public class ContextService {

    private final KnowledgeDocumentRepository documentRepository;
    private final MemoryEntryRepository memoryRepository;
    private final ContextChunkRepository chunkRepository;
    private final ContextChunker chunker;
    private final SensitiveDataSanitizer sanitizer;

    public ContextService(KnowledgeDocumentRepository documentRepository,
                          MemoryEntryRepository memoryRepository,
                          ContextChunkRepository chunkRepository,
                          ContextChunker chunker,
                          SensitiveDataSanitizer sanitizer) {
        this.documentRepository = documentRepository;
        this.memoryRepository = memoryRepository;
        this.chunkRepository = chunkRepository;
        this.chunker = chunker;
        this.sanitizer = sanitizer;
    }

    @Transactional
    public KnowledgeDocument createDocument(String tenantId, String userId, CreateDocumentRequest request) {
        KnowledgeDocument document = documentRepository.save(new KnowledgeDocument(tenantId, userId,
                sanitizer.sanitize(request.title()), sanitizer.sanitize(request.content()),
                sanitizer.sanitize(request.sensitivity()), sanitizer.sanitize(request.allowedUsers())));
        replaceChunks(tenantId, "DOCUMENT", document.getId(),
                document.getTitle() + "\n" + document.getContent());
        return document;
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
    }

    @Transactional
    public MemoryEntry createMemory(String tenantId, String userId, CreateMemoryRequest request) {
        if (sanitizer.containsSensitiveData(request.content())) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "SENSITIVE_MEMORY_REJECTED",
                    "疑似密钥或凭证的信息不能写入长期记忆");
        }
        MemoryEntry memory = memoryRepository.save(new MemoryEntry(tenantId, userId, sanitizer.sanitize(request.memoryType()),
                sanitizer.sanitize(request.content()), sanitizer.sanitize(request.sourceRunId()), request.expiresAt()));
        replaceChunks(tenantId, "MEMORY", memory.getId(),
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
    }

    private void assertTenant(String actualTenantId, String expectedTenantId) {
        if (expectedTenantId == null || !expectedTenantId.equals(actualTenantId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "TENANT_ACCESS_DENIED", "无权访问其他组织的上下文数据");
        }
    }

    private void replaceChunks(String tenantId, String parentType, String parentId, String content) {
        chunkRepository.deleteByParentTypeAndParentId(parentType, parentId);
        List<ContextChunk> chunks = new ArrayList<>();
        for (var draft : chunker.chunk(content)) {
            chunks.add(new ContextChunk(tenantId, parentType, parentId, draft.chunkIndex(),
                    draft.content(), sha256(draft.content())));
        }
        if (!chunks.isEmpty()) {
            chunkRepository.saveAll(chunks);
        }
    }

    private void markChunksDeleted(String parentType, String parentId) {
        List<ContextChunk> chunks = chunkRepository
                .findByParentTypeAndParentIdAndDeletedAtIsNull(parentType, parentId);
        chunks.forEach(ContextChunk::markDeleted);
        if (!chunks.isEmpty()) {
            chunkRepository.saveAll(chunks);
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM 缺少 SHA-256 算法", exception);
        }
    }
}
