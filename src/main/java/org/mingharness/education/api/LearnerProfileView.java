package org.mingharness.education.api;

import org.mingharness.education.LearnerProfile;

import java.time.Instant;

public record LearnerProfileView(
        String id,
        String tenantId,
        String userId,
        String subject,
        String gradeLevel,
        String curriculumVersion,
        String learningGoal,
        String language,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {

    public static LearnerProfileView from(LearnerProfile profile) {
        return new LearnerProfileView(profile.getId(), profile.getTenantId(), profile.getUserId(),
                profile.getSubject(), profile.getGradeLevel(), profile.getCurriculumVersion(),
                profile.getLearningGoal(), profile.getLanguage(), profile.isActive(),
                profile.getCreatedAt(), profile.getUpdatedAt());
    }
}
