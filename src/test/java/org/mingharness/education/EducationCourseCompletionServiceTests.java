package org.mingharness.education;

import org.junit.jupiter.api.Test;
import org.mingharness.common.BusinessException;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.education.api.EducationCourseCompletionRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EducationCourseCompletionServiceTests {

    @Test
    void shouldCompleteCourseOnlyAfterEveryEffectiveAssignmentIsTeacherVerified() {
        EducationCourseRepository courses = mock(EducationCourseRepository.class);
        LearningAssignmentRepository assignments = mock(LearningAssignmentRepository.class);
        EducationEnrollmentRepository enrollments = mock(EducationEnrollmentRepository.class);
        EducationCourse course = course();
        LearningAssignment completed = completedAssignment(course, "student-1");
        completed.verifyByTeacher("teacher-1", "已核验", Instant.now());
        when(courses.findByTenantIdAndId("tenant-a", course.getId())).thenReturn(Optional.of(course));
        when(assignments.findByTenantIdAndCourseIdOrderByCreatedAtDesc(
                "tenant-a", course.getId())).thenReturn(List.of(completed));
        when(courses.save(any(EducationCourse.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(enrollments.countByTenantIdAndCourseIdAndStatus(
                "tenant-a", course.getId(), EducationEnrollmentStatus.ACTIVE)).thenReturn(1L);

        var result = new EducationCourseCompletionService(courses, assignments, enrollments,
                new SensitiveDataSanitizer()).complete("tenant-a", "teacher-1", course.getId(),
                new EducationCourseCompletionRequest("本期课程完成"));

        assertEquals("COMPLETED", result.status());
        assertEquals("本期课程完成", result.completionNote());
        assertEquals("teacher-1", result.completedByUserId());
        assertNotNull(result.completedAt());
        verify(courses).save(course);
    }

    @Test
    void shouldRejectCourseCompletionWhenAssignmentStillNeedsTeacherAction() {
        EducationCourseRepository courses = mock(EducationCourseRepository.class);
        LearningAssignmentRepository assignments = mock(LearningAssignmentRepository.class);
        EducationEnrollmentRepository enrollments = mock(EducationEnrollmentRepository.class);
        EducationCourse course = course();
        LearningAssignment assigned = new LearningAssignment("tenant-a", "teacher-1", "student-1",
                "函数作业", "完成练习", "数学", "高中一年级", "人教A版", "函数定义域", 0.8,
                Instant.now().plusSeconds(3600), course.getId(), "batch-1");
        when(courses.findByTenantIdAndId("tenant-a", course.getId())).thenReturn(Optional.of(course));
        when(assignments.findByTenantIdAndCourseIdOrderByCreatedAtDesc(
                "tenant-a", course.getId())).thenReturn(List.of(assigned));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                new EducationCourseCompletionService(courses, assignments, enrollments,
                        new SensitiveDataSanitizer()).complete("tenant-a", "teacher-1", course.getId(), null));

        assertEquals("EDUCATION_COURSE_NOT_READY_TO_COMPLETE", exception.getCode());
        verifyNoInteractions(enrollments);
    }

    @Test
    void shouldRejectCourseCompletionWithoutEffectiveAssignments() {
        EducationCourseRepository courses = mock(EducationCourseRepository.class);
        LearningAssignmentRepository assignments = mock(LearningAssignmentRepository.class);
        EducationEnrollmentRepository enrollments = mock(EducationEnrollmentRepository.class);
        EducationCourse course = course();
        LearningAssignment cancelled = new LearningAssignment("tenant-a", "teacher-1", "student-1",
                "函数作业", "完成练习", "数学", "高中一年级", "人教A版", "函数定义域", 0.8,
                Instant.now().plusSeconds(3600), course.getId(), "batch-1");
        cancelled.cancel();
        when(courses.findByTenantIdAndId("tenant-a", course.getId())).thenReturn(Optional.of(course));
        when(assignments.findByTenantIdAndCourseIdOrderByCreatedAtDesc(
                "tenant-a", course.getId())).thenReturn(List.of(cancelled));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                new EducationCourseCompletionService(courses, assignments, enrollments,
                        new SensitiveDataSanitizer()).complete("tenant-a", "teacher-1", course.getId(), null));

        assertEquals("EDUCATION_COURSE_ASSIGNMENTS_EMPTY", exception.getCode());
    }

    private EducationCourse course() {
        return new EducationCourse("tenant-a", "teacher-1", "math-g1", "高一数学", "数学",
                "高中一年级", "人教A版");
    }

    private LearningAssignment completedAssignment(EducationCourse course, String learnerUserId) {
        LearningAssignment assignment = new LearningAssignment("tenant-a", "teacher-1", learnerUserId,
                "函数作业", "完成练习", "数学", "高中一年级", "人教A版", "函数定义域", 0.8,
                Instant.now().plusSeconds(3600), course.getId(), "batch-1");
        assignment.accept("profile-1", "goal-1", Instant.now());
        assignment.complete(Instant.now());
        return assignment;
    }
}
