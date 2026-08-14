package org.mingharness.education.api;

import java.util.List;

/** 当前学习状态下的可审计策略推荐结果。 */
public record EducationRetrievalPolicyView(
        String version,
        String conditioning,
        String selectedStrategy,
        long eligibleRunCount,
        String selectionReason,
        List<EducationRetrievalPolicyCandidateView> candidates
) {

    public EducationRetrievalPolicyView {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }

    public static EducationRetrievalPolicyView from(
            org.mingharness.education.EducationRetrievalPolicySnapshot snapshot) {
        return new EducationRetrievalPolicyView(snapshot.version(), snapshot.conditioning(),
                snapshot.selectedStrategy(), snapshot.eligibleRunCount(), snapshot.selectionReason(),
                snapshot.candidates().stream().map(EducationRetrievalPolicyCandidateView::from).toList());
    }
}
