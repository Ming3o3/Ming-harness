package org.mingharness.education.api;

import java.util.List;

/** 单次教育 Run 创建时冻结的检索策略审计结果。 */
public record EducationRetrievalRunPolicyView(
        String runId,
        String requestedStrategy,
        String effectiveStrategy,
        boolean snapshotFrozen,
        String snapshotType,
        String snapshotVersion,
        String conditioning,
        long eligibleRunCount,
        String selectionReason,
        List<EducationRetrievalPolicyCandidateView> candidates
) {

    public EducationRetrievalRunPolicyView {
        runId = runId == null ? "" : runId.trim();
        requestedStrategy = requestedStrategy == null ? "FULL" : requestedStrategy.trim();
        effectiveStrategy = effectiveStrategy == null ? "FULL" : effectiveStrategy.trim();
        snapshotType = snapshotType == null ? "NONE" : snapshotType.trim();
        snapshotVersion = snapshotVersion == null ? "" : snapshotVersion.trim();
        conditioning = conditioning == null ? "UNKNOWN" : conditioning.trim();
        eligibleRunCount = Math.max(0, eligibleRunCount);
        selectionReason = selectionReason == null ? "" : selectionReason.trim();
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }
}
