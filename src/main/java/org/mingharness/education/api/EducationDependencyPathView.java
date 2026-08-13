package org.mingharness.education.api;

import org.mingharness.education.EducationDependencyPath;

public record EducationDependencyPathView(String conceptKey, int depth,
                                          double masteryScore, double deficit) {

    public static EducationDependencyPathView from(EducationDependencyPath path) {
        return new EducationDependencyPathView(path.conceptKey(), path.depth(),
                path.masteryScore(), path.deficit());
    }
}
