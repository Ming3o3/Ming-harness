package org.mingharness.education.api;

import org.mingharness.education.LearnerStateTransition;

import java.time.Instant;

/** 学习者或教师审计页面可见的状态转移摘要。 */
public record LearnerStateTransitionView(
        String id,
        String learnerProfileId,
        String conceptKey,
        String runId,
        double beforeMastery,
        double afterMastery,
        double beforeEffectiveMastery,
        double afterEffectiveMastery,
        double beforeRetentionScore,
        double afterRetentionScore,
        int beforeAttempts,
        int afterAttempts,
        int beforeCorrectAttempts,
        int afterCorrectAttempts,
        String evidenceSource,
        String assessmentType,
        String diagnosticCategory,
        String evidenceText,
        Double behaviorTestPassRate,
        int difficultyLevel,
        double evidenceWeight,
        boolean hintUsed,
        boolean independentEvidence,
        String stateModelVersion,
        String retentionModelVersion,
        Instant createdAt
) {

    public static LearnerStateTransitionView from(LearnerStateTransition transition) {
        return new LearnerStateTransitionView(transition.getId(), transition.getLearnerProfileId(),
                transition.getConceptKey(), transition.getRunId(), transition.getBeforeMastery(),
                transition.getAfterMastery(), transition.getBeforeEffectiveMastery(),
                transition.getAfterEffectiveMastery(), transition.getBeforeRetentionScore(),
                transition.getAfterRetentionScore(), transition.getBeforeAttempts(),
                transition.getAfterAttempts(), transition.getBeforeCorrectAttempts(),
                transition.getAfterCorrectAttempts(), transition.getEvidenceSource(),
                transition.getAssessmentType(), transition.getDiagnosticCategory(),
                transition.getEvidenceText(), transition.getBehaviorTestPassRate(),
                transition.getDifficultyLevel(), transition.getEvidenceWeight(),
                transition.isHintUsed(), transition.isIndependentEvidence(),
                transition.getStateModelVersion(), transition.getRetentionModelVersion(),
                transition.getCreatedAt());
    }
}
