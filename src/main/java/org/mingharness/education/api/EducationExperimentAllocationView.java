package org.mingharness.education.api;

/**
 * 请求策略到实际执行策略的分配审计事实。
 *
 * <p>ADAPTIVE 和 BALANCED_EXPERIMENT 是分配器，不应被误当成检索算法；该视图保留
 * 请求/实际两列，并按冻结的学习状态条件聚合，便于检查分配是否均衡以及导出论文实验样本。</p>
 */
public record EducationExperimentAllocationView(
        String requestedStrategy,
        String effectiveStrategy,
        String conditioning,
        long allocationCount,
        long successfulRunCount,
        long outcomeRunCount,
        long assessmentCount
) {

    public EducationExperimentAllocationView {
        requestedStrategy = requestedStrategy == null ? "FULL" : requestedStrategy.trim();
        effectiveStrategy = effectiveStrategy == null ? "FULL" : effectiveStrategy.trim();
        conditioning = conditioning == null || conditioning.isBlank() ? "UNKNOWN" : conditioning.trim();
        allocationCount = Math.max(0, allocationCount);
        successfulRunCount = Math.max(0, successfulRunCount);
        outcomeRunCount = Math.max(0, outcomeRunCount);
        assessmentCount = Math.max(0, assessmentCount);
    }
}
