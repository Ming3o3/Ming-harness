package org.mingharness.education;

import java.util.Arrays;
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
        Integer maxDifficulty
) {

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
}
