package org.mingharness.education.api;

import org.mingharness.education.EducationDependencyPath;

public record EducationDependencyPathView(String conceptKey, int depth,
                                          double masteryScore, double deficit,
                                          double uncertainty, double forgettingRisk,
                                          double statePriority) {

    /** 兼容旧 API 适配器和组件测试。 */
    public EducationDependencyPathView(String conceptKey, int depth,
                                       double masteryScore, double deficit) {
        this(conceptKey, depth, masteryScore, deficit, 0.0, 0.0, 0.0);
    }

    public static EducationDependencyPathView from(EducationDependencyPath path) {
        return new EducationDependencyPathView(path.conceptKey(), path.depth(),
                path.masteryScore(), path.deficit(), path.uncertainty(),
                path.forgettingRisk(), path.statePriority());
    }
}
