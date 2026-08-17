package org.mingharness.education;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 教育模式的硬约束过滤器。
 *
 * <p>课程字段是硬过滤条件，不把年级或课程版本交给模型自行判断，避免相似但不适用的
 * 教材内容进入上下文。conceptKey 和难度用于缩小教育知识源候选集。</p>
 */
public record EducationRetrievalFilter(
        String subject,
        String gradeLevel,
        String curriculumVersion,
        String conceptKey,
        String programmingLanguage,
        Integer minDifficulty,
        Integer maxDifficulty,
        Map<String, Double> masteryScores,
        EducationDependencyGraph dependencyGraph,
        Map<String, LearnerStateEvidence> masteryEvidence
) {

    /** 保持旧调用方的六参数构造方式；学习者状态默认为空。 */
    public EducationRetrievalFilter(String subject, String gradeLevel, String curriculumVersion,
                                    String conceptKey, Integer minDifficulty, Integer maxDifficulty) {
        this(subject, gradeLevel, curriculumVersion, conceptKey, minDifficulty, maxDifficulty, Map.of());
    }

    /** 兼容已携带掌握度快照但尚未冻结依赖图的调用方。 */
    public EducationRetrievalFilter(String subject, String gradeLevel, String curriculumVersion,
                                    String conceptKey, Integer minDifficulty, Integer maxDifficulty,
                                    Map<String, Double> masteryScores) {
        this(subject, gradeLevel, curriculumVersion, conceptKey, null, minDifficulty, maxDifficulty,
                masteryScores, null, Map.of());
    }

    /** 绑定可选编程语言的轻量构造方式。 */
    public EducationRetrievalFilter(String subject, String gradeLevel, String curriculumVersion,
                                    String conceptKey, String programmingLanguage,
                                    Integer minDifficulty, Integer maxDifficulty) {
        this(subject, gradeLevel, curriculumVersion, conceptKey, programmingLanguage,
                minDifficulty, maxDifficulty, Map.of(), null, Map.of());
    }

    /** 携带 Run 创建时冻结的掌握度证据与依赖图。 */
    public EducationRetrievalFilter(String subject, String gradeLevel, String curriculumVersion,
                                    String conceptKey, Integer minDifficulty, Integer maxDifficulty,
                                    Map<String, Double> masteryScores,
                                    EducationDependencyGraph dependencyGraph) {
        this(subject, gradeLevel, curriculumVersion, conceptKey, null, minDifficulty, maxDifficulty,
                masteryScores, dependencyGraph, Map.of());
    }

    /** 兼容尚未携带编程语言标签的完整旧构造方式。 */
    public EducationRetrievalFilter(String subject, String gradeLevel, String curriculumVersion,
                                    String conceptKey, Integer minDifficulty, Integer maxDifficulty,
                                    Map<String, Double> masteryScores,
                                    EducationDependencyGraph dependencyGraph,
                                    Map<String, LearnerStateEvidence> masteryEvidence) {
        this(subject, gradeLevel, curriculumVersion, conceptKey, null, minDifficulty, maxDifficulty,
                masteryScores, dependencyGraph, masteryEvidence);
    }

    public EducationRetrievalFilter {
        subject = normalize(subject);
        gradeLevel = normalize(gradeLevel);
        curriculumVersion = normalize(curriculumVersion);
        conceptKey = normalize(conceptKey);
        programmingLanguage = normalizeProgrammingLanguage(programmingLanguage);
        minDifficulty = boundDifficulty(minDifficulty);
        maxDifficulty = boundDifficulty(maxDifficulty);
        if (minDifficulty != null && maxDifficulty != null && minDifficulty > maxDifficulty) {
            int temporary = minDifficulty;
            minDifficulty = maxDifficulty;
            maxDifficulty = temporary;
        }
        masteryScores = normalizeMasteryScores(masteryScores);
        dependencyGraph = dependencyGraph;
        masteryEvidence = normalizeMasteryEvidence(masteryEvidence);
    }

    public boolean active() {
        return subject != null || gradeLevel != null || curriculumVersion != null
                || conceptKey != null || programmingLanguage != null
                || minDifficulty != null || maxDifficulty != null;
    }

    public boolean matches(EducationKnowledgeSource source) {
        if (!matchesCourseAndDifficulty(source)) return false;
        return conceptKey == null || containsConcept(conceptKey, source.getConceptTags());
    }

    private boolean matchesCourseAndDifficulty(EducationKnowledgeSource source) {
        if (source == null || !source.isActive()) return false;
        return equalsOrUnconstrained(subject, source.getSubject())
                && equalsOrUnconstrained(gradeLevel, source.getGradeLevel())
                && equalsOrUnconstrained(curriculumVersion, source.getCurriculumVersion())
                && languageMatches(source)
                && (minDifficulty == null || source.getDifficultyLevel() >= minDifficulty)
                && (maxDifficulty == null || source.getDifficultyLevel() <= maxDifficulty);
    }

    private boolean languageMatches(EducationKnowledgeSource source) {
        return programmingLanguage == null
                || (source != null && programmingLanguage.equalsIgnoreCase(source.getProgrammingLanguage()));
    }

    public boolean requiresEducationMetadata() {
        return active();
    }

    public String subjectOrNull() { return subject; }
    public String gradeLevelOrNull() { return gradeLevel; }
    public String curriculumVersionOrNull() { return curriculumVersion; }
    public String conceptKeyOrNull() { return conceptKey; }
    public String programmingLanguageOrNull() { return programmingLanguage; }
    public Integer minDifficultyOrNull() { return minDifficulty; }
    public Integer maxDifficultyOrNull() { return maxDifficulty; }

    /** 返回不可变的知识点掌握度快照，分数已限制在 [0,1]。 */
    public Map<String, Double> masteryScores() { return masteryScores; }

    /** Run 创建时冻结的图快照；旧请求为 null，允许服务回退到实时图查询。 */
    public EducationDependencyGraph dependencyGraphOrNull() { return dependencyGraph; }

    public EducationRetrievalFilter withDependencyGraph(EducationDependencyGraph graph) {
        return new EducationRetrievalFilter(subject, gradeLevel, curriculumVersion, conceptKey,
                programmingLanguage, minDifficulty, maxDifficulty, masteryScores, graph, masteryEvidence);
    }

    /** 返回目标知识点及其传递前置知识点，供图驱动召回使用。 */
    public Set<String> retrievalConceptKeys() {
        Set<String> concepts = new java.util.LinkedHashSet<>();
        if (conceptKey != null) concepts.add(normalizeConcept(conceptKey));
        if (dependencyGraph != null) {
            dependencyGraph.prerequisites().stream()
                    .map(EducationDependencyPath::conceptKey)
                    .map(EducationRetrievalFilter::normalizeConcept)
                    .filter(java.util.Objects::nonNull)
                    .forEach(concepts::add);
        }
        return Collections.unmodifiableSet(concepts);
    }

    /** 图只扩展知识点候选，课程、可见性和难度仍然是硬约束。 */
    public boolean matchesForRetrieval(EducationKnowledgeSource source) {
        if (!matchesCourseAndDifficulty(source)) return false;
        if (conceptKey == null) return true;
        return containsAnyConcept(retrievalConceptKeys(), source == null ? null : source.getConceptTags());
    }

    /** 供教育重排使用：没有观测过的知识点按中性掌握度处理。 */
    public double masteryFor(String concept) {
        if (concept == null || concept.isBlank()) return 0.5;
        String normalized = normalizeConcept(concept);
        LearnerStateEvidence evidence = masteryEvidence.get(normalized);
        return evidence == null ? masteryScores.getOrDefault(normalized, 0.5)
                : evidence.effectiveMastery();
    }

    /** 返回指定知识点的冻结证据；旧 Run 没有证据快照时使用中性状态。 */
    public LearnerStateEvidence masteryEvidenceFor(String concept) {
        if (concept == null || concept.isBlank()) {
            return new LearnerStateEvidence(0.5, 0, 0);
        }
        String normalized = normalizeConcept(concept);
        LearnerStateEvidence evidence = masteryEvidence.get(normalized);
        if (evidence != null) return evidence;
        return new LearnerStateEvidence(masteryFor(normalized), 0, 0);
    }

    /** 使用置信下界驱动新 Run；历史 Run 没有计数时保留其摘要分数语义。 */
    public double conservativeMasteryFor(String concept) {
        String normalized = concept == null ? null : normalizeConcept(concept);
        // 旧 Run 只有摘要分数，没有观测计数；保留历史排序语义，同时通过
        // uncertaintyFor 显式标记高不确定性。新 Run 严格使用 Wilson 下界。
        return normalized != null && masteryEvidence.containsKey(normalized)
                ? masteryEvidence.get(normalized).conservativeMastery()
                : masteryFor(normalized);
    }

    public double uncertaintyFor(String concept) {
        String normalized = concept == null ? null : normalizeConcept(concept);
        LearnerStateEvidence evidence = normalized == null ? null : masteryEvidence.get(normalized);
        return evidence == null ? 1.0 : evidence.uncertainty();
    }

    /** 返回冻结时点的保持度风险，旧 Run 没有时间证据时为 0。 */
    public double forgettingRiskFor(String concept) {
        String normalized = concept == null ? null : normalizeConcept(concept);
        LearnerStateEvidence evidence = normalized == null ? null : masteryEvidence.get(normalized);
        return evidence == null ? 0.0 : evidence.forgettingRisk();
    }

    private static boolean equalsOrUnconstrained(String expected, String actual) {
        return expected == null || expected.equalsIgnoreCase(actual);
    }

    private static boolean containsConcept(String expected, String values) {
        if (expected == null) return true;
        if (values == null || values.isBlank()) return false;
        String normalizedExpected = normalizeConcept(expected);
        return normalizedConcepts(values).stream()
                .anyMatch(tag -> conceptsMatch(normalizedExpected, tag));
    }

    private static boolean containsAnyConcept(Set<String> expected, String values) {
        if (expected == null || expected.isEmpty()) return true;
        if (values == null || values.isBlank()) return false;
        return normalizedConcepts(values).stream()
                .anyMatch(tag -> expected.stream().anyMatch(candidate -> conceptsMatch(candidate, tag)));
    }

    /**
     * 课程标签是短主题，作业目标有时会带教学动作，例如“理解二次函数的概念及一般形式”。
     * 在双方都是足够具体的词组时，允许短主题命中目标描述，避免把同一课程主题误判为无资料。
     */
    public static boolean conceptsMatch(String expected, String candidate) {
        String normalizedExpected = normalizeConcept(expected);
        String normalizedCandidate = normalizeConcept(candidate);
        if (normalizedExpected == null || normalizedCandidate == null) return false;
        if (normalizedExpected.equals(normalizedCandidate)) return true;
        if (conceptLength(normalizedExpected) < 3 || conceptLength(normalizedCandidate) < 3) return false;
        return normalizedExpected.contains(normalizedCandidate)
                || normalizedCandidate.contains(normalizedExpected);
    }

    private static Set<String> normalizedConcepts(String values) {
        return Arrays.stream(values.split("[,，;；\\n]+"))
                .map(EducationRetrievalFilter::normalizeConcept)
                .filter(value -> value != null)
                .collect(Collectors.toSet());
    }

    private static int conceptLength(String value) {
        return value.codePointCount(0, value.length());
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private static String normalizeProgrammingLanguage(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private static Integer boundDifficulty(Integer value) {
        return value == null ? null : Math.max(1, Math.min(5, value));
    }

    private static Map<String, Double> normalizeMasteryScores(Map<String, Double> values) {
        if (values == null || values.isEmpty()) return Map.of();
        Map<String, Double> normalized = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            String concept = normalizeConcept(key);
            if (concept == null || value == null || !Double.isFinite(value)) return;
            double bounded = Math.max(0.0, Math.min(1.0, value));
            // Run 快照的摘要使用两位小数；过滤器采用同一规范化精度，确保幂等重放稳定。
            normalized.put(concept, Math.round(bounded * 100.0) / 100.0);
        });
        return normalized.isEmpty()
                ? Map.of() : Collections.unmodifiableMap(normalized);
    }

    private static Map<String, LearnerStateEvidence> normalizeMasteryEvidence(
            Map<String, LearnerStateEvidence> values) {
        if (values == null || values.isEmpty()) return Map.of();
        Map<String, LearnerStateEvidence> normalized = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            String concept = normalizeConcept(key);
            if (concept != null && value != null) normalized.put(concept, value);
        });
        return normalized.isEmpty() ? Map.of() : Collections.unmodifiableMap(normalized);
    }

    private static String normalizeConcept(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }
}
