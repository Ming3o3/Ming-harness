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
        List<EducationExperimentPairView> pairedComparisons,
        List<EducationExperimentAllocationView> allocations
) {

    /** 兼容旧版调用方；没有分配审计时返回空列表。 */
    public EducationExperimentView(Instant generatedAt, long totalRunCount, long successfulRunCount,
                                   long totalAssessmentCount, boolean tenantScope,
                                   long pairedLearnerGoalCount, long fullyPairedLearnerGoalCount,
                                   List<EducationExperimentStrategyView> strategies,
                                   List<EducationExperimentPairView> pairedComparisons) {
        this(generatedAt, totalRunCount, successfulRunCount, totalAssessmentCount, tenantScope,
                pairedLearnerGoalCount, fullyPairedLearnerGoalCount, strategies, pairedComparisons, List.of());
    }

    public EducationExperimentView {
        strategies = strategies == null ? List.of() : List.copyOf(strategies);
        pairedComparisons = pairedComparisons == null ? List.of() : List.copyOf(pairedComparisons);
        allocations = allocations == null ? List.of() : List.copyOf(allocations);
    }
}
