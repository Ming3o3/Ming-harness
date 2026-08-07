package org.mingharness.integration;

import org.junit.jupiter.api.Test;
import org.mingharness.context.ContextChunk;
import org.mingharness.context.ContextChunkRepository;
import org.mingharness.context.ContextEmbeddingCache;
import org.mingharness.context.ContextEmbeddingStore;
import org.mingharness.context.ContextEmbeddingUpdate;
import org.mingharness.context.ContextParentWindow;
import org.mingharness.context.ContextParentWindowRepository;
import org.mingharness.context.EmbeddingVector;
import org.mingharness.context.EmbeddingGateway;
import org.mingharness.context.VectorContextRetriever;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.config.ContextRetrievalProperties;
import org.mingharness.config.EmbeddingProperties;
import org.mingharness.context.KnowledgeDocument;
import org.mingharness.context.KnowledgeDocumentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 在真实 PostgreSQL + pgvector 上验证迁移、向量写入和余弦运算。 */
@SpringBootTest
@ActiveProfiles("local-infra")
@TestPropertySource(properties = {
        "spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/ming_harness}",
        "spring.datasource.username=${DB_USERNAME:ming_harness}",
        "spring.datasource.password=${DB_PASSWORD:ming_harness}",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "harness.execution.mode=rabbit",
        "harness.redis.enabled=true",
        "harness.messaging.enabled=true"
})
class ContextPgVectorIT {

    @Autowired
    private ContextChunkRepository chunkRepository;
    @Autowired
    private ContextEmbeddingStore embeddingStore;
    @Autowired
    private ContextEmbeddingCache embeddingCache;
    @Autowired
    private ContextParentWindowRepository parentWindowRepository;
    @Autowired
    private KnowledgeDocumentRepository documentRepository;
    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldWrite1536DimensionVectorAndUseHnswCosineOperator() {
        assertTrue(embeddingStore.supported());
        String tenantId = "tenant-vector-" + UUID.randomUUID();
        ContextChunk chunk = chunkRepository.saveAndFlush(new ContextChunk(
                tenantId, "DOCUMENT", "document-" + UUID.randomUUID(), 0,
                "pgvector integration", "hash-vector"));
        List<Double> values = new ArrayList<>(java.util.Collections.nCopies(1536, 0.0));
        values.set(0, 1.0);

        embeddingStore.save(List.of(new ContextEmbeddingUpdate(chunk,
                new EmbeddingVector("integration-model", values))));

        Integer dimensions = jdbcTemplate.queryForObject(
                "SELECT vector_dims(embedding) FROM harness_context_chunks WHERE id = ?",
                Integer.class, chunk.getId());
        String model = jdbcTemplate.queryForObject(
                "SELECT embedding_model FROM harness_context_chunks WHERE id = ?",
                String.class, chunk.getId());
        Double distance = jdbcTemplate.queryForObject(
                "SELECT embedding <=> CAST(? AS vector) FROM harness_context_chunks WHERE id = ?",
                Double.class, vectorLiteral(values), chunk.getId());
        Integer hnswIndexes = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE tablename = 'harness_context_chunks' "
                        + "AND indexname = 'idx_harness_context_chunks_embedding_hnsw'",
                Integer.class);

        assertEquals(1536, dimensions);
        assertEquals("integration-model", model);
        assertEquals(0.0, distance, 0.000001);
        assertEquals(1, hnswIndexes);

