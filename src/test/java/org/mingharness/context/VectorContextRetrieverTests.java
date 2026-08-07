package org.mingharness.context;

import org.junit.jupiter.api.Test;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.config.ContextRetrievalProperties;
import org.mingharness.config.EmbeddingProperties;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class VectorContextRetrieverTests {

    @Test
    void shouldExpandAuthorizedHitWithAdjacentParentChunks() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        ContextChunkRepository chunkRepository = mock(ContextChunkRepository.class);
        EmbeddingGateway gateway = mock(EmbeddingGateway.class);
        ContextEmbeddingStore store = mock(ContextEmbeddingStore.class);
        EmbeddingProperties embeddingProperties = new EmbeddingProperties(true, "http://embedding", "key", "model",
                2, 8, 1_000, 100_000, 1, 0, 10_000);
        ContextRetrievalProperties retrievalProperties = new ContextRetrievalProperties(20, 5, 1, 0.2);
        VectorContextRetriever retriever = new VectorContextRetriever(jdbcTemplate, chunkRepository, gateway, store,
                embeddingProperties, retrievalProperties, new SensitiveDataSanitizer());
        ContextChunk first = new ContextChunk("tenant-a", "DOCUMENT", "doc-1", 0, "前置条件", "hash-1");
        ContextChunk hit = new ContextChunk("tenant-a", "DOCUMENT", "doc-1", 1, "回滚步骤", "hash-2");
        ContextChunk last = new ContextChunk("tenant-a", "DOCUMENT", "doc-1", 2, "验证结果", "hash-3");
        when(gateway.enabled()).thenReturn(true);
        when(store.supported()).thenReturn(true);
        when(gateway.embed(List.of("如何回滚发布")))
                .thenReturn(List.of(new EmbeddingVector("model", List.of(0.1, 0.2))));
        when(jdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(new VectorContextRetriever.VectorHit("chunk-2", "DOCUMENT", "doc-1", 1,
                        "回滚步骤", "发布回滚", 0.91)));
        when(chunkRepository.findByTenantIdAndParentTypeAndParentIdAndDeletedAtIsNullOrderByChunkIndexAsc(
                "tenant-a", "DOCUMENT", "doc-1")).thenReturn(List.of(first, hit, last));

        var result = retriever.retrieve("tenant-a", "operator", "如何回滚发布", 4_000);

        assertEquals(1, result.evidences().size());
        assertEquals("document:doc-1#chunk:1", result.evidences().get(0).citation());
        assertTrue(result.text().contains("前置条件"));
        assertTrue(result.text().contains("回滚步骤"));
        assertTrue(result.text().contains("验证结果"));

        ArgumentCaptor<SqlParameterSource> parameters = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbcTemplate).query(anyString(), parameters.capture(), any(RowMapper.class));
        assertEquals(60, parameters.getValue().getValue("candidateLimit"));
    }

    @Test
    void shouldSkipVectorQueryWhenEmbeddingIsUnavailable() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        ContextChunkRepository chunkRepository = mock(ContextChunkRepository.class);
        EmbeddingGateway gateway = mock(EmbeddingGateway.class);
        ContextEmbeddingStore store = mock(ContextEmbeddingStore.class);
        EmbeddingProperties embeddingProperties = new EmbeddingProperties(false, "http://embedding", "key", "model",
                2, 8, 1_000, 100_000, 1, 0, 10_000);
        ContextRetrievalProperties retrievalProperties = new ContextRetrievalProperties(20, 5, 1, 0.2);
        VectorContextRetriever retriever = new VectorContextRetriever(jdbcTemplate, chunkRepository, gateway, store,
                embeddingProperties, retrievalProperties, new SensitiveDataSanitizer());
        when(gateway.enabled()).thenReturn(false);
        when(store.supported()).thenReturn(true);

        var result = retriever.retrieve("tenant-a", "operator", "查询", 4_000);

        assertTrue(result.isEmpty());
        verifyNoInteractions(jdbcTemplate, chunkRepository);
    }

    @Test
    void shouldReturnBoundedParentWindowForAChildVectorHit() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        ContextChunkRepository chunkRepository = mock(ContextChunkRepository.class);
        ContextParentWindowRepository windowRepository = mock(ContextParentWindowRepository.class);
        EmbeddingGateway gateway = mock(EmbeddingGateway.class);
        ContextEmbeddingStore store = mock(ContextEmbeddingStore.class);
        EmbeddingProperties embeddingProperties = new EmbeddingProperties(true, "http://embedding", "key", "model",
                2, 8, 1_000, 100_000, 1, 0, 10_000);
        ContextRetrievalProperties retrievalProperties = new ContextRetrievalProperties(20, 5, 1, 0.2);
        VectorContextRetriever retriever = new VectorContextRetriever(jdbcTemplate, chunkRepository,
                windowRepository, gateway, store, embeddingProperties, retrievalProperties,
                new SensitiveDataSanitizer(), null);
        String windowId = "window-1";
        ContextParentWindow window = new ContextParentWindow("tenant-a", "DOCUMENT", "doc-1", 2,
                "标题\n\n前置条件\n\n回滚步骤\n\n验证结果" + "x".repeat(500), "window-hash");
        when(gateway.enabled()).thenReturn(true);
        when(store.supported()).thenReturn(true);
        when(gateway.embed(List.of("如何回滚发布")))
                .thenReturn(List.of(new EmbeddingVector("model", List.of(0.1, 0.2))));
        when(jdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(new VectorContextRetriever.VectorHit("chunk-2", "DOCUMENT", "doc-1", 1,
                        "回滚步骤", windowId, 2, "发布回滚", 0.91)));
        when(windowRepository.findByIdAndTenantIdAndParentTypeAndParentIdAndDeletedAtIsNull(
                windowId, "tenant-a", "DOCUMENT", "doc-1")).thenReturn(window);

        var result = retriever.retrieve("tenant-a", "operator", "如何回滚发布", 400);

        assertEquals(1, result.evidences().size());
        assertEquals("document:doc-1#window:2#chunk:1", result.evidences().get(0).citation());
        assertTrue(result.evidences().get(0).excerpt().length() <= 400);
        assertTrue(result.evidences().get(0).excerpt().contains("标题"));
        verifyNoInteractions(chunkRepository);
    }

    @Test
    void shouldReuseCachedQueryEmbeddingAndSkipProviderCall() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        ContextChunkRepository chunkRepository = mock(ContextChunkRepository.class);
        ContextEmbeddingCache cache = mock(ContextEmbeddingCache.class);
        EmbeddingGateway gateway = mock(EmbeddingGateway.class);
        ContextEmbeddingStore store = mock(ContextEmbeddingStore.class);
        EmbeddingProperties embeddingProperties = new EmbeddingProperties(true, "http://embedding", "key", "model",
                2, 8, 1_000, 100_000, 1, 0, 10_000);
        ContextRetrievalProperties retrievalProperties = new ContextRetrievalProperties(20, 5, 1, 0.2);
        VectorContextRetriever retriever = new VectorContextRetriever(jdbcTemplate, chunkRepository, null, gateway,
                store, embeddingProperties, retrievalProperties, new SensitiveDataSanitizer(), cache, null);
        EmbeddingVector cached = new EmbeddingVector("model", List.of(0.1, 0.2));
        when(gateway.enabled()).thenReturn(true);
        when(store.supported()).thenReturn(true);
        when(cache.find(eq("tenant-a"), anyString(), eq("model"), eq("v1"), eq(2)))
                .thenReturn(java.util.Optional.of(cached));
        when(jdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        var result = retriever.retrieve("tenant-a", "operator", "如何回滚发布", 4_000);

        assertTrue(result.isEmpty());
        verify(cache).find(eq("tenant-a"), anyString(), eq("model"), eq("v1"), eq(2));
        verify(gateway, never()).embed(any());
    }

    @Test
    void shouldCacheFreshQueryEmbeddingAfterProviderCall() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        ContextChunkRepository chunkRepository = mock(ContextChunkRepository.class);
        ContextEmbeddingCache cache = mock(ContextEmbeddingCache.class);
        EmbeddingGateway gateway = mock(EmbeddingGateway.class);
        ContextEmbeddingStore store = mock(ContextEmbeddingStore.class);
        EmbeddingProperties embeddingProperties = new EmbeddingProperties(true, "http://embedding", "key", "model",
                2, 8, 1_000, 100_000, 1, 0, 10_000);
        ContextRetrievalProperties retrievalProperties = new ContextRetrievalProperties(20, 5, 1, 0.2);
        VectorContextRetriever retriever = new VectorContextRetriever(jdbcTemplate, chunkRepository, null, gateway,
                store, embeddingProperties, retrievalProperties, new SensitiveDataSanitizer(), cache, null);
        EmbeddingVector vector = new EmbeddingVector("model", List.of(0.1, 0.2));
        when(gateway.enabled()).thenReturn(true);
        when(store.supported()).thenReturn(true);
        when(cache.find(eq("tenant-a"), anyString(), eq("model"), eq("v1"), eq(2)))
                .thenReturn(java.util.Optional.empty());
        when(gateway.embed(List.of("如何回滚发布"))).thenReturn(List.of(vector));
        when(jdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        retriever.retrieve("tenant-a", "operator", "如何回滚发布", 4_000);

        verify(cache).save(eq("tenant-a"), anyString(), eq("model"), eq("v1"), eq(vector));
    }
}
