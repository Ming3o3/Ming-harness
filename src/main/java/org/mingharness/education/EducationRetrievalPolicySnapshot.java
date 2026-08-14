package org.mingharness.education;

import java.util.List;

/** 创建 Run 时冻结的学习状态自适应策略选择结果。 */
public record EducationRetrievalPolicySnapshot(
        String version,
        String conditioning,
        String selectedStrategy,
        long eligibleRunCount,
        String selectionReason,
        List<EducationRetrievalPolicyCandidate> candidates
) {

    public static final String VERSION = "retrieval-policy-v1";

    public EducationRetrievalPolicySnapshot {
        version = version == null || version.isBlank() ? VERSION : version.trim();
        conditioning = conditioning == null || conditioning.isBlank() ? "UNKNOWN" : conditioning.trim();
        selectedStrategy = normalizeStrategy(selectedStrategy);
        eligibleRunCount = Math.max(0, eligibleRunCount);
        selectionReason = selectionReason == null ? "" : selectionReason.trim();
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }

    public static EducationRetrievalPolicySnapshot prior(String conditioning) {
        return new EducationRetrievalPolicySnapshot(VERSION, conditioning, EducationRetrievalStrategy.FULL.name(),
                0, "历史样本不足，回退 FULL", List.of());
    }

    private static String normalizeStrategy(String value) {
        EducationRetrievalStrategy strategy = EducationRetrievalStrategy.parse(value);
        return strategy == EducationRetrievalStrategy.ADAPTIVE
                ? EducationRetrievalStrategy.FULL.name() : strategy.name();
    }
}
