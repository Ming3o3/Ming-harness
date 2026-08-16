package org.mingharness.education.api;

import org.mingharness.education.LearnerMastery;

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
        double confidenceUpper
) {

    public static LearnerMasteryView from(LearnerMastery mastery) {
        return new LearnerMasteryView(mastery.getId(), mastery.getTenantId(),
                mastery.getLearnerProfileId(), mastery.getConceptKey(), mastery.getMasteryScore(),
                mastery.getAttempts(), mastery.getCorrectAttempts(), mastery.getLastAssessedAt(),
                mastery.getUpdatedAt(), mastery.getConfidenceLower(), mastery.getConfidenceUpper());
    }
}
