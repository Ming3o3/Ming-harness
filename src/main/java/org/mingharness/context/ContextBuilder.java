package org.mingharness.context;

import org.mingharness.config.ContextRetrievalProperties;
import org.mingharness.context.api.ContextEvidence;
import org.mingharness.context.api.ContextResult;
import org.mingharness.education.EducationKnowledgeSourceRepository;
import org.mingharness.education.EducationRetrievalFilter;
import org.mingharness.observability.HarnessMetrics;
import org.springframework.dao.DataAccessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 统一构建可引用上下文，先做权限过滤，再做轻量关键词召回和预算裁剪。 */
@Service
public class ContextBuilder {

    private final KnowledgeDocumentRepository documentRepository;
    private final MemoryEntryRepository memoryRepository;
    private final VectorContextRetriever vectorContextRetriever;
    private final HarnessMetrics metrics;
    private final ContextRetrievalProperties retrievalProperties;
    private final EducationKnowledgeSourceRepository educationSourceRepository;

    /** 兼容单元测试和本地调用；生产环境使用配置注入的构造器。 */
    public ContextBuilder(KnowledgeDocumentRepository documentRepository,
                          MemoryEntryRepository memoryRepository,
                          VectorContextRetriever vectorContextRetriever,
                          HarnessMetrics metrics) {
        this(documentRepository, memoryRepository, vectorContextRetriever, metrics,
                new ContextRetrievalProperties(20, 5, 1, 0.7), null);
    }

    @Autowired
    public ContextBuilder(KnowledgeDocumentRepository documentRepository,
                          MemoryEntryRepository memoryRepository,
                          VectorContextRetriever vectorContextRetriever,
                          HarnessMetrics metrics,
                          ContextRetrievalProperties retrievalProperties) {
        this(documentRepository, memoryRepository, vectorContextRetriever, metrics,
                retrievalProperties, null);
    }

    @Autowired
    public ContextBuilder(KnowledgeDocumentRepository documentRepository,
                          MemoryEntryRepository memoryRepository,
                          VectorContextRetriever vectorContextRetriever,
                          HarnessMetrics metrics,
                          ContextRetrievalProperties retrievalProperties,
                          EducationKnowledgeSourceRepository educationSourceRepository) {
        this.documentRepository = documentRepository;
        this.memoryRepository = memoryRepository;
        this.vectorContextRetriever = vectorContextRetriever;
        this.metrics = metrics;
        this.retrievalProperties = retrievalProperties;
        this.educationSourceRepository = educationSourceRepository;
    }

    public ContextResult build(String tenantId, String userId, String query, int maxChars) {
        return build(tenantId, userId, query, maxChars, null);
    }

    /** 使用教育硬约束构建上下文；null 表示旧的通用检索路径。 */
    public ContextResult build(String tenantId, String userId, String query, int maxChars,
                               EducationRetrievalFilter educationFilter) {
        if (query == null || query.isBlank() || maxChars < 1) {
            return new ContextResult("", List.of());
        }
        return metrics.recordContextRetrieval(() -> buildInternal(tenantId, userId, query, maxChars,
                educationFilter));
    }

    private ContextResult buildInternal(String tenantId, String userId, String query, int maxChars,
                                        EducationRetrievalFilter educationFilter) {
        ContextResult vectorResult = new ContextResult("", List.of());
        try {
            vectorResult = educationFilter == null
                    ? vectorContextRetriever.retrieve(tenantId, userId, query, maxChars)
                    : vectorContextRetriever.retrieve(tenantId, userId, query, maxChars, educationFilter);
        } catch (EmbeddingGatewayException | DataAccessException exception) {
            // embedding 服务或 pgvector 暂时不可用时保持旧的确定性关键词召回能力。
        }
        ContextResult keywordResult = buildKeyword(tenantId, userId, query, maxChars, educationFilter);
        if (vectorResult.isEmpty()) {
            if (!keywordResult.isEmpty()) metrics.contextFallback();
            return keywordResult;
        }
        if (keywordResult.isEmpty()) return vectorResult;
        ContextResult merged = merge(vectorResult, keywordResult, maxChars);
        metrics.contextKeywordSupplements(Math.max(0,
                merged.evidences().size() - vectorResult.evidences().size()));
        return merged;
    }

