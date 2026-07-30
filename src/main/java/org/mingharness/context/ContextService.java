package org.mingharness.context;

import org.mingharness.common.BusinessException;
import org.mingharness.context.api.CreateDocumentRequest;
import org.mingharness.context.api.CreateMemoryRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class ContextService {

    private final KnowledgeDocumentRepository documentRepository;
    private final MemoryEntryRepository memoryRepository;

    public ContextService(KnowledgeDocumentRepository documentRepository,
                          MemoryEntryRepository memoryRepository) {
        this.documentRepository = documentRepository;
        this.memoryRepository = memoryRepository;
    }

    @Transactional
    public KnowledgeDocument createDocument(String tenantId, String userId, CreateDocumentRequest request) {
        return documentRepository.save(new KnowledgeDocument(tenantId, userId, request.title(), request.content(),
                request.sensitivity(), request.allowedUsers()));
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
    }

    @Transactional
    public MemoryEntry createMemory(String tenantId, String userId, CreateMemoryRequest request) {
        if (request.content().matches("(?is).*\\b(api[_-]?key|password|passwd|secret|token)\\b.*")) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "SENSITIVE_MEMORY_REJECTED",
                    "疑似密钥或凭证的信息不能写入长期记忆");
        }
        return memoryRepository.save(new MemoryEntry(tenantId, userId, request.memoryType(), request.content(),
                request.sourceRunId(), request.expiresAt()));
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
    }

    private void assertTenant(String actualTenantId, String expectedTenantId) {
        if (expectedTenantId == null || !expectedTenantId.equals(actualTenantId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "TENANT_ACCESS_DENIED", "无权访问其他租户的上下文数据");
        }
    }
}
