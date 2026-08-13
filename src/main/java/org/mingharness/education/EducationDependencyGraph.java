package org.mingharness.education;

import java.util.List;
import java.util.Map;

/** 一次教育检索使用的有界知识依赖图快照。 */
public record EducationDependencyGraph(String targetConcept,
                                       List<EducationDependencyPath> prerequisites,
                                       boolean truncated) {

    public EducationDependencyGraph {
        targetConcept = targetConcept == null ? "" : targetConcept.trim();
        prerequisites = prerequisites == null ? List.of() : List.copyOf(prerequisites);
    }

    public static EducationDependencyGraph empty(String targetConcept) {
        return new EducationDependencyGraph(targetConcept, List.of(), false);
    }

    public Map<String, EducationDependencyPath> byConcept() {
        return prerequisites.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                path -> normalize(path.conceptKey()), path -> path, (left, right) ->
                        left.depth() <= right.depth() ? left : right));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
