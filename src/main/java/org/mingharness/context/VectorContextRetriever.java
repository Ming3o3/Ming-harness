package org.mingharness.context;

import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.config.ContextRetrievalProperties;
import org.mingharness.config.EmbeddingProperties;
import org.mingharness.context.api.ContextEvidence;
import org.mingharness.context.api.ContextResult;
import org.mingharness.education.EducationRetrievalFilter;
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
import java.util.Optional;

/**
 * 使用 pgvector 做权限感知的子块召回，并为命中子块补回相邻上下文。
 * 召回失败时由上层 ContextBuilder 降级到现有关键词实现。
 */
@Service
public class VectorContextRetriever {

    private static final String SEARCH_SQL = """
            WITH vector_candidates AS (
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
                   AND c.embedding_model = :embeddingSignature
                   AND (
                        :educationFilterEnabled = FALSE
                        OR (c.parent_type = 'DOCUMENT' AND EXISTS (
                            SELECT 1
                              FROM harness_education_sources es
                             WHERE es.tenant_id = c.tenant_id
                               AND es.document_id = c.parent_id
                               AND es.deleted_at IS NULL
                               AND (:educationSubject IS NULL OR es.subject = :educationSubject)
                               AND (:educationGradeLevel IS NULL OR es.grade_level = :educationGradeLevel)
                               AND (:educationCurriculumVersion IS NULL
                                    OR es.curriculum_version = :educationCurriculumVersion)
                               AND (:educationConceptKey IS NULL
                                    OR LOWER(:educationConceptKey) = ANY(
                                        string_to_array(LOWER(es.concept_tags), ','))
                                    OR (:educationConceptKeys IS NOT NULL AND EXISTS (
                                        SELECT 1
                                          FROM unnest(string_to_array(LOWER(:educationConceptKeys), ',')) requested_key
                                         WHERE requested_key = ANY(
                                            string_to_array(LOWER(es.concept_tags), ',')))))
                               AND (:educationMinDifficulty IS NULL
                                    OR es.difficulty_level >= :educationMinDifficulty)
                               AND (:educationMaxDifficulty IS NULL
                                    OR es.difficulty_level <= :educationMaxDifficulty)
                        ))
                   )
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
                 ORDER BY c.embedding <=> CAST(:queryVector AS vector), c.id
                 LIMIT :candidatePoolLimit
            ), ranked_candidates AS (
                SELECT vector_candidates.*,
                       ROW_NUMBER() OVER (
                           PARTITION BY parent_type, parent_id
                           ORDER BY similarity DESC, chunk_id
                       ) AS parent_rank
                  FROM vector_candidates
            )
            SELECT chunk_id,
                   parent_type,
                   parent_id,
                   chunk_index,
                   content,
                   parent_window_id,
                   parent_window_index,
                   title,
                   similarity
              FROM ranked_candidates
             WHERE parent_rank <= :maxCandidatesPerParent
             ORDER BY similarity DESC, chunk_id
             LIMIT :candidateLimit
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ContextChunkRepository chunkRepository;
    private final ContextParentWindowRepository parentWindowRepository;
    private final EmbeddingGateway embeddingGateway;
    private final ContextEmbeddingStore embeddingStore;
    private final ContextEmbeddingCache embeddingCache;
    private final EmbeddingProperties embeddingProperties;
    private final EmbeddingProviderConfigService configService;
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
                retrievalProperties, sanitizer, new NoopContextEmbeddingCache(), null);
    }

    public VectorContextRetriever(NamedParameterJdbcTemplate jdbcTemplate,
                                  ContextChunkRepository chunkRepository,
                                  ContextParentWindowRepository parentWindowRepository,
                                  EmbeddingGateway embeddingGateway,
                                  ContextEmbeddingStore embeddingStore,
                                  EmbeddingProperties embeddingProperties,
                                  ContextRetrievalProperties retrievalProperties,
                                  SensitiveDataSanitizer sanitizer,
                                  HarnessMetrics metrics) {
        this(jdbcTemplate, chunkRepository, parentWindowRepository, embeddingGateway, embeddingStore,
                embeddingProperties, retrievalProperties, sanitizer, new NoopContextEmbeddingCache(), metrics);
    }

    public VectorContextRetriever(NamedParameterJdbcTemplate jdbcTemplate,
                                  ContextChunkRepository chunkRepository,
                                  ContextParentWindowRepository parentWindowRepository,
                                  EmbeddingGateway embeddingGateway,
                                  ContextEmbeddingStore embeddingStore,
                                  EmbeddingProperties embeddingProperties,
                                  ContextRetrievalProperties retrievalProperties,
                                  SensitiveDataSanitizer sanitizer,
                                  ContextEmbeddingCache embeddingCache,
                                  HarnessMetrics metrics) {
        this(jdbcTemplate, chunkRepository, parentWindowRepository, embeddingGateway, embeddingStore,
                embeddingProperties, retrievalProperties, sanitizer, embeddingCache, metrics, null);
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
                                  ContextEmbeddingCache embeddingCache,
                                  HarnessMetrics metrics,
                                  EmbeddingProviderConfigService configService) {
        this.jdbcTemplate = jdbcTemplate;
        this.chunkRepository = chunkRepository;
        this.parentWindowRepository = parentWindowRepository;
        this.embeddingGateway = embeddingGateway;
        this.embeddingStore = embeddingStore;
        this.embeddingCache = embeddingCache;
        this.embeddingProperties = embeddingProperties;
        this.configService = configService;
        this.retrievalProperties = retrievalProperties;
        this.sanitizer = sanitizer;
        this.metrics = metrics;
    }

    public ContextResult retrieve(String tenantId, String userId, String query, int maxChars) {
        return retrieve(tenantId, userId, query, maxChars, null);
    }

    /** 向量检索的教育约束路径；普通调用保持原有 SQL 参数和行为。 */
    public ContextResult retrieve(String tenantId, String userId, String query, int maxChars,
                                  EducationRetrievalFilter educationFilter) {
        EmbeddingProviderConfigService.ResolvedEmbeddingConfig config = config(tenantId);
        if (query == null || query.isBlank() || maxChars < 1
                || !enabled(tenantId) || !embeddingStore.supported()) {
            return new ContextResult("", List.of());
        }
        if (metrics != null) metrics.contextVectorQuery();
        String boundedQuery = boundQuery(query, config);
        EmbeddingVector queryVector = queryEmbedding(tenantId, boundedQuery, config);
        Map<String, Object> parameters = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("userId", userId)
                .addValue("queryVector", vectorLiteral(queryVector))
                .addValue("embeddingSignature", config.signature())
                .addValue("minSimilarity", retrievalProperties.minSimilarity())
                .addValue("candidatePoolLimit", retrievalProperties.candidatePoolLimit())
                .addValue("maxCandidatesPerParent", retrievalProperties.maxCandidatesPerParent())
                .addValue("candidateLimit", retrievalProperties.expandedCandidateLimit())
                .addValue("educationFilterEnabled", educationFilter != null && educationFilter.active())
                .addValue("educationSubject", educationFilter == null ? null : educationFilter.subjectOrNull())
                .addValue("educationGradeLevel", educationFilter == null ? null : educationFilter.gradeLevelOrNull())
                .addValue("educationCurriculumVersion", educationFilter == null
                        ? null : educationFilter.curriculumVersionOrNull())
                .addValue("educationConceptKey", educationFilter == null ? null : educationFilter.conceptKeyOrNull())
                .addValue("educationConceptKeys", educationFilter == null
                        || educationFilter.retrievalConceptKeys().size() <= 1
                        ? null : String.join(",", educationFilter.retrievalConceptKeys()))
                .addValue("educationMinDifficulty", educationFilter == null
                        ? null : educationFilter.minDifficultyOrNull())
                .addValue("educationMaxDifficulty", educationFilter == null
                        ? null : educationFilter.maxDifficultyOrNull())
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
                    citationPrefix + best.parentId() + windowCitation + "#chunk:" + best.chunkIndex(), excerpt,
                    best.similarity(), "", List.of()));
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

    private String boundQuery(String query,
                              EmbeddingProviderConfigService.ResolvedEmbeddingConfig config) {
        String normalized = sanitizer.sanitize(query).trim();
        if (normalized.length() > config.maxInputChars()) {
            normalized = normalized.substring(0, config.maxInputChars());
        }
        return EmbeddingTokenEstimator.truncate(normalized, config.maxInputTokens()).trim();
    }

    private EmbeddingVector queryEmbedding(String tenantId, String boundedQuery,
                                           EmbeddingProviderConfigService.ResolvedEmbeddingConfig config) {
        String contentHash = EmbeddingContentHasher.sha256(boundedQuery);
        try {
            Optional<EmbeddingVector> cached = embeddingCache.find(tenantId, contentHash,
                    config.model(), config.modelVersion(), config.dimension());
            if (cached.isPresent() && cached.get().dimension() == config.dimension()) {
                if (metrics != null) metrics.contextQueryEmbeddingCacheHit();
                return cached.get();
            }
        } catch (RuntimeException ignored) {
            // 缓存只是加速层，读取失败时继续调用 embedding 供应商。
        }
        if (metrics != null) metrics.contextQueryEmbeddingCacheMiss();

        List<EmbeddingVector> queryVectors = configService == null
                ? embeddingGateway.embed(List.of(boundedQuery))
                : embeddingGateway.embed(tenantId, List.of(boundedQuery));
        if (queryVectors.size() != 1 || queryVectors.get(0).dimension() != config.dimension()) {
            throw new EmbeddingGatewayException(false, "查询 embedding 维度不匹配");
        }
        EmbeddingVector queryVector = queryVectors.get(0);
        try {
            embeddingCache.save(tenantId, contentHash, config.model(), config.modelVersion(), queryVector);
        } catch (RuntimeException ignored) {
            // 缓存写入失败不能影响本次检索结果。
        }
        return queryVector;
    }

    private boolean enabled(String tenantId) {
        return configService == null ? embeddingGateway.enabled() : embeddingGateway.enabled(tenantId);
    }

    private EmbeddingProviderConfigService.ResolvedEmbeddingConfig config(String tenantId) {
        return configService == null
                ? new EmbeddingProviderConfigService.ResolvedEmbeddingConfig(embeddingProperties.enabled(),
                embeddingProperties.baseUrl(), embeddingProperties.apiKey(), embeddingProperties.model(),
                embeddingProperties.modelVersion(), embeddingProperties.dimension(), embeddingProperties.batchSize(),
                embeddingProperties.maxInputChars(), embeddingProperties.maxInputTokens(), embeddingProperties.maxResponseChars(),
                embeddingProperties.maxAttempts(), embeddingProperties.retryBackoffMs(), embeddingProperties.timeoutMs(),
                "environment", null)
                : configService.resolve(tenantId);
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
