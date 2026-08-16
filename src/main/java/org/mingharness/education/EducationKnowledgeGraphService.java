package org.mingharness.education;

import org.mingharness.common.SensitiveDataSanitizer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 将课程元数据物化为有向知识依赖图，并为检索提供有界的传递前置查询。
 *
 * <p>教师维护资料时仍只需填写“知识点”和“前置知识”，服务会为每个目标知识点创建
 * prerequisite -> concept 边；检索阶段再结合 Run 冻结的掌握度计算缺口。这让图结构
 * 可复现、可审计，同时不要求模型在每轮自行猜测知识依赖。</p>
 */
@Service
public class EducationKnowledgeGraphService {

    private static final int MAX_DEPTH = 5;
    private static final int MAX_NODES = 100;

    private final EducationConceptDependencyRepository dependencyRepository;
    private final SensitiveDataSanitizer sanitizer;

    public EducationKnowledgeGraphService(EducationConceptDependencyRepository dependencyRepository,
                                          SensitiveDataSanitizer sanitizer) {
        this.dependencyRepository = dependencyRepository;
        this.sanitizer = sanitizer;
    }

    /** 根据一份课程资料的结构化标签替换其派生边。 */
    @Transactional
    public void replaceDerivedEdges(EducationKnowledgeSource source) {
        if (source == null) return;
        dependencyRepository.deleteByTenantIdAndSourceDocumentId(
                source.getTenantId(), source.getDocumentId());
        // Ensure old edges are removed before inserts with the same unique key are queued.
        dependencyRepository.flush();
        List<String> concepts = split(source.getConceptTags());
        List<String> prerequisites = split(source.getPrerequisiteConcepts());
        if (concepts.isEmpty() || prerequisites.isEmpty()) return;

        List<EducationConceptDependency> edges = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String concept : concepts) {
            for (String prerequisite : prerequisites) {
                String conceptKey = normalizeConcept(concept);
                String prerequisiteKey = normalizeConcept(prerequisite);
                if (conceptKey == null || prerequisiteKey == null || conceptKey.equals(prerequisiteKey)) {
                    continue;
                }
                String key = conceptKey + "\u0000" + prerequisiteKey;
                if (!seen.add(key)) continue;
                edges.add(new EducationConceptDependency(
                        source.getTenantId(), source.getSubject(), source.getGradeLevel(),
                        source.getCurriculumVersion(), concept, prerequisite,
                        source.getDocumentId(), "PREREQUISITE", 1.0));
            }
        }
        if (!edges.isEmpty()) dependencyRepository.saveAll(edges);
    }

    @Transactional
    public void removeDerivedEdges(String tenantId, String sourceDocumentId) {
        if (tenantId == null || tenantId.isBlank() || sourceDocumentId == null || sourceDocumentId.isBlank()) return;
        dependencyRepository.deleteByTenantIdAndSourceDocumentId(tenantId, sourceDocumentId);
    }

    /**
     * 查询目标知识点的传递前置图。BFS 保证返回最短依赖深度，并以固定上限防止错误标签
     * 或环形依赖拖垮检索；掌握度来自当前 Run 冻结的过滤器。
     */
    @Transactional(readOnly = true)
    public EducationDependencyGraph resolve(String tenantId, EducationRetrievalFilter filter) {
        String target = filter == null ? null : filter.conceptKeyOrNull();
        if (target == null || target.isBlank() || filter == null || !filter.active()) {
            return EducationDependencyGraph.empty(target);
        }
        List<EducationConceptDependency> edges = dependencyRepository
                .findByTenantIdAndSubjectAndGradeLevelAndCurriculumVersionOrderByConceptKeyAscPrerequisiteConceptAsc(
                        tenantId, filter.subjectOrNull(), filter.gradeLevelOrNull(),
                        filter.curriculumVersionOrNull());
        if (edges.isEmpty()) return EducationDependencyGraph.empty(target);

        Map<String, List<EducationConceptDependency>> incoming = new LinkedHashMap<>();
        for (EducationConceptDependency edge : edges) {
            incoming.computeIfAbsent(normalizeConcept(edge.getConceptKey()), ignored -> new ArrayList<>()).add(edge);
        }
        String normalizedTarget = normalizeConcept(target);
        Map<String, Integer> depths = new LinkedHashMap<>();
        ArrayDeque<NodeDepth> queue = new ArrayDeque<>();
        queue.add(new NodeDepth(normalizedTarget, 0));
        boolean truncated = false;
        while (!queue.isEmpty()) {
            NodeDepth current = queue.removeFirst();
            if (current.depth() >= MAX_DEPTH) {
                if (!incoming.getOrDefault(current.concept(), List.of()).isEmpty()) truncated = true;
                continue;
            }
            for (EducationConceptDependency edge : incoming.getOrDefault(current.concept(), List.of())) {
                String prerequisite = normalizeConcept(edge.getPrerequisiteConcept());
                if (prerequisite == null || prerequisite.equals(normalizedTarget)) continue;
                int nextDepth = current.depth() + 1;
                Integer existing = depths.get(prerequisite);
                if (existing != null && existing <= nextDepth) continue;
                if (depths.size() >= MAX_NODES) {
                    truncated = true;
                    break;
                }
                depths.put(prerequisite, nextDepth);
                queue.addLast(new NodeDepth(prerequisite, nextDepth));
            }
            if (truncated && depths.size() >= MAX_NODES) break;
        }

        List<EducationDependencyPath> paths = depths.entrySet().stream()
                .map(entry -> new EducationDependencyPath(
                        displayName(entry.getKey(), edges), entry.getValue(),
                        filter.conservativeMasteryFor(entry.getKey()),
                        1.0 - filter.conservativeMasteryFor(entry.getKey())))
                .sorted(Comparator.comparingInt(EducationDependencyPath::depth)
                        .thenComparing(path -> path.conceptKey().toLowerCase(Locale.ROOT)))
                .toList();
        return new EducationDependencyGraph(target, paths, truncated);
    }

    private String displayName(String normalized, List<EducationConceptDependency> edges) {
        return edges.stream()
                .map(EducationConceptDependency::getPrerequisiteConcept)
                .filter(value -> normalizeConcept(value).equals(normalized))
                .findFirst().orElse(normalized);
    }

    private List<String> split(String value) {
        if (value == null || value.isBlank()) return List.of();
        return java.util.Arrays.stream(value.split("[,，;；\\n]+"))
                .map(item -> sanitizer.sanitize(item == null ? "" : item.trim()))
                .filter(item -> !item.isBlank())
                .distinct()
                .toList();
    }

    private String normalizeConcept(String value) {
        if (value == null || value.isBlank()) return null;
        return sanitizer.sanitize(value.trim()).toLowerCase(Locale.ROOT);
    }

    private record NodeDepth(String concept, int depth) {
    }
}
