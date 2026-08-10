package org.mingharness.education.api;

import org.mingharness.education.EducationCourse;

import java.time.Instant;

/** 课程实例的安全投影，名单数仅统计当前活跃学习者。 */
public record EducationCourseView(
        String id,
        String ownerUserId,
        String code,
        String title,
        String subject,
        String gradeLevel,
        String curriculumVersion,
        String status,
        long activeEnrollmentCount,
        Instant createdAt,
        Instant updatedAt
) {
    public static EducationCourseView from(EducationCourse course, long activeEnrollmentCount) {
        return new EducationCourseView(course.getId(), course.getOwnerUserId(), course.getCode(),
                course.getTitle(), course.getSubject(), course.getGradeLevel(),
                course.getCurriculumVersion(), course.getStatus().name(), activeEnrollmentCount,
                course.getCreatedAt(), course.getUpdatedAt());
    }
}
