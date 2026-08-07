package org.mingharness.context;

import org.junit.jupiter.api.Test;
import org.mingharness.config.ContextChunkingProperties;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContextChunkWriterTests {

    @Test
    void shouldPersistDeterministicChunksWithoutCallingSemanticChunking() {
        ContextChunkRepository chunkRepository = mock(ContextChunkRepository.class);
        ContextParentWindowRepository windowRepository = mock(ContextParentWindowRepository.class);
        ContextSemanticChunker semanticChunker = mock(ContextSemanticChunker.class);
        ContextChunkingProperties properties = new ContextChunkingProperties(128, 16, true, 0.35, 3, 128);
        ContextChunkWriter writer = new ContextChunkWriter(chunkRepository, windowRepository,
                semanticChunker, properties);
        ContextChunkingResult result = new ContextChunkingResult(
                List.of(new ContextChunkDraft(0, "确定性正文")), "DETERMINISTIC", "deterministic-v1");
        when(semanticChunker.deterministicOnly("正文")).thenReturn(result);

        assertEquals(1, writer.replaceDeterministic("tenant-a", "DOCUMENT", "doc-1", "正文"));

        verify(semanticChunker).deterministicOnly("正文");
        verify(semanticChunker, never()).chunk("正文");
    }

    @Test
    void shouldMaterializeContiguousChunksIntoBoundedParentWindows() {
        ContextChunkRepository chunkRepository = mock(ContextChunkRepository.class);
        ContextParentWindowRepository windowRepository = mock(ContextParentWindowRepository.class);
        ContextSemanticChunker semanticChunker = mock(ContextSemanticChunker.class);
        ContextChunkingProperties properties = new ContextChunkingProperties(128, 16, false, 0.35, 3, 128);
        ContextChunkWriter writer = new ContextChunkWriter(chunkRepository, windowRepository,
                semanticChunker, properties);
        String first = "a".repeat(80);
        String second = "b".repeat(30);
        String third = "c".repeat(20);
        when(semanticChunker.chunk("tenant-a", "正文")).thenReturn(new ContextChunkingResult(List.of(
                new ContextChunkDraft(0, first),
                new ContextChunkDraft(1, second),
                new ContextChunkDraft(2, third)), "DETERMINISTIC", "deterministic-v1"));

        int created = writer.replace("tenant-a", "DOCUMENT", "doc-1", "正文");

        assertEquals(3, created);
        var windowCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
        var chunkCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(windowRepository).saveAll(windowCaptor.capture());
        verify(chunkRepository).saveAll(chunkCaptor.capture());

        @SuppressWarnings("unchecked")
        List<ContextParentWindow> windows = (List<ContextParentWindow>) windowCaptor.getValue();
        @SuppressWarnings("unchecked")
        List<ContextChunk> chunks = (List<ContextChunk>) chunkCaptor.getValue();
        assertEquals(2, windows.size());
        assertEquals(first + "\n\n" + second, windows.get(0).getContent());
        assertEquals(third, windows.get(1).getContent());
        assertEquals(windows.get(0).getId(), chunks.get(0).getParentWindowId());
        assertEquals(windows.get(0).getId(), chunks.get(1).getParentWindowId());
        assertEquals(windows.get(1).getId(), chunks.get(2).getParentWindowId());
        assertNotNull(chunks.get(0).getContentHash());
        verify(windowRepository).deleteByParentTypeAndParentId("DOCUMENT", "doc-1");
        verify(chunkRepository).deleteByParentTypeAndParentId("DOCUMENT", "doc-1");
    }
}
