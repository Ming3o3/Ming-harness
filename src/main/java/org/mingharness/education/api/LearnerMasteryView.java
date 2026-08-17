package org.mingharness.education.api;

import org.mingharness.education.LearnerMastery;
import org.mingharness.education.LearnerRetentionModel;

import java.time.Instant;

public record LearnerMasteryView(
        String id,
        String tenantId,
        String learnerProfileId,
        String conceptKey,
        double masteryScore,
        int attempts,
        int correctAttempts,
        Instant lastAssessedAt,
        Instant updatedAt,
        double confidenceLower,
        double confidenceUpper,
        double effectiveMasteryScore,
        double retentionScore,
        double forgettingRisk,
        String retentionModelVersion
) {

    public static LearnerMasteryView from(LearnerMastery mastery) {
        Instant now = Instant.now();
        return new LearnerMasteryView(mastery.getId(), mastery.getTenantId(),
                mastery.getLearnerProfileId(), mastery.getConceptKey(), mastery.getMasteryScore(),
                mastery.getAttempts(), mastery.getCorrectAttempts(), mastery.getLastAssessedAt(),
                mastery.getUpdatedAt(), mastery.confidenceLowerAt(now), mastery.confidenceUpperAt(now),
                mastery.effectiveMasteryAt(now), mastery.retentionScoreAt(now),
                mastery.forgettingRiskAt(now), LearnerRetentionModel.VERSION);
    }
}
