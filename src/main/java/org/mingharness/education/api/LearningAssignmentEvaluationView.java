package org.mingharness.education.api;

import org.mingharness.education.LearningAssignmentEvaluation;

import java.time.Instant;

public record LearningAssignmentEvaluationView(
        String id,
        String learningAssignmentId,
        String courseId,
        String learnerUserId,
        String evaluatorUserId,
        String decision,
        String rubricVersion,
        int contentCorrectnessScore,
        int evidenceQualityScore,
        int transferReadinessScore,
        String note,
        Instant createdAt
) {
    public static LearningAssignmentEvaluationView from(LearningAssignmentEvaluation evaluation) {
        return new LearningAssignmentEvaluationView(evaluation.getId(),
                evaluation.getLearningAssignmentId(), evaluation.getCourseId(),
                evaluation.getLearnerUserId(), evaluation.getEvaluatorUserId(),
                evaluation.getDecision().name(), evaluation.getRubricVersion(),
                evaluation.getContentCorrectnessScore(), evaluation.getEvidenceQualityScore(),
                evaluation.getTransferReadinessScore(), evaluation.getNote(), evaluation.getCreatedAt());
    }
}
