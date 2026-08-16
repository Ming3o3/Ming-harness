package org.mingharness.context;

import org.mingharness.config.ContextRetrievalProperties;
import org.mingharness.context.api.ContextEvidence;
import org.mingharness.context.api.ContextResult;
import org.mingharness.context.api.EducationRankingBreakdown;
import org.mingharness.context.api.EducationRankingWeights;
import org.mingharness.education.EducationKnowledgeSourceRepository;
import org.mingharness.education.EducationKnowledgeSource;
import org.mingharness.education.EducationDependencyGraph;
import org.mingharness.education.EducationDependencyPath;
import org.mingharness.education.EducationKnowledgeGraphService;
import org.mingharness.education.EducationRetrievalFilter;
import org.mingharness.education.EducationRetrievalStrategy;
import org.mingharness.observability.HarnessMetrics;
import org.springframework.dao.DataAccessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
    private final EducationKnowledgeGraphService knowledgeGraphService;

    /** 兼容单元测试和本地调用；生产环境使用配置注入的构造器。 */
    public ContextBuilder(KnowledgeDocumentRepository documentRepository,
                          MemoryEntryRepository memoryRepository,
                          VectorContextRetriever vectorContextRetriever,
                          HarnessMetrics metrics) {
        this(documentRepository, memoryRepository, vectorContextRetriever, metrics,
                new ContextRetrievalProperties(20, 5, 1, 0.7), null);
    }

    public ContextBuilder(KnowledgeDocumentRepository documentRepository,
                          MemoryEntryRepository memoryRepository,
                          VectorContextRetriever vectorContextRetriever,
                          HarnessMetrics metrics,
                          ContextRetrievalProperties retrievalProperties) {
        this(documentRepository, memoryRepository, vectorContextRetriever, metrics,
                retrievalProperties, null);
    }

    public ContextBuilder(KnowledgeDocumentRepository documentRepository,
                          MemoryEntryRepository memoryRepository,
                          VectorContextRetriever vectorContextRetriever,
                          HarnessMetrics metrics,
                          ContextRetrievalProperties retrievalProperties,
                          EducationKnowledgeSourceRepository educationSourceRepository) {
        this(documentRepository, memoryRepository, vectorContextRetriever, metrics, retrievalProperties,
                educationSourceRepository, null);
    }

    @Autowired
    public ContextBuilder(KnowledgeDocumentRepository documentRepository,
                          MemoryEntryRepository memoryRepository,
                          VectorContextRetriever vectorContextRetriever,
                          HarnessMetrics metrics,
                          ContextRetrievalProperties retrievalProperties,
                          EducationKnowledgeSourceRepository educationSourceRepository,
                          EducationKnowledgeGraphService knowledgeGraphService) {
        this.documentRepository = documentRepository;
        this.memoryRepository = memoryRepository;
        this.vectorContextRetriever = vectorContextRetriever;
        this.metrics = metrics;
        this.retrievalProperties = retrievalProperties;
        this.educationSourceRepository = educationSourceRepository;
        this.knowledgeGraphService = knowledgeGraphService;
    }

    public ContextResult build(String tenantId, String userId, String query, int maxChars) {
        return build(tenantId, userId, query, maxChars, null);
    }

    /** 使用教育硬约束构建上下文；null 表示旧的通用检索路径。 */
    public ContextResult build(String tenantId, String userId, String query, int maxChars,
                               EducationRetrievalFilter educationFilter) {
        return build(tenantId, userId, query, maxChars, educationFilter,
                EducationRetrievalStrategy.FULL);
    }

    /** 使用冻结的教育检索策略构建上下文；普通检索保持 FULL 兼容行为。 */
    public ContextResult build(String tenantId, String userId, String query, int maxChars,
                               EducationRetrievalFilter educationFilter,
                               EducationRetrievalStrategy strategy) {
        return build(tenantId, userId, query, maxChars, educationFilter, strategy, null);
    }

    /** 使用冻结的校准权重构建上下文；null 表示按策略使用默认状态权重。 */
    public ContextResult build(String tenantId, String userId, String query, int maxChars,
                               EducationRetrievalFilter educationFilter,
                               EducationRetrievalStrategy strategy,
                               EducationRankingWeights calibratedWeights) {
        if (query == null || query.isBlank() || maxChars < 1) {
            return new ContextResult("", List.of());
        }
        EducationRetrievalStrategy effectiveStrategy = strategy == null
                ? EducationRetrievalStrategy.FULL : strategy;
        return metrics.recordContextRetrieval(() -> buildInternal(tenantId, userId, query, maxChars,
                educationFilter, effectiveStrategy, calibratedWeights));
    }

    private ContextResult buildInternal(String tenantId, String userId, String query, int maxChars,
                                        EducationRetrievalFilter educationFilter,
                                        EducationRetrievalStrategy strategy,
                                        EducationRankingWeights calibratedWeights) {
        EducationRetrievalFilter effectiveEducationFilter = educationFilter;
        EducationDependencyGraph dependencyGraph = EducationDependencyGraph.empty(
                educationFilter == null ? null : educationFilter.conceptKeyOrNull());
        if (educationFilter != null && educationFilter.active()) {
            if (strategy.usesDependencyGraph()) {
                dependencyGraph = educationFilter.dependencyGraphOrNull() != null
                        ? educationFilter.dependencyGraphOrNull()
                        : knowledgeGraphService == null
                        ? dependencyGraph
                        : knowledgeGraphService.resolve(tenantId, educationFilter);
            }
            if (strategy.usesDependencyGraph() && dependencyGraph != null
                    && !dependencyGraph.prerequisites().isEmpty()) {
                effectiveEducationFilter = educationFilter.withDependencyGraph(dependencyGraph);
            }
        }
        ContextResult vectorResult = new ContextResult("", List.of());
        // 保持向量召回 API 的旧过滤器契约；图扩展首先作用于关键词候选和统一选择阶段。
        // 生产向量实现可通过冻结快照自行扩展候选，旧的检索适配器仍能无感兼容。
        EducationRetrievalFilter vectorFilter = educationFilter;
        if (strategy.usesVector()) {
            try {
                vectorResult = vectorFilter == null
                        ? vectorContextRetriever.retrieve(tenantId, userId, query, maxChars)
                        : vectorContextRetriever.retrieve(tenantId, userId, query, maxChars, vectorFilter);
                if (vectorResult == null) vectorResult = new ContextResult("", List.of());
            } catch (EmbeddingGatewayException | DataAccessException exception) {
                // embedding 服务或 pgvector 暂时不可用时保持关键词路径；VECTOR_ONLY 会明确返回空。
            }
        }
        ContextResult keywordResult = strategy.usesKeyword()
                ? buildKeyword(tenantId, userId, query, maxChars, effectiveEducationFilter)
                : new ContextResult("", List.of());
        if (effectiveEducationFilter != null && effectiveEducationFilter.active()) {
            // VECTOR_ONLY / KEYWORD_ONLY 是召回基线：只保留 SQL/元数据硬过滤，不叠加
            // 教育软重排，避免基线被完整方法的目标匹配和难度策略污染。
            if (strategy == EducationRetrievalStrategy.VECTOR_ONLY) return vectorResult;
            if (strategy == EducationRetrievalStrategy.KEYWORD_ONLY) return keywordResult;
            ContextResult merged = vectorResult.isEmpty()
                    ? keywordResult
                    : keywordResult.isEmpty() ? vectorResult : merge(vectorResult, keywordResult, maxChars);
            ContextResult selected = selectEducationEvidence(merged, tenantId, effectiveEducationFilter,
                    dependencyGraph, maxChars, strategy, calibratedWeights);
            if (vectorResult.isEmpty() && !keywordResult.isEmpty()) metrics.contextFallback();
            if (!vectorResult.isEmpty() && !keywordResult.isEmpty()) {
                metrics.contextKeywordSupplements(Math.max(0,
                        selected.evidences().size() - vectorResult.evidences().size()));
            }
            return selected;
        }
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
        for (KnowledgeDocument document : keywordDocuments(tenantId, educationFilter)) {
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

        // 学习者掌握度已经冻结在 EducationRunConfiguration；课程知识检索不能让任意
        // 私人记忆绕过学科、年级、版本和知识点的硬过滤。
        if (educationFilter == null || !educationFilter.active()) {
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
        }

        candidates.sort(Comparator.comparingDouble(ScoredContext::rankingScore).reversed()
                .thenComparing(Comparator.comparingInt(ScoredContext::score).reversed())
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
                    candidate.citation(), excerpt, keywordRelevance(candidate.score(), terms.length),
                    "", List.of()));
        }
        return new ContextResult(context.toString(), List.copyOf(evidences));
    }

    /**
     * 教育关键词降级路径必须先应用课程硬约束，再考虑文档发布时间。
     *
     * <p>通用检索保留最近 100 篇的成本上限；教育检索则先从受约束知识源提取文档 ID，
     * 让课程资料即使不在全库最新 100 篇内，也仍可作为本轮教学依据。</p>
     */
    private List<KnowledgeDocument> keywordDocuments(String tenantId,
                                                     EducationRetrievalFilter educationFilter) {
        if (educationFilter == null || !educationFilter.active()) {
            return documentRepository.findTop100ByTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc(tenantId);
        }
        if (educationSourceRepository == null) return List.of();
        List<String> sourceDocumentIds = educationSourceRepository
                .findByTenantIdAndDeletedAtIsNullOrderByUpdatedAtDesc(tenantId).stream()
                .filter(educationFilter::matchesForRetrieval)
                .map(EducationKnowledgeSource::getDocumentId)
                .filter(documentId -> documentId != null && !documentId.isBlank())
                .distinct()
                .toList();
        if (sourceDocumentIds.isEmpty()) return List.of();
        return documentRepository.findByTenantIdAndIdInAndDeletedAtIsNullOrderByCreatedAtDesc(
                tenantId, sourceDocumentIds);
    }

    /**
     * 在统一的上下文预算中做教育证据集合选择。
     *
     * <p>候选证据不再只按静态分数排序：每次选择都计算其对尚未覆盖的前置缺口的
     * 边际贡献，并对重复覆盖施加惩罚。这使“知识依赖图 + 掌握度”真正影响证据集合，
     * 而不是只在单文档分数上追加一个规则项。</p>
     */
    private ContextResult selectEducationEvidence(ContextResult result, String tenantId,
                                                  EducationRetrievalFilter filter,
                                                  EducationDependencyGraph dependencyGraph,
                                                  int maxChars,
                                                  EducationRetrievalStrategy strategy,
                                                  EducationRankingWeights calibratedWeights) {
        if (result == null || result.isEmpty()) return result;
        if (educationSourceRepository == null) return new ContextResult("", List.of());

        List<RankedEducationEvidence> candidates = result.evidences().stream()
                .map(evidence -> {
                    EducationRankingBreakdown breakdown = educationRanking(tenantId, evidence,
                            filter, dependencyGraph, strategy, calibratedWeights);
                    Set<String> gaps = coveredGapSet(tenantId, evidence, filter, dependencyGraph);
                    return breakdown == null ? null : new RankedEducationEvidence(evidence, breakdown,
                            gaps, gapWeights(dependencyGraph, gaps));
                })
                .filter(Objects::nonNull)
                .toList();

        Set<String> covered = new LinkedHashSet<>();
        List<ContextEvidence> selected = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        Set<String> evidenceKeys = new HashSet<>();

        // 证据规划先保留一个目标锚点，再用剩余预算补充前置缺口。这样教育检索
        // 不会为了提高缺口覆盖而丢失回答当前问题所需的目标 grounding；前置资料
        // 仍由后续的状态条件化边际选择决定顺序和数量。
        RankedEducationEvidence targetAnchor = candidates.stream()
                .filter(candidate -> candidate.breakdown().targetConceptMatch() >= 0.5)
                .max(Comparator.comparingDouble((RankedEducationEvidence candidate)
                                -> candidate.breakdown().difficultyFit())
                        .thenComparingDouble(candidate -> candidate.breakdown().baseScore()))
                .orElse(null);
        if (targetAnchor != null) {
            String block = contextBlock(targetAnchor.evidence().title(), targetAnchor.evidence().excerpt());
            if (text.length() + block.length() <= maxChars) {
                candidates = candidates.stream()
                        .filter(candidate -> !candidate.evidence().citation()
                                .equals(targetAnchor.evidence().citation()))
                        .toList();
                String sourceKey = evidenceKey(targetAnchor.evidence().citation());
                if (evidenceKeys.add(sourceKey)) {
                    double anchorCoverage = targetAnchor.gaps().isEmpty() ? 0.0 : 1.0;
                    EducationRankingBreakdown anchorBreakdown = targetAnchor.breakdown()
                            .withSelection(anchorCoverage, 0.0, targetAnchor.breakdown().baseScore());
                    ContextEvidence explained = explainEducationEvidence(targetAnchor.evidence(),
                            anchorBreakdown, targetAnchor.gaps(), true);
                    text.append(block);
                    selected.add(explained);
                    covered.addAll(targetAnchor.gaps());
                }
            }
        }
        while (!candidates.isEmpty()) {
            RankedEducationEvidence best = candidates.stream()
                    .map(candidate -> candidate.withSelection(covered))
                    .max(Comparator.comparingDouble(RankedEducationEvidence::selectionScore)
                            .thenComparingDouble(candidate -> candidate.breakdown().baseScore()))
                    .orElse(null);
            if (best == null || best.selectionScore() <= 0.0) break;
            candidates = candidates.stream()
                    .filter(candidate -> !candidate.evidence().citation().equals(best.evidence().citation()))
                    .toList();
            String sourceKey = evidenceKey(best.evidence().citation());
            if (!evidenceKeys.add(sourceKey)) continue;
            ContextEvidence explained = explainEducationEvidence(best.evidence(), best.selectedBreakdown(),
                    best.gaps(), false);
            String block = contextBlock(explained.title(), explained.excerpt());
            if (text.length() + block.length() > maxChars) continue;
            text.append(block);
            selected.add(explained);
            covered.addAll(best.gaps());
        }
        return new ContextResult(text.toString(), List.copyOf(selected));
    }

    private EducationRankingBreakdown educationRanking(String tenantId, ContextEvidence evidence,
                                                        EducationRetrievalFilter filter,
                                                        EducationDependencyGraph dependencyGraph,
                                                        EducationRetrievalStrategy strategy,
                                                        EducationRankingWeights calibratedWeights) {
        if (evidence == null || evidence.citation() == null
                || !evidence.citation().startsWith("document:")) return null;
        String documentId = evidence.documentId();
        if (documentId == null || documentId.isBlank()) return null;
        EducationKnowledgeSource source = educationSourceRepository
                .findByTenantIdAndDocumentIdAndDeletedAtIsNull(tenantId, documentId)
                .orElse(null);
        if (source == null || !filter.matchesForRetrieval(source)) return null;

        double retrievalRelevance = evidence.retrievalScore() > 0.0
                ? Math.min(1.0, evidence.retrievalScore()) : 0.5;
        String targetConcept = filter.conceptKeyOrNull() != null
                ? filter.conceptKeyOrNull()
                : dependencyGraph == null ? null : dependencyGraph.targetConcept();
        double targetConceptMatch = targetConcept != null
                && containsConcept(source.getConceptTags(), targetConcept) ? 1.0 : 0.0;
        String[] prerequisites = splitConcepts(source.getPrerequisiteConcepts());
        double prerequisiteGap = java.util.Arrays.stream(prerequisites)
                .mapToDouble(prerequisite -> 1.0 - filter.masteryFor(prerequisite))
                .average().orElse(0.0);
        Set<String> graphGaps = graphGapSet(dependencyGraph);
        Set<String> sourceConcepts = sourceConceptSet(source);
        double graphCoverage = graphGaps.isEmpty() ? 0.0
                : weightedCoverage(graphGaps, sourceConcepts, dependencyGraph);
        double targetMastery = filter.masteryFor(filter.conceptKeyOrNull());
        double preferredDifficulty = targetMastery < 0.35 ? 2.0
                : targetMastery < 0.70 ? 3.0 : 4.0;
        double difficultyFit = 1.0 - Math.min(1.0,
                Math.abs(source.getDifficultyLevel() - preferredDifficulty) / 4.0);
        double deficit = graphGaps.isEmpty() ? prerequisiteGap
                : dependencyGraph.prerequisites().stream()
                .filter(path -> graphGaps.contains(normalizeConcept(path.conceptKey())))
                .mapToDouble(EducationDependencyPath::deficit).average().orElse(prerequisiteGap);
        EducationRankingWeights weights = strategy == EducationRetrievalStrategy.CALIBRATED
                && calibratedWeights != null ? calibratedWeights
                : strategy != null && strategy.usesAdaptiveWeights()
                ? EducationRankingWeights.conditioned(targetMastery, deficit, !graphGaps.isEmpty())
                : EducationRankingWeights.fixed();
        return new EducationRankingBreakdown(retrievalRelevance, targetConceptMatch,
                prerequisiteGap, graphCoverage, difficultyFit, 0.0, 0.0, 0.0, weights);
    }

    private Set<String> coveredGapSet(String tenantId, ContextEvidence evidence,
                                      EducationRetrievalFilter filter,
                                      EducationDependencyGraph dependencyGraph) {
        if (educationSourceRepository == null || evidence == null) return Set.of();
        EducationKnowledgeSource source = educationSourceRepository
                .findByTenantIdAndDocumentIdAndDeletedAtIsNull(tenantId, evidence.documentId())
                .orElse(null);
        if (source == null || !filter.matchesForRetrieval(source)) return Set.of();
        Set<String> gaps = graphGapSet(dependencyGraph);
        Set<String> concepts = sourceConceptSet(source);
        Set<String> result = new LinkedHashSet<>(gaps);
        result.retainAll(concepts);
        return result;
    }

    /**
     * 将知识依赖图的缺口转换为证据集合选择的边际收益。
     *
     * <p>同样的掌握度缺口下，距离目标更近的前置节点优先级更高；缺口严重度仍
     * 作为主权重。这样贪心规划会先补齐依赖前沿，再把预算留给更深层节点，且
     * 该优先级可以从路径深度和 deficit 直接回放。</p>
     */
    private Map<String, Double> gapWeights(EducationDependencyGraph graph, Set<String> gaps) {
        if (gaps == null || gaps.isEmpty()) return Map.of();
        Map<String, Double> result = new LinkedHashMap<>();
        if (graph != null) {
            for (EducationDependencyPath path : graph.prerequisites()) {
                String concept = normalizeConcept(path.conceptKey());
                if (!gaps.contains(concept)) continue;
                double deficit = Math.max(0.001, path.deficit());
                double depthPriority = 1.0 / Math.max(1, path.depth());
                result.put(concept, deficit * depthPriority);
            }
        }
        for (String gap : gaps) {
            result.putIfAbsent(gap, 1.0);
        }
        return Map.copyOf(result);
    }

    private Set<String> graphGapSet(EducationDependencyGraph graph) {
        if (graph == null) return Set.of();
        return graph.prerequisites().stream()
                .filter(path -> path.deficit() >= 0.5)
                .map(path -> normalizeConcept(path.conceptKey()))
                .filter(value -> !value.isBlank())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> sourceConceptSet(EducationKnowledgeSource source) {
        Set<String> result = new LinkedHashSet<>();
        // conceptTags 表示该资料实际覆盖的知识点；prerequisiteConcepts 只表示学习该资料
        // 前需要具备什么，不能把“依赖”误报为“已经覆盖”，否则解释会高估缺口覆盖率。
        java.util.Arrays.stream(splitConcepts(source.getConceptTags()))
                .map(this::normalizeConcept).filter(value -> !value.isBlank()).forEach(result::add);
        return result;
    }

    private double weightedCoverage(Set<String> graphGaps, Set<String> sourceConcepts,
                                    EducationDependencyGraph graph) {
        if (graphGaps.isEmpty()) return 0.0;
        double total = 0.0;
        double covered = 0.0;
        for (EducationDependencyPath path : graph.prerequisites()) {
            String concept = normalizeConcept(path.conceptKey());
            if (!graphGaps.contains(concept)) continue;
            // 立即前置节点优先于更深层节点，避免在上下文预算紧张时跳过依赖前沿。
            double weight = Math.max(0.0, path.deficit()) / Math.max(1, path.depth());
            total += weight;
            if (sourceConcepts.contains(concept)) covered += weight;
        }
        return total <= 0.0 ? 0.0 : Math.min(1.0, covered / total);
    }

    private ContextEvidence explainEducationEvidence(ContextEvidence evidence,
                                                     EducationRankingBreakdown breakdown,
                                                     Set<String> gaps,
                                                     boolean targetAnchor) {
        if (evidence == null) return null;
        List<String> displayGaps = gaps == null ? List.of() : gaps.stream().toList();
        List<String> reasons = new ArrayList<>();
        if (targetAnchor) reasons.add("目标证据锚点");
        reasons.add(String.format(Locale.ROOT, "相关性 %.2f", breakdown.retrievalRelevance()));
        if (breakdown.targetConceptMatch() >= 0.5) reasons.add("匹配目标知识点");
        if (breakdown.difficultyFit() >= 0.75) reasons.add("难度适配");
        if (!displayGaps.isEmpty()) reasons.add("覆盖前置缺口：" + String.join("、", displayGaps));
        reasons.add("状态权重=" + breakdown.weights().conditioning());
        String reason = "满足课程硬约束" + (reasons.isEmpty() ? "" : "；" + String.join("；", reasons));
        return new ContextEvidence(evidence.documentId(), evidence.title(), evidence.citation(),
                evidence.excerpt(), breakdown.finalScore(), reason, displayGaps, breakdown);
    }

    private String normalizeConcept(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean containsConcept(String values, String expected) {
        if (values == null || values.isBlank() || expected == null || expected.isBlank()) return false;
        for (String value : splitConcepts(values)) {
            if (EducationRetrievalFilter.conceptsMatch(expected, value)) return true;
        }
        return false;
    }

    private String[] splitConcepts(String values) {
        if (values == null || values.isBlank()) return new String[0];
        // 与 EducationRetrievalFilter 使用相同的标签语法。课程资料由教师以中文为主录入，
        // 不能让“集合；定义域”在重排时变成一个无法匹配掌握度的伪知识点。
        return java.util.Arrays.stream(values.split("[,，;；\\n]+"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toArray(String[]::new);
    }

    private boolean matchesEducationFilter(String tenantId, KnowledgeDocument document,
                                           EducationRetrievalFilter educationFilter) {
        if (educationFilter == null || !educationFilter.active()) return true;
        if (educationSourceRepository == null) return false;
        return educationSourceRepository
                .findByTenantIdAndDocumentIdAndDeletedAtIsNull(tenantId, document.getId())
                .map(educationFilter::matchesForRetrieval)
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

    private double keywordRelevance(int score, int termCount) {
        if (score <= 0 || termCount <= 0) return 0.0;
        return Math.min(1.0, score / (double) termCount);
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

        private double rankingScore() {
            return score;
        }

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

    private record RankedEducationEvidence(ContextEvidence evidence,
                                           EducationRankingBreakdown breakdown,
                                           Set<String> gaps,
                                           Map<String, Double> gapWeights,
                                           EducationRankingBreakdown selectedBreakdown,
                                           double selectionScore) {

        private RankedEducationEvidence(ContextEvidence evidence,
                                        EducationRankingBreakdown breakdown,
                                        Set<String> gaps,
                                        Map<String, Double> gapWeights) {
            this(evidence, breakdown, Set.copyOf(gaps), Map.copyOf(gapWeights), breakdown,
                    breakdown.baseScore());
        }

        private RankedEducationEvidence withSelection(Set<String> alreadyCovered) {
            Set<String> marginalGaps = new LinkedHashSet<>(gaps);
            marginalGaps.removeAll(alreadyCovered);
            double totalWeight = gapWeights.values().stream().mapToDouble(Double::doubleValue).sum();
            double marginalWeight = marginalGaps.stream()
                    .mapToDouble(gap -> gapWeights.getOrDefault(gap, 1.0)).sum();
            double marginalCoverage = totalWeight <= 0.0 ? 0.0 : marginalWeight / totalWeight;
            double redundancy = gaps.isEmpty() ? 1.0
                    : 1.0 - marginalCoverage;
            // 基础相关性保留主体，缺口边际覆盖决定同分候选的优先级，冗余只做轻惩罚。
            double score = 0.70 * breakdown.baseScore()
                    + 0.25 * marginalCoverage
                    - 0.05 * redundancy;
            EducationRankingBreakdown selected = breakdown.withSelection(marginalCoverage,
                    redundancy, score);
            return new RankedEducationEvidence(evidence, breakdown, gaps, gapWeights, selected, score);
        }
    }
}
