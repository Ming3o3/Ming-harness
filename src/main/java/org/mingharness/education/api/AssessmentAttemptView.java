package org.mingharness.education.api;

import org.mingharness.education.AssessmentAttempt;
import org.mingharness.education.EducationRetrievalEvidence;

import java.time.Instant;
import java.util.List;

public record AssessmentAttemptView(
        String id,
        String tenantId,
        String userId,
        String runId,
        String stepId,
        String learningGoalId,
        String learningAssignmentId,
        String learnerProfileId,
        String conceptKey,
        boolean correct,
        double observedMastery,
        double masteryBefore,
        double masteryAfter,
        String assessmentType,
        String reviewPlanId,
        String evidenceSource,
        String evidenceText,
        String learnerEvidenceQuote,
        String feedback,
        List<AssessmentEvidenceReference> retrievalEvidence,
        int difficultyLevel,
        String knowledgePointScoresJson,
        boolean hintUsed,
        boolean independentEvidence,
        String questionType,
        Instant createdAt
) {

    public static AssessmentAttemptView from(AssessmentAttempt attempt) {
        return new AssessmentAttemptView(attempt.getId(), attempt.getTenantId(), attempt.getUserId(),
                attempt.getRunId(), attempt.getStepId(), attempt.getLearningGoalId(),
                attempt.getLearningAssignmentId(), attempt.getLearnerProfileId(),
                attempt.getConceptKey(), attempt.isCorrect(),
                attempt.getObservedMastery(), attempt.getMasteryBefore(), attempt.getMasteryAfter(),
                attempt.getAssessmentType().name(), attempt.getReviewPlanId(),
                attempt.getEvidenceSource(), attempt.getEvidenceText(),
                attempt.getLearnerEvidenceQuote(),
                attempt.getFeedback(),
                EducationRetrievalEvidence.decode(attempt.getRetrievalEvidenceJson()),
                attempt.getDifficultyLevel(), attempt.getKnowledgePointScoresJson(),
                attempt.isHintUsed(), attempt.isIndependentEvidence(), attempt.getQuestionType(),
                attempt.getCreatedAt());
    }
}