    private ContextResult buildKeyword(String tenantId, String userId, String query, int maxChars,
                                      EducationRetrievalFilter educationFilter) {
        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        String[] terms = normalizedQuery.split("\\s+|[，。！？、,:：;；]+");
        List<ScoredContext> candidates = new ArrayList<>();
        for (KnowledgeDocument document : documentRepository
                .findTop100ByTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc(tenantId)) {
            if (!document.isVisibleTo(userId)) {
                continue;
            }
            if (!matchesEducationFilter(tenantId, document, educationFilter)) {
                continue;
            }
            String searchable = (document.getTitle() + "\n" + document.getContent()).toLowerCase(Locale.ROOT);
            int score = score(searchable, terms);
            if (score > 0) {
                candidates.add(ScoredContext.document(document, score));
            }
        }

        Instant now = Instant.now();
        for (MemoryEntry memory : memoryRepository
                .findTop100ByTenantIdAndUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(tenantId, userId)) {
            if (!memory.isActive(now)) {
                continue;
            }
            String searchable = (memory.getMemoryType() + "\n" + memory.getContent()).toLowerCase(Locale.ROOT);
            int score = score(searchable, terms);
            if (score > 0) {
                candidates.add(ScoredContext.memory(memory, score));
            }
        }

        candidates.sort(Comparator.comparingInt(ScoredContext::score).reversed()
                .thenComparing(ScoredContext::createdAt, Comparator.reverseOrder()));

        List<ContextEvidence> evidences = new ArrayList<>();
        StringBuilder context = new StringBuilder();
        for (ScoredContext candidate : candidates) {
            String excerpt = excerpt(candidate.content(), normalizedQuery, terms, 800);
            String block = contextBlock(candidate.title(), excerpt);
            if (context.length() + block.length() > maxChars) {
                // 单个来源过大时跳过它，继续尝试更小的来源，避免一篇长文阻断相关记忆。
                continue;
            }
            context.append(block);
            evidences.add(new ContextEvidence(candidate.id(), candidate.title(),
                    candidate.citation(), excerpt));
        }
        return new ContextResult(context.toString(), List.copyOf(evidences));
    }

    private boolean matchesEducationFilter(String tenantId, KnowledgeDocument document,
                                           EducationRetrievalFilter educationFilter) {
        if (educationFilter == null || !educationFilter.active()) return true;
        if (educationSourceRepository == null) return false;
        return educationSourceRepository
                .findByTenantIdAndDocumentIdAndDeletedAtIsNull(tenantId, document.getId())
                .map(educationFilter::matches)
                .orElse(false);
    }

    /** 以父来源为单位融合语义召回和关键词召回，兼顾语义匹配与错误码/名称精确匹配。 */
    private ContextResult merge(ContextResult vectorResult, ContextResult keywordResult, int maxChars) {
        if (!retrievalProperties.rrfEnabled()) {
            return mergeLegacy(vectorResult, keywordResult, maxChars);
        }

        Map<String, Integer> vectorRanks = uniqueParentRanks(vectorResult);
        Map<String, Integer> keywordRanks = uniqueParentRanks(keywordResult);
        Map<String, Double> parentScores = new HashMap<>();
        vectorRanks.forEach((parent, rank) -> parentScores.merge(parent,
                retrievalProperties.vectorWeight() / (retrievalProperties.rrfK() + rank), Double::sum));
        keywordRanks.forEach((parent, rank) -> parentScores.merge(parent,
                retrievalProperties.keywordWeight() / (retrievalProperties.rrfK() + rank), Double::sum));

        Set<String> vectorParents = vectorRanks.keySet();
        List<RankedEvidence> candidates = new ArrayList<>();
        int order = 0;
        for (ContextEvidence evidence : vectorResult.evidences()) {
            String parent = parentKey(evidence.citation());
            candidates.add(new RankedEvidence(evidence, parentScores.getOrDefault(parent, 0.0),
                    vectorRanks.getOrDefault(parent, Integer.MAX_VALUE), 0, order++));
        }
        for (ContextEvidence evidence : keywordResult.evidences()) {
            String parent = parentKey(evidence.citation());
            // 父窗口已经提供了该来源的上下文时，不再追加整篇关键词结果；关键词排名仍会提升该父来源。
            if (vectorParents.contains(parent)) continue;
            candidates.add(new RankedEvidence(evidence, parentScores.getOrDefault(parent, 0.0),
                    Integer.MAX_VALUE, 1, order++));
        }
        candidates.sort(Comparator.comparingDouble(RankedEvidence::score).reversed()
                .thenComparingInt(RankedEvidence::vectorRank)
                .thenComparingInt(RankedEvidence::sourceRank)
                .thenComparingInt(RankedEvidence::order));

        List<ContextEvidence> evidences = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        Set<String> evidenceKeys = new HashSet<>();
        for (RankedEvidence candidate : candidates) {
            String source = evidenceKey(candidate.evidence().citation());
            if (!evidenceKeys.add(source)) continue;
            ContextEvidence evidence = candidate.evidence();
            String block = contextBlock(evidence.title(), evidence.excerpt());
            if (text.length() + block.length() > maxChars) continue;
            text.append(block);
            evidences.add(evidence);
        }
        return new ContextResult(text.toString(), List.copyOf(evidences));
    }