        jdbcTemplate.update("DELETE FROM harness_context_chunks WHERE id = ?", chunk.getId());
    }

    @Test
    void shouldPersistParentWindowRelationForChildChunks() {
        String tenantId = "tenant-window-" + UUID.randomUUID();
        String parentId = "document-" + UUID.randomUUID();
        ContextParentWindow window = parentWindowRepository.saveAndFlush(new ContextParentWindow(
                tenantId, "DOCUMENT", parentId, 0, "标题\n\n正文窗口", "window-hash"));
        ContextChunk chunk = chunkRepository.saveAndFlush(new ContextChunk(
                tenantId, "DOCUMENT", parentId, 0, "正文窗口", "chunk-hash",
                "DETERMINISTIC", "deterministic-v1", window.getId()));

        String linkedWindowId = jdbcTemplate.queryForObject(
                "SELECT parent_window_id FROM harness_context_chunks WHERE id = ?",
                String.class, chunk.getId());
        Integer windows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM harness_context_parent_windows WHERE id = ? AND window_index = 0",
                Integer.class, window.getId());

        assertEquals(window.getId(), linkedWindowId);
        assertEquals(1, windows);

        jdbcTemplate.update("DELETE FROM harness_context_chunks WHERE id = ?", chunk.getId());
        jdbcTemplate.update("DELETE FROM harness_context_parent_windows WHERE id = ?", window.getId());
    }

    @Test
    void shouldReuseTenantAndModelScopedEmbeddingCache() {
        assertTrue(embeddingCache instanceof org.mingharness.context.JdbcContextEmbeddingCache);
        String tenantId = "tenant-cache-" + UUID.randomUUID();
        String hash = "cache-hash-" + UUID.randomUUID();
        List<Double> values = new ArrayList<>(java.util.Collections.nCopies(1536, 0.0));
        values.set(0, 0.75);
        EmbeddingVector vector = new EmbeddingVector("response-model", values);

        embeddingCache.save(tenantId, hash, "requested-model", "v1", vector);

        var cached = embeddingCache.find(tenantId, hash, "requested-model", "v1", 1536);
        assertTrue(cached.isPresent());
        assertEquals("response-model", cached.orElseThrow().model());
        assertEquals(values, cached.orElseThrow().values());
        assertTrue(embeddingCache.find(tenantId, hash, "other-model", "v1", 1536).isEmpty());
        assertTrue(embeddingCache.find(tenantId, hash, "requested-model", "v2", 1536).isEmpty());

        jdbcTemplate.update("DELETE FROM harness_context_embedding_cache WHERE tenant_id = ?", tenantId);
    }

    @Test
    void shouldKeepMultipleParentSourcesWhenOneDocumentHasManyMatchingChunks() {
        String tenantId = "tenant-diversity-" + UUID.randomUUID();
        String userId = "operator";
        KnowledgeDocument dominant = documentRepository.saveAndFlush(new KnowledgeDocument(
                tenantId, userId, "主文档", "主文档内容", "INTERNAL", ""));
        KnowledgeDocument secondary = documentRepository.saveAndFlush(new KnowledgeDocument(
                tenantId, userId, "次文档", "次文档内容", "INTERNAL", ""));

        List<ContextChunk> chunks = new ArrayList<>();
        for (int index = 0; index < 6; index++) {
            chunks.add(new ContextChunk(tenantId, "DOCUMENT", dominant.getId(), index,
                    "主文档命中片段 " + index, "dominant-hash-" + index));
        }
        chunks.add(new ContextChunk(tenantId, "DOCUMENT", secondary.getId(), 0,
                "次文档命中片段", "secondary-hash"));
        chunkRepository.saveAllAndFlush(chunks);

        List<ContextEmbeddingUpdate> updates = new ArrayList<>();
        for (int index = 0; index < 6; index++) {
            updates.add(new ContextEmbeddingUpdate(chunks.get(index), vectorUpdate(1.0, 0.0)));
        }
        updates.add(new ContextEmbeddingUpdate(chunks.get(6), vectorUpdate(0.8, 0.6)));
        embeddingStore.save(updates);

        EmbeddingGateway gateway = mock(EmbeddingGateway.class);
        when(gateway.enabled()).thenReturn(true);
        when(gateway.embed(anyList())).thenReturn(List.of(vectorUpdate(1.0, 0.0)));
        ContextRetrievalProperties retrievalProperties = new ContextRetrievalProperties(
                2, 2, 0, 0.0, true, 60, 1.0, 0.7, 3, 1);
        EmbeddingProperties embeddingProperties = new EmbeddingProperties(true, "http://embedding", "key",
                "model", 1536, 8, 1_000, 100_000, 1, 0, 10_000);
        VectorContextRetriever retriever = new VectorContextRetriever(namedParameterJdbcTemplate,
                chunkRepository, parentWindowRepository, gateway, embeddingStore, embeddingProperties,
                retrievalProperties, new SensitiveDataSanitizer(), embeddingCache, null);

        var result = retriever.retrieve(tenantId, userId, "命中", 8_000);

        assertEquals(2, result.evidences().size());
        assertTrue(result.evidences().stream().anyMatch(item -> item.documentId().equals(dominant.getId())));
        assertTrue(result.evidences().stream().anyMatch(item -> item.documentId().equals(secondary.getId())));

        jdbcTemplate.update("DELETE FROM harness_context_chunks WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM harness_context_documents WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM harness_context_embedding_cache WHERE tenant_id = ?", tenantId);
    }

    private EmbeddingVector vectorUpdate(double first, double second) {
        List<Double> values = new ArrayList<>(java.util.Collections.nCopies(1536, 0.0));
        values.set(0, first);
        values.set(1, second);
        return new EmbeddingVector("integration-model", values);
    }

    private String vectorLiteral(List<Double> values) {
        return "[" + values.stream().map(String::valueOf).reduce((left, right) -> left + "," + right)
                .orElseThrow() + "]";
    }
}
