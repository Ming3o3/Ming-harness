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
        LearningAssignmentSubmissionRepository submissions = mock(LearningAssignmentSubmissionRepository.class);
        EducationEnrollmentRepository enrollments = mock(EducationEnrollmentRepository.class);
        EducationCourseResultService resultService = mock(EducationCourseResultService.class);
        EducationCourse course = course();
        LearningAssignment completed = completedAssignment(course, "student-1");
        completed.verifyByTeacher("teacher-1", "已核验", Instant.now());
        when(courses.findByTenantIdAndId("tenant-a", course.getId())).thenReturn(Optional.of(course));
        when(assignments.findByTenantIdAndCourseIdOrderByCreatedAtDesc(
                "tenant-a", course.getId())).thenReturn(List.of(completed));
        when(enrollments.findByTenantIdAndCourseIdAndStatus(
                "tenant-a", course.getId(), EducationEnrollmentStatus.ACTIVE)).thenReturn(List.of(
                new EducationEnrollment("tenant-a", course.getId(), "student-1", Instant.now())));
        when(submissions.existsByTenantIdAndLearningAssignmentId("tenant-a", completed.getId()))
                .thenReturn(true);
        when(courses.save(any(EducationCourse.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(enrollments.countByTenantIdAndCourseIdAndStatus(
                "tenant-a", course.getId(), EducationEnrollmentStatus.ACTIVE)).thenReturn(1L);

        var result = new EducationCourseCompletionService(courses, assignments, submissions, enrollments,
                new SensitiveDataSanitizer(), resultService).complete("tenant-a", "teacher-1", course.getId(),
                new EducationCourseCompletionRequest("本期课程完成"));

        assertEquals("COMPLETED", result.status());
        assertEquals("本期课程完成", result.completionNote());
        assertEquals("teacher-1", result.completedByUserId());
        assertNotNull(result.completedAt());
        verify(courses).save(course);
        org.mockito.Mockito.verify(resultService).capture(
                "tenant-a", "teacher-1", course);
    }

    @Test
    void shouldRepairMissingResultSnapshotWhenCompletedCourseIsRetried() {
        EducationCourseRepository courses = mock(EducationCourseRepository.class);
        LearningAssignmentRepository assignments = mock(LearningAssignmentRepository.class);
        LearningAssignmentSubmissionRepository submissions = mock(LearningAssignmentSubmissionRepository.class);
        EducationEnrollmentRepository enrollments = mock(EducationEnrollmentRepository.class);
        EducationCourseResultService resultService = mock(EducationCourseResultService.class);
        EducationCourse course = course();
        Instant completedAt = Instant.parse("2026-08-10T12:00:00Z");
        course.complete("teacher-1", "历史结课", completedAt);
        when(courses.findByTenantIdAndId("tenant-a", course.getId())).thenReturn(Optional.of(course));
        when(enrollments.countByTenantIdAndCourseIdAndStatus(
                "tenant-a", course.getId(), EducationEnrollmentStatus.ACTIVE)).thenReturn(1L);

        var result = new EducationCourseCompletionService(courses, assignments, submissions, enrollments,
                new SensitiveDataSanitizer(), resultService).complete("tenant-a", "teacher-1", course.getId(),
                new EducationCourseCompletionRequest("不应覆盖历史结课说明"));

        assertEquals("COMPLETED", result.status());
        assertEquals("历史结课", result.completionNote());
        assertEquals(completedAt, result.completedAt());
        org.mockito.Mockito.verify(resultService).capture("tenant-a", "teacher-1", course);
        verifyNoInteractions(assignments, submissions);
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
        when(enrollments.findByTenantIdAndCourseIdAndStatus(
                "tenant-a", course.getId(), EducationEnrollmentStatus.ACTIVE)).thenReturn(List.of(
                new EducationEnrollment("tenant-a", course.getId(), "student-1", Instant.now())));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                new EducationCourseCompletionService(courses, assignments, enrollments,
                        new SensitiveDataSanitizer()).complete("tenant-a", "teacher-1", course.getId(), null));

        assertEquals("EDUCATION_COURSE_NOT_READY_TO_COMPLETE", exception.getCode());
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

    @Test
    void shouldRejectCourseCompletionWhenTeacherVerifiedAssignmentHasNoLearnerSubmission() {
        EducationCourseRepository courses = mock(EducationCourseRepository.class);
        LearningAssignmentRepository assignments = mock(LearningAssignmentRepository.class);
        LearningAssignmentSubmissionRepository submissions = mock(LearningAssignmentSubmissionRepository.class);
        EducationEnrollmentRepository enrollments = mock(EducationEnrollmentRepository.class);
        EducationCourse course = course();
        LearningAssignment completed = completedAssignment(course, "student-1");
        completed.verifyByTeacher("teacher-1", "已核验", Instant.now());
        when(courses.findByTenantIdAndId("tenant-a", course.getId())).thenReturn(Optional.of(course));
        when(assignments.findByTenantIdAndCourseIdOrderByCreatedAtDesc(
                "tenant-a", course.getId())).thenReturn(List.of(completed));
        when(enrollments.findByTenantIdAndCourseIdAndStatus(
                "tenant-a", course.getId(), EducationEnrollmentStatus.ACTIVE)).thenReturn(List.of(
                new EducationEnrollment("tenant-a", course.getId(), "student-1", Instant.now())));
        when(submissions.existsByTenantIdAndLearningAssignmentId("tenant-a", completed.getId()))
                .thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                new EducationCourseCompletionService(courses, assignments, submissions, enrollments,
                        new SensitiveDataSanitizer()).complete("tenant-a", "teacher-1", course.getId(), null));

        assertEquals("EDUCATION_COURSE_SUBMISSIONS_REQUIRED", exception.getCode());
    }

    @Test
    void shouldRejectCourseCompletionWhenActiveLearnerHasNoAssignment() {
        EducationCourseRepository courses = mock(EducationCourseRepository.class);
        LearningAssignmentRepository assignments = mock(LearningAssignmentRepository.class);
        EducationEnrollmentRepository enrollments = mock(EducationEnrollmentRepository.class);
        EducationCourse course = course();
        LearningAssignment completed = completedAssignment(course, "student-1");
        completed.verifyByTeacher("teacher-1", "已核验", Instant.now());
        when(courses.findByTenantIdAndId("tenant-a", course.getId())).thenReturn(Optional.of(course));
        when(assignments.findByTenantIdAndCourseIdOrderByCreatedAtDesc(
                "tenant-a", course.getId())).thenReturn(List.of(completed));
        when(enrollments.findByTenantIdAndCourseIdAndStatus(
                "tenant-a", course.getId(), EducationEnrollmentStatus.ACTIVE)).thenReturn(List.of(
                new EducationEnrollment("tenant-a", course.getId(), "student-1", Instant.now()),
                new EducationEnrollment("tenant-a", course.getId(), "student-2", Instant.now())));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                new EducationCourseCompletionService(courses, assignments, enrollments,
                        new SensitiveDataSanitizer()).complete("tenant-a", "teacher-1", course.getId(), null));

        assertEquals("EDUCATION_COURSE_ROSTER_ASSIGNMENTS_REQUIRED", exception.getCode());
    }

    @Test
    void shouldIgnoreHistoricalAssignmentOfRemovedLearnerWhenCompletingCourse() {
        EducationCourseRepository courses = mock(EducationCourseRepository.class);
        LearningAssignmentRepository assignments = mock(LearningAssignmentRepository.class);
        LearningAssignmentSubmissionRepository submissions = mock(LearningAssignmentSubmissionRepository.class);
        EducationEnrollmentRepository enrollments = mock(EducationEnrollmentRepository.class);
        EducationCourseResultService resultService = mock(EducationCourseResultService.class);
        EducationCourse course = course();
        LearningAssignment activeAssignment = completedAssignment(course, "student-1");
        activeAssignment.verifyByTeacher("teacher-1", "已核验", Instant.now());
        LearningAssignment historicalAssignment = completedAssignment(course, "student-2");
        historicalAssignment.verifyByTeacher("teacher-1", "已核验", Instant.now());
        EducationEnrollment active = new EducationEnrollment(
                "tenant-a", course.getId(), "student-1", Instant.now());
        EducationEnrollment removed = new EducationEnrollment(
                "tenant-a", course.getId(), "student-2", Instant.now());
        removed.remove(Instant.now());

        when(courses.findByTenantIdAndId("tenant-a", course.getId())).thenReturn(Optional.of(course));
        when(assignments.findByTenantIdAndCourseIdOrderByCreatedAtDesc(
                "tenant-a", course.getId())).thenReturn(List.of(activeAssignment, historicalAssignment));
        when(enrollments.findByTenantIdAndCourseIdAndStatus(
                "tenant-a", course.getId(), EducationEnrollmentStatus.ACTIVE)).thenReturn(List.of(active));
        when(submissions.existsByTenantIdAndLearningAssignmentId(any(), any())).thenReturn(true);
        when(courses.save(any(EducationCourse.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(enrollments.countByTenantIdAndCourseIdAndStatus(
                "tenant-a", course.getId(), EducationEnrollmentStatus.ACTIVE)).thenReturn(1L);

        var result = new EducationCourseCompletionService(courses, assignments, submissions, enrollments,
                new SensitiveDataSanitizer(), resultService).complete("tenant-a", "teacher-1", course.getId(),
                new EducationCourseCompletionRequest("移除成员后的正常结课"));

        assertEquals("COMPLETED", result.status());
        verify(resultService).capture("tenant-a", "teacher-1", course);
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
