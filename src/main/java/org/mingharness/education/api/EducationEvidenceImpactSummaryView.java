package org.mingharness.education.api;

import java.time.Instant;
import java.util.List;

/** 教育检索证据与学习结果归因的只读摘要。 */
public record EducationEvidenceImpactSummaryView(
        Instant generatedAt,
        boolean tenantScope,
        long totalFormativeAssessmentCount,
        long assessmentsWithEvidence,
        long assessmentsWithMatchedEvidence,
        long evidenceReferenceCount,
        long matchedEvidenceReferenceCount,
        double evidenceLinkRate,
        double snapshotMatchRate,
        String sampleStatus,
        List<EducationEvidenceImpactView> impacts
) {

    public EducationEvidenceImpactSummaryView {
        impacts = impacts == null ? List.of() : List.copyOf(impacts);
    }
}
