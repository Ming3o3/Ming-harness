package org.mingharness.education.api;

import java.time.Instant;
import java.util.List;

/** 教育检索论文实验摘要；数据来自 Run、检索证据快照和形成性测评事实。 */
public record EducationExperimentView(
        Instant generatedAt,
        long totalRunCount,
        long successfulRunCount,
        long totalAssessmentCount,
        boolean tenantScope,
        long pairedLearnerGoalCount,
        long fullyPairedLearnerGoalCount,
        List<EducationExperimentStrategyView> strategies,
        List<EducationExperimentPairView> pairedComparisons
) {

    public EducationExperimentView {
        strategies = strategies == null ? List.of() : List.copyOf(strategies);
        pairedComparisons = pairedComparisons == null ? List.of() : List.copyOf(pairedComparisons);
    }
}
