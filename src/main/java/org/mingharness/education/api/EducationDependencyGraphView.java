package org.mingharness.education.api;

import org.mingharness.education.EducationDependencyGraph;

import java.util.List;

public record EducationDependencyGraphView(String targetConcept,
                                           List<EducationDependencyPathView> prerequisites,
                                           boolean truncated) {

    public static EducationDependencyGraphView from(EducationDependencyGraph graph) {
        return new EducationDependencyGraphView(graph.targetConcept(),
                graph.prerequisites().stream().map(EducationDependencyPathView::from).toList(),
                graph.truncated());
    }
}
