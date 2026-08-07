package org.mingharness.context;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContextSemanticRechunkerTests {

    @Test
    void shouldLockActiveDocumentBeforeReplacingChunksAndReindexing() {
        KnowledgeDocumentRepository documentRepository = mock(KnowledgeDocumentRepository.class);
        MemoryEntryRepository memoryRepository = mock(MemoryEntryRepository.class);
        ContextChunkWriter chunkWriter = mock(ContextChunkWriter.class);
        ContextEmbeddingDispatcher embeddingDispatcher = mock(ContextEmbeddingDispatcher.class);
        KnowledgeDocument document = new KnowledgeDocument(
                "tenant-a", "owner", "部署说明", "先准备数据库", "INTERNAL", "");
        when(documentRepository.findByIdForUpdate(document.getId())).thenReturn(Optional.of(document));

        ContextSemanticRechunker rechunker = new ContextSemanticRechunker(documentRepository, memoryRepository,
                chunkWriter, embeddingDispatcher);

        rechunker.rechunk("DOCUMENT", document.getId());

        verify(documentRepository).findByIdForUpdate(document.getId());
        verify(chunkWriter).replaceSemantic("tenant-a", "DOCUMENT", document.getId(),
                "部署说明\n先准备数据库");
        verify(embeddingDispatcher).dispatchAfterCommit("DOCUMENT", document.getId());
    }

    @Test
    void shouldIgnoreDeletedMemory() {
        KnowledgeDocumentRepository documentRepository = mock(KnowledgeDocumentRepository.class);
        MemoryEntryRepository memoryRepository = mock(MemoryEntryRepository.class);
        ContextChunkWriter chunkWriter = mock(ContextChunkWriter.class);
        ContextEmbeddingDispatcher embeddingDispatcher = mock(ContextEmbeddingDispatcher.class);
        MemoryEntry memory = new MemoryEntry("tenant-a", "operator", "preference", "内容", "run-1", null);
        memory.markDeleted();
        when(memoryRepository.findByIdForUpdate(memory.getId())).thenReturn(Optional.of(memory));

        ContextSemanticRechunker rechunker = new ContextSemanticRechunker(documentRepository, memoryRepository,
                chunkWriter, embeddingDispatcher);

        rechunker.rechunk("MEMORY", memory.getId());

        verify(memoryRepository).findByIdForUpdate(memory.getId());
        org.mockito.Mockito.verifyNoInteractions(chunkWriter, embeddingDispatcher);
    }
}
