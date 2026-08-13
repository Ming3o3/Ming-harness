package org.mingharness.education;

/** 目标知识点到某个前置知识点的最短依赖深度及当前掌握度。 */
public record EducationDependencyPath(String conceptKey, int depth,
                                      double masteryScore, double deficit) {

    public EducationDependencyPath {
        conceptKey = conceptKey == null ? "" : conceptKey.trim();
        depth = Math.max(1, depth);
        masteryScore = bounded(masteryScore);
        deficit = bounded(deficit);
    }

    private static double bounded(double value) {
        return Double.isFinite(value) ? Math.max(0.0, Math.min(1.0, value)) : 0.0;
    }
}
