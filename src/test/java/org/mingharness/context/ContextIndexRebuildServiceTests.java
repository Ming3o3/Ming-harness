package org.mingharness.context;

import org.junit.jupiter.api.Test;
import org.mingharness.context.api.ContextReindexRequest;
import org.mingharness.context.api.ContextReindexResponse;
import org.mingharness.observability.HarnessMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.mockito.ArgumentMatchers;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContextIndexRebuildServiceTests {

    @Test
    void shouldRebuildMissingActiveParentsWithoutTouchingExpiredMemories() {
        KnowledgeDocumentRepository documentRepository = mock(KnowledgeDocumentRepository.class);
        MemoryEntryRepository memoryRepository = mock(MemoryEntryRepository.class);
        ContextChunkRepository chunkRepository = mock(ContextChunkRepository.class);
        ContextChunkWriter chunkWriter = mock(ContextChunkWriter.class);
        ContextEmbeddingIndexer embeddingIndexer = mock(ContextEmbeddingIndexer.class);

        KnowledgeDocument document = new KnowledgeDocument("tenant-a", "owner", "规则",
                "订单需要审核", "INTERNAL", "");
        MemoryEntry activeMemory = new MemoryEntry("tenant-a", "operator", "preference",
                "偏好中文", null, Instant.now().plusSeconds(60));
        MemoryEntry expiredMemory = new MemoryEntry("tenant-a", "operator", "stale",
                "已经过期", null, Instant.now().minusSeconds(60));

        PageRequest page = PageRequest.of(0, 10);
        when(documentRepository.findByTenantIdAndDeletedAtIsNullOrderByCreatedAtAsc("tenant-a", page))
                .thenReturn(List.of(document));
        when(memoryRepository.findByTenantIdAndDeletedAtIsNullOrderByCreatedAtAsc("tenant-a", page))
                .thenReturn(List.of(activeMemory, expiredMemory));
        when(chunkWriter.hasActiveChunks("tenant-a", "DOCUMENT", document.getId())).thenReturn(false);
        when(chunkWriter.hasActiveChunks("tenant-a", "MEMORY", activeMemory.getId())).thenReturn(false);
        when(chunkWriter.replace("tenant-a", "DOCUMENT", document.getId(), "规则\n订单需要审核"))
                .thenReturn(2);
        when(chunkWriter.replace("tenant-a", "MEMORY", activeMemory.getId(), "preference\n偏好中文"))
                .thenReturn(2);
        when(chunkRepository.countByTenantIdAndParentTypeAndParentIdAndDeletedAtIsNull(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString(), ArgumentMatchers.anyString())).thenReturn(2L);
        when(embeddingIndexer.ready("tenant-a")).thenReturn(true);
        when(chunkRepository.findByTenantIdAndDeletedAtIsNullAndEmbeddedAtIsNullOrderByCreatedAtAsc(
                "tenant-a", PageRequest.of(0, 20))).thenReturn(List.of());
        when(chunkRepository.countByTenantIdAndDeletedAtIsNullAndEmbeddedAtIsNull("tenant-a"))
                .thenReturn(4L);

        ContextIndexRebuildService service = new ContextIndexRebuildService(documentRepository,
                memoryRepository, chunkRepository, chunkWriter, embeddingIndexer,
                new HarnessMetrics(new SimpleMeterRegistry()));
        ContextReindexResponse result = service.rebuild("tenant-a",
                new ContextReindexRequest("ALL", 10, 20));

        assertEquals("ALL", result.scope());
        assertEquals(2, result.parentsScanned());
        assertEquals(2, result.parentsRebuilt());
        assertEquals(4, result.chunksCreated());
        assertEquals(0, result.chunksIndexed());
        assertEquals(4, result.pendingChunks());
        assertTrue(result.embeddingReady());
        verify(chunkWriter).replace("tenant-a", "DOCUMENT", document.getId(), "规则\n订单需要审核");
        verify(chunkWriter).replace("tenant-a", "MEMORY", activeMemory.getId(), "preference\n偏好中文");
        verify(chunkWriter, never()).hasActiveChunks("tenant-a", "MEMORY", expiredMemory.getId());
    }

    @Test
    void shouldRechunkExistingParentsWhenExplicitlyRequested() {
        KnowledgeDocumentRepository documentRepository = mock(KnowledgeDocumentRepository.class);
        MemoryEntryRepository memoryRepository = mock(MemoryEntryRepository.class);
        ContextChunkRepository chunkRepository = mock(ContextChunkRepository.class);
        ContextChunkWriter chunkWriter = mock(ContextChunkWriter.class);
        ContextEmbeddingIndexer embeddingIndexer = mock(ContextEmbeddingIndexer.class);
        KnowledgeDocument document = new KnowledgeDocument("tenant-a", "owner", "规则",
                "新分块内容", "INTERNAL", "");
        when(documentRepository.findByTenantIdAndDeletedAtIsNullOrderByCreatedAtAsc("tenant-a", PageRequest.of(0, 1)))
                .thenReturn(List.of(document));
        when(chunkWriter.replace("tenant-a", "DOCUMENT", document.getId(), "规则\n新分块内容"))
                .thenReturn(3);
        when(embeddingIndexer.ready("tenant-a")).thenReturn(false);
        when(chunkRepository.findByTenantIdAndDeletedAtIsNullAndEmbeddedAtIsNullOrderByCreatedAtAsc(
                "tenant-a", PageRequest.of(0, 5))).thenReturn(List.of());
        when(chunkRepository.countByTenantIdAndDeletedAtIsNullAndEmbeddedAtIsNull("tenant-a"))
                .thenReturn(3L);

        ContextIndexRebuildService service = new ContextIndexRebuildService(documentRepository,
                memoryRepository, chunkRepository, chunkWriter, embeddingIndexer,
                new HarnessMetrics(new SimpleMeterRegistry()));
        ContextReindexResponse result = service.rebuild("tenant-a",
                new ContextReindexRequest("DOCUMENT", 1, 5, true));

        assertEquals(1, result.parentsRebuilt());
        assertEquals(3, result.chunksCreated());
        verify(chunkWriter).replace("tenant-a", "DOCUMENT", document.getId(), "规则\n新分块内容");
    }
}
