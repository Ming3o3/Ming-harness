package org.mingharness.education.api;

/**
 * 学习者状态与知识依赖图联合消融的描述性摘要。
 *
 * <p>interaction effect 使用同一学习者—目标内固定混合召回条件的四臂配对：
 * FULL − NO_LEARNER_STATE − NO_DEPENDENCY_GRAPH + NO_STATE_NO_GRAPH。
 * VECTOR_ONLY 另作召回模态基线，不参与该协同项。该摘要用于观察协同趋势，
 * 不替代随机实验、置信区间或显著性检验。</p>
 */
public record EducationExperimentSynergyView(
        long fullyPairedLearnerGoalCount,
        double fullAverageMasteryGain,
        double noLearnerStateAverageMasteryGain,
        double noDependencyGraphAverageMasteryGain,
        double noStateNoGraphAverageMasteryGain,
        double vectorOnlyAverageMasteryGain,
        double fullMinusNoLearnerState,
        double fullMinusNoDependencyGraph,
        double interactionEffect,
        String sampleStatus
) {
}