    private ContextResult mergeLegacy(ContextResult vectorResult, ContextResult keywordResult, int maxChars) {
        List<ContextEvidence> evidences = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        Set<String> evidenceKeys = new HashSet<>();
        Set<String> vectorParents = vectorResult.evidences().stream()
                .map(evidence -> parentKey(evidence.citation()))
                .collect(java.util.stream.Collectors.toSet());
        appendResult(vectorResult, maxChars, text, evidences, evidenceKeys, Set.of());
        appendResult(keywordResult, maxChars, text, evidences, evidenceKeys, vectorParents);
        return new ContextResult(text.toString(), List.copyOf(evidences));
    }

    private Map<String, Integer> uniqueParentRanks(ContextResult result) {
        Map<String, Integer> ranks = new java.util.LinkedHashMap<>();
        if (result == null || result.evidences() == null) return ranks;
        int rank = 1;
        for (ContextEvidence evidence : result.evidences()) {
            if (evidence == null) continue;
            String parent = parentKey(evidence.citation());
            if (ranks.putIfAbsent(parent, rank) == null) rank++;
        }
        return ranks;
    }

    private void appendResult(ContextResult result, int maxChars, StringBuilder text,
                              List<ContextEvidence> evidences,
                              java.util.Set<String> evidenceKeys,
                              java.util.Set<String> protectedParents) {
        for (ContextEvidence evidence : result.evidences()) {
            if (protectedParents.contains(parentKey(evidence.citation()))) continue;
            String source = evidenceKey(evidence.citation());
            if (!evidenceKeys.add(source)) continue;
            String block = contextBlock(evidence.title(), evidence.excerpt());
            if (text.length() + block.length() > maxChars) continue;
            text.append(block);
            evidences.add(evidence);
        }
    }

    /**
     * 给模型的上下文只展示用户可读的来源名称；内部 citation 仍保留在 ContextEvidence 中，
     * 供审计和诊断使用，避免把 document:<id>#window:<n>#chunk:<m> 泄漏到最终回答。
     */
    private String contextBlock(String title, String excerpt) {
        String readableTitle = title == null || title.isBlank() ? "未命名来源" : title.trim();
        return "[来源：" + readableTitle + "]\n"
                + (excerpt == null ? "" : excerpt) + "\n";
    }

    /** 同一父窗口内的多个子块只保留一份证据，但同一父文档的不同窗口可以并列返回。 */
    private String evidenceKey(String citation) {
        if (citation == null) return "";
        int chunk = citation.indexOf("#chunk:");
        return chunk < 0 ? citation : citation.substring(0, chunk);
    }

    /** 关键词结果是整篇父文档，需按父文档 ID 与向量窗口结果比较，避免重复补充。 */
    private String parentKey(String citation) {
        if (citation == null) return "";
        int window = citation.indexOf("#window:");
        int chunk = citation.indexOf("#chunk:");
        int end = citation.length();
        if (window >= 0) end = Math.min(end, window);
        if (chunk >= 0) end = Math.min(end, chunk);
        return citation.substring(0, end);
    }

    private int score(String searchable, String[] terms) {
        int score = 0;
        for (String term : terms) {
            if (!term.isBlank() && searchable.contains(term)) {
                score++;
            }
        }
        return score;
    }

    private String excerpt(String content, String query, String[] terms, int maxLength) {
        String normalized = content == null ? "" : content;
        String searchable = normalized.toLowerCase(Locale.ROOT);
        int position = searchable.indexOf(query);
        if (position < 0) {
            for (String term : terms) {
                if (!term.isBlank()) {
                    position = searchable.indexOf(term);
                    if (position >= 0) {
                        break;
                    }
                }
            }
        }
        if (position < 0) {
            return normalized.substring(0, Math.min(normalized.length(), maxLength));
        }
        int start = Math.max(0, position - maxLength / 3);
        int end = Math.min(normalized.length(), start + maxLength);
        return normalized.substring(start, end);
    }

    private record ScoredContext(String id, String title, String citation,
                                 String content, int score, Instant createdAt) {

        private static ScoredContext document(KnowledgeDocument document, int score) {
            return new ScoredContext(document.getId(), document.getTitle(),
                    "document:" + document.getId(), document.getContent(), score, document.getCreatedAt());
        }

        private static ScoredContext memory(MemoryEntry memory, int score) {
            return new ScoredContext(memory.getId(),
                    "记忆 · " + memory.getMemoryType(), "memory:" + memory.getId(),
                    memory.getContent(), score, memory.getCreatedAt());
        }
    }

    private record RankedEvidence(ContextEvidence evidence, double score,
                                  int vectorRank, int sourceRank, int order) {
    }
}
