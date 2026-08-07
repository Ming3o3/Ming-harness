package org.mingharness.context;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 在独立事务中完成语义重分块，并在新子块提交后触发向量索引。 */
@Service
public class ContextSemanticRechunker {

    private final KnowledgeDocumentRepository documentRepository;
    private final MemoryEntryRepository memoryRepository;
    private final ContextChunkWriter chunkWriter;
    private final ContextEmbeddingDispatcher embeddingDispatcher;

    public ContextSemanticRechunker(KnowledgeDocumentRepository documentRepository,
                                    MemoryEntryRepository memoryRepository,
                                    ContextChunkWriter chunkWriter,
                                    ContextEmbeddingDispatcher embeddingDispatcher) {
        this.documentRepository = documentRepository;
        this.memoryRepository = memoryRepository;
        this.chunkWriter = chunkWriter;
        this.embeddingDispatcher = embeddingDispatcher;
    }

    @Transactional
    public void rechunk(String parentType, String parentId) {
        ParentContent parent = findActiveParent(parentType, parentId);
        if (parent == null) return;
        chunkWriter.replaceSemantic(parent.tenantId(), parent.type(), parent.id(), parent.content());
        embeddingDispatcher.dispatchAfterCommit(parent.type(), parent.id());
    }

    private ParentContent findActiveParent(String parentType, String parentId) {
        if ("DOCUMENT".equals(parentType)) {
            return documentRepository.findByIdForUpdate(parentId)
                    .filter(document -> document.getDeletedAt() == null)
                    .map(document -> new ParentContent("DOCUMENT", document.getId(), document.getTenantId(),
                            document.getTitle() + "\n" + document.getContent()))
                    .orElse(null);
        }
        if ("MEMORY".equals(parentType)) {
            return memoryRepository.findByIdForUpdate(parentId)
                    .filter(memory -> memory.getDeletedAt() == null)
                    .map(memory -> new ParentContent("MEMORY", memory.getId(), memory.getTenantId(),
                            memory.getMemoryType() + "\n" + memory.getContent()))
                    .orElse(null);
        }
        return null;
    }

    private record ParentContent(String type, String id, String tenantId, String content) {
    }
}
