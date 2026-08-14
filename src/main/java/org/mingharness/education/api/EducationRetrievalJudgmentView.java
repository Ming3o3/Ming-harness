package org.mingharness.education.api;

import org.mingharness.education.EducationRetrievalJudgment;

import java.time.Instant;

public record EducationRetrievalJudgmentView(
        String id,
        String tenantId,
        String runId,
        String stepId,
        String evaluatorUserId,
        String documentId,
        String evidenceCitation,
        int targetGroundingScore,
        int prerequisiteUtilityScore,
        int difficultyFitScore,
        int overallUtilityScore,
        String rubricVersion,
        String note,
        Instant createdAt
) {
    public static EducationRetrievalJudgmentView from(EducationRetrievalJudgment judgment) {
        return new EducationRetrievalJudgmentView(judgment.getId(), judgment.getTenantId(),
                judgment.getRunId(), judgment.getStepId(), judgment.getEvaluatorUserId(),
                judgment.getDocumentId(), judgment.getEvidenceCitation(),
                judgment.getTargetGroundingScore(), judgment.getPrerequisiteUtilityScore(),
                judgment.getDifficultyFitScore(), judgment.getOverallUtilityScore(),
                judgment.getRubricVersion(), judgment.getNote(), judgment.getCreatedAt());
    }
}
