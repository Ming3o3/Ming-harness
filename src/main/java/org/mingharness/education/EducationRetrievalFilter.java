package org.mingharness.education;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
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
        Integer minDifficulty,
        Integer maxDifficulty,
        Map<String, Double> masteryScores
) {

    /** 保持旧调用方的六参数构造方式；学习者状态默认为空。 */
    public EducationRetrievalFilter(String subject, String gradeLevel, String curriculumVersion,
                                    String conceptKey, Integer minDifficulty, Integer maxDifficulty) {
        this(subject, gradeLevel, curriculumVersion, conceptKey, minDifficulty, maxDifficulty, Map.of());
    }

    public EducationRetrievalFilter {
        subject = normalize(subject);
        gradeLevel = normalize(gradeLevel);
        curriculumVersion = normalize(curriculumVersion);
        conceptKey = normalize(conceptKey);
        minDifficulty = boundDifficulty(minDifficulty);
        maxDifficulty = boundDifficulty(maxDifficulty);
        if (minDifficulty != null && maxDifficulty != null && minDifficulty > maxDifficulty) {
            int temporary = minDifficulty;
            minDifficulty = maxDifficulty;
            maxDifficulty = temporary;
        }
        masteryScores = normalizeMasteryScores(masteryScores);
    }

    public boolean active() {
        return subject != null || gradeLevel != null || curriculumVersion != null
                || conceptKey != null || minDifficulty != null || maxDifficulty != null;
    }

    public boolean matches(EducationKnowledgeSource source) {
        if (source == null || !source.isActive()) return false;
        return equalsOrUnconstrained(subject, source.getSubject())
                && equalsOrUnconstrained(gradeLevel, source.getGradeLevel())
                && equalsOrUnconstrained(curriculumVersion, source.getCurriculumVersion())
                && containsConcept(conceptKey, source.getConceptTags())
                && (minDifficulty == null || source.getDifficultyLevel() >= minDifficulty)
                && (maxDifficulty == null || source.getDifficultyLevel() <= maxDifficulty);
    }

    public boolean requiresEducationMetadata() {
        return active();
    }

    public String subjectOrNull() { return subject; }
    public String gradeLevelOrNull() { return gradeLevel; }
    public String curriculumVersionOrNull() { return curriculumVersion; }
    public String conceptKeyOrNull() { return conceptKey; }
    public Integer minDifficultyOrNull() { return minDifficulty; }
    public Integer maxDifficultyOrNull() { return maxDifficulty; }

    /** 返回不可变的知识点掌握度快照，分数已限制在 [0,1]。 */
    public Map<String, Double> masteryScores() { return masteryScores; }

    /** 供教育重排使用：没有观测过的知识点按中性掌握度处理。 */
    public double masteryFor(String concept) {
        if (concept == null || concept.isBlank()) return 0.5;
        return masteryScores.getOrDefault(normalize(concept), 0.5);
    }

    private static boolean equalsOrUnconstrained(String expected, String actual) {
        return expected == null || expected.equalsIgnoreCase(actual);
    }

    private static boolean containsConcept(String expected, String values) {
        if (expected == null) return true;
        if (values == null || values.isBlank()) return false;
        Set<String> normalized = Arrays.stream(values.split(","))
                .map(EducationRetrievalFilter::normalize)
                .filter(value -> value != null)
                .collect(Collectors.toSet());
        return normalized.contains(expected);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private static Integer boundDifficulty(Integer value) {
        return value == null ? null : Math.max(1, Math.min(5, value));
    }

    private static Map<String, Double> normalizeMasteryScores(Map<String, Double> values) {
        if (values == null || values.isEmpty()) return Map.of();
        Map<String, Double> normalized = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            String concept = normalize(key);
            if (concept == null || value == null || !Double.isFinite(value)) return;
            normalized.put(concept, Math.max(0.0, Math.min(1.0, value)));
        });
        return normalized.isEmpty()
                ? Map.of() : Collections.unmodifiableMap(normalized);
    }
}
