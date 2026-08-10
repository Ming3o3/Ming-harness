package org.mingharness.education.api;

import org.mingharness.education.EducationEnrollment;

import java.time.Instant;

/** 课程名单成员安全投影。 */
public record EducationEnrollmentView(
        String id,
        String courseId,
        String learnerUserId,
        String status,
        Instant enrolledAt,
        Instant removedAt,
        Instant updatedAt
) {
    public static EducationEnrollmentView from(EducationEnrollment enrollment) {
        return new EducationEnrollmentView(enrollment.getId(), enrollment.getCourseId(),
                enrollment.getLearnerUserId(), enrollment.getStatus().name(),
                enrollment.getEnrolledAt(), enrollment.getRemovedAt(), enrollment.getUpdatedAt());
    }
}
