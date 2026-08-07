package org.mingharness.context;

import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.config.ContextRetrievalProperties;
import org.mingharness.config.EmbeddingProperties;
import org.mingharness.context.api.ContextEvidence;
import org.mingharness.context.api.ContextResult;
import org.mingharness.observability.HarnessMetrics;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 使用 pgvector 做权限感知的子块召回，并为命中子块补回相邻上下文。
 * 召回失败时由上层 ContextBuilder 降级到现有关键词实现。
 */
@Service
public class VectorContextRetriever {

    private static final String SEARCH_SQL = """
            SELECT c.id AS chunk_id,
                   c.parent_type,
                   c.parent_id,
                   c.chunk_index,
                   c.content,
                   c.parent_window_id,
                   w.window_index AS parent_window_index,
                   CASE WHEN c.parent_type = 'DOCUMENT' THEN d.title
                        ELSE '记忆 · ' || m.memory_type END AS title,
                   1 - (c.embedding <=> CAST(:queryVector AS vector)) AS similarity
              FROM harness_context_chunks c
              LEFT JOIN harness_context_documents d
                ON c.parent_type = 'DOCUMENT' AND d.id = c.parent_id
              LEFT JOIN harness_context_memories m
                ON c.parent_type = 'MEMORY' AND m.id = c.parent_id
              LEFT JOIN harness_context_parent_windows w
                ON c.parent_window_id = w.id
               AND w.deleted_at IS NULL
               AND w.tenant_id = c.tenant_id
               AND w.parent_type = c.parent_type
               AND w.parent_id = c.parent_id
             WHERE c.tenant_id = :tenantId
               AND c.deleted_at IS NULL
               AND c.embedding IS NOT NULL
               AND (
                    (c.parent_type = 'DOCUMENT'
                     AND d.deleted_at IS NULL
                     AND (d.allowed_users IS NULL OR d.allowed_users = ''
                          OR :userId = ANY(string_to_array(d.allowed_users, ','))))
                    OR
                    (c.parent_type = 'MEMORY'
                     AND m.deleted_at IS NULL
                     AND m.user_id = :userId
                     AND (m.expires_at IS NULL OR m.expires_at > CURRENT_TIMESTAMP))
               )
               AND 1 - (c.embedding <=> CAST(:queryVector AS vector)) >= :minSimilarity
             ORDER BY c.embedding <=> CAST(:queryVector AS vector)
             LIMIT :candidateLimit
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ContextChunkRepository chunkRepository;
    private final ContextParentWindowRepository parentWindowRepository;
    private final EmbeddingGateway embeddingGateway;
    private final ContextEmbeddingStore embeddingStore;
    private final EmbeddingProperties embeddingProperties;
    private final ContextRetrievalProperties retrievalProperties;
    private final SensitiveDataSanitizer sanitizer;
    private final HarnessMetrics metrics;

    public VectorContextRetriever(NamedParameterJdbcTemplate jdbcTemplate,
                                  ContextChunkRepository chunkRepository,
                                  EmbeddingGateway embeddingGateway,
                                  ContextEmbeddingStore embeddingStore,
                                  EmbeddingProperties embeddingProperties,
                                  ContextRetrievalProperties retrievalProperties,
                                  SensitiveDataSanitizer sanitizer) {
        this(jdbcTemplate, chunkRepository, null, embeddingGateway, embeddingStore, embeddingProperties,
                retrievalProperties, sanitizer, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public VectorContextRetriever(NamedParameterJdbcTemplate jdbcTemplate,
                                  ContextChunkRepository chunkRepository,
                                  ContextParentWindowRepository parentWindowRepository,
                                  EmbeddingGateway embeddingGateway,
                                  ContextEmbeddingStore embeddingStore,
                                  EmbeddingProperties embeddingProperties,
                                  ContextRetrievalProperties retrievalProperties,
                                  SensitiveDataSanitizer sanitizer,
                                  HarnessMetrics metrics) {
        this.jdbcTemplate = jdbcTemplate;
        this.chunkRepository = chunkRepository;
        this.parentWindowRepository = parentWindowRepository;
        this.embeddingGateway = embeddingGateway;
        this.embeddingStore = embeddingStore;
        this.embeddingProperties = embeddingProperties;
        this.retrievalProperties = retrievalProperties;
        this.sanitizer = sanitizer;
        this.metrics = metrics;
    }

    public ContextResult retrieve(String tenantId, String userId, String query, int maxChars) {
        if (query == null || query.isBlank() || maxChars < 1
                || !embeddingGateway.enabled() || !embeddingStore.supported()) {
            return new ContextResult("", List.of());
        }
        if (metrics != null) metrics.contextVectorQuery();
        String boundedQuery = boundQuery(query);
        List<EmbeddingVector> queryVectors = embeddingGateway.embed(List.of(boundedQuery));
        if (queryVectors.size() != 1 || queryVectors.get(0).dimension() != embeddingProperties.dimension()) {
            throw new EmbeddingGatewayException(false, "查询 embedding 维度不匹配");
        }
        EmbeddingVector queryVector = queryVectors.get(0);
        Map<String, Object> parameters = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("userId", userId)
                .addValue("queryVector", vectorLiteral(queryVector))
                .addValue("minSimilarity", retrievalProperties.minSimilarity())
                .addValue("candidateLimit", retrievalProperties.expandedCandidateLimit())
                .getValues();
        List<VectorHit> hits = jdbcTemplate.query(SEARCH_SQL, new MapSqlParameterSource(parameters),
                (row, rowNumber) -> new VectorHit(
                        row.getString("chunk_id"), row.getString("parent_type"),
                        row.getString("parent_id"), row.getInt("chunk_index"),
                        row.getString("content"), row.getString("parent_window_id"),
                        row.getObject("parent_window_index", Integer.class), row.getString("title"),
                        row.getDouble("similarity")));
        if (metrics != null) metrics.contextVectorHits(hits.size());
        return buildResult(hits, tenantId, maxChars);
    }

    private ContextResult buildResult(List<VectorHit> hits, String tenantId, int maxChars) {
        Map<String, ParentHit> parents = new LinkedHashMap<>();
        for (VectorHit hit : hits) {
            String key = hit.parentType() + ":" + hit.parentId()
                    + (hit.parentWindowId() == null ? "" : ":window:" + hit.parentWindowId());
            parents.computeIfAbsent(key, ignored -> new ParentHit(hit)).add(hit);
        }
        List<ParentHit> ordered = parents.values().stream()
                .sorted(Comparator.comparingDouble(ParentHit::bestSimilarity).reversed()
                        .thenComparing(Comparator.comparingInt(ParentHit::hitCount).reversed()))
                .limit(retrievalProperties.maxParents())
                .toList();
        StringBuilder context = new StringBuilder();
        List<ContextEvidence> evidences = new ArrayList<>();
        for (ParentHit parent : ordered) {
            VectorHit best = parent.bestHit();
            ContextParentWindow parentWindow = findParentWindow(tenantId, best);
            List<ContextChunk> chunks = parentWindow == null
                    ? chunkRepository
                    .findByTenantIdAndParentTypeAndParentIdAndDeletedAtIsNullOrderByChunkIndexAsc(
                            tenantId, best.parentType(), best.parentId())
                    : List.of();
            String excerpt = parentWindow == null
                    ? expandedExcerpt(chunks, best.chunkIndex())
                    : boundedParentWindow(parentWindow.getContent(), maxChars);
            String displayId = "DOCUMENT".equals(best.parentType())
                    ? best.parentId() : "memory:" + best.parentId();
            String block = "[" + displayId + "#chunk:" + best.chunkIndex() + "] "
                    + best.title() + "\n" + excerpt + "\n";
            if (context.length() + block.length() > maxChars) continue;
            context.append(block);
            String citationPrefix = "DOCUMENT".equals(best.parentType()) ? "document:" : "memory:";
            String windowCitation = best.parentWindowId() == null || best.parentWindowIndex() == null
                    ? "" : "#window:" + best.parentWindowIndex();
            evidences.add(new ContextEvidence(best.parentId(), best.title(),
                    citationPrefix + best.parentId() + windowCitation + "#chunk:" + best.chunkIndex(), excerpt));
        }
        return new ContextResult(context.toString(), List.copyOf(evidences));
    }

    private ContextParentWindow findParentWindow(String tenantId, VectorHit hit) {
        if (parentWindowRepository == null || hit.parentWindowId() == null) return null;
        return parentWindowRepository.findByIdAndTenantIdAndParentTypeAndParentIdAndDeletedAtIsNull(
                hit.parentWindowId(), tenantId, hit.parentType(), hit.parentId());
    }

    private String boundedParentWindow(String content, int maxChars) {
        String sanitized = sanitizer.sanitize(content == null ? "" : content);
        int limit = Math.max(1, maxChars - 256);
        return sanitized.length() <= limit ? sanitized : sanitized.substring(0, limit).trim() + "...";
    }

    private String expandedExcerpt(List<ContextChunk> chunks, int hitIndex) {
        if (chunks == null || chunks.isEmpty()) return "";
        int start = Math.max(0, hitIndex - retrievalProperties.neighborRadius());
        int end = Math.min(chunks.size() - 1, hitIndex + retrievalProperties.neighborRadius());
        StringBuilder result = new StringBuilder();
        for (ContextChunk chunk : chunks) {
            if (chunk.getChunkIndex() < start || chunk.getChunkIndex() > end) continue;
            if (!result.isEmpty()) result.append("\n\n");
            result.append(chunk.getContent());
        }
        return sanitizer.sanitize(result.toString());
    }

    private String boundQuery(String query) {
        String normalized = sanitizer.sanitize(query).trim();
        return normalized.length() <= embeddingProperties.maxInputChars()
                ? normalized : normalized.substring(0, embeddingProperties.maxInputChars());
    }

    private String vectorLiteral(EmbeddingVector vector) {
        return "[" + vector.values().stream().map(String::valueOf).reduce((left, right) -> left + "," + right)
                .orElseThrow(() -> new EmbeddingGatewayException(false, "查询 embedding 不能为空")) + "]";
    }

    static record VectorHit(String chunkId, String parentType, String parentId, int chunkIndex,
                            String content, String parentWindowId, Integer parentWindowIndex,
                            String title, double similarity) {

        VectorHit(String chunkId, String parentType, String parentId, int chunkIndex,
                  String content, String title, double similarity) {
            this(chunkId, parentType, parentId, chunkIndex, content, null, null, title, similarity);
        }
    }

    private static final class ParentHit {
        private final List<VectorHit> hits = new ArrayList<>();

        private ParentHit(VectorHit first) {
            hits.add(first);
        }

        private void add(VectorHit hit) {
            if (!hits.contains(hit)) hits.add(hit);
        }

        private VectorHit bestHit() {
            return hits.stream().max(Comparator.comparingDouble(VectorHit::similarity)).orElseThrow();
        }

        private double bestSimilarity() {
            return bestHit().similarity();
        }

        private int hitCount() {
            return hits.size();
        }
    }
}
