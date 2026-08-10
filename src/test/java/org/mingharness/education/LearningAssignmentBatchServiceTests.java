package org.mingharness.education;

import org.junit.jupiter.api.Test;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.education.api.EducationCourseAssignmentRequest;
import org.mingharness.education.api.LearningAssignmentRequest;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LearningAssignmentBatchServiceTests {

    @Test
    void shouldExpandActiveCourseRosterIntoIndependentAssignments() {
        EducationCourseService courses = mock(EducationCourseService.class);
        EducationEnrollmentRepository enrollments = mock(EducationEnrollmentRepository.class);
        LearningAssignmentRepository assignments = mock(LearningAssignmentRepository.class);
        LearningAssignmentService assignmentService = mock(LearningAssignmentService.class);
        EducationCourse course = course();
        when(courses.requireOwnerCourse("tenant-a", "teacher-1", course.getId())).thenReturn(course);
        when(assignments.findByTenantIdAndCourseIdAndBatchIdOrderByCreatedAtAsc(
                "tenant-a", course.getId(), "batch-1")).thenReturn(List.of());
        when(enrollments.findByTenantIdAndCourseIdAndStatus(
                "tenant-a", course.getId(), EducationEnrollmentStatus.ACTIVE)).thenReturn(List.of(
                new EducationEnrollment("tenant-a", course.getId(), "student-1", Instant.now()),
                new EducationEnrollment("tenant-a", course.getId(), "student-2", Instant.now())));
        when(assignmentService.create(eq("tenant-a"), eq("teacher-1"),
                any(LearningAssignmentRequest.class), eq("batch-1"))).thenAnswer(invocation -> {
            LearningAssignmentRequest request = invocation.getArgument(2);
            return new LearningAssignment("tenant-a", "teacher-1", request.learnerUserId(),
                    request.title(), request.instructions(), request.subject(), request.gradeLevel(),
                    request.curriculumVersion(), request.conceptKey(), request.effectiveTargetMastery(),
                    request.dueAt(), request.courseId(), "batch-1");
        });

        var result = new LearningAssignmentBatchService(courses, enrollments, assignments,
                assignmentService, new SensitiveDataSanitizer()).assign(
                "tenant-a", "teacher-1", course.getId(),
                new EducationCourseAssignmentRequest("函数作业", "完成练习", "函数定义域", 0.8,
                        Instant.now().plusSeconds(3600)), "batch-1");

        assertEquals(false, result.reused());
        assertEquals(2, result.assignmentCount());
        assertEquals(List.of("student-1", "student-2"), result.assignments().stream()
                .map(item -> item.learnerUserId()).toList());
        verify(assignmentService, times(2)).create(eq("tenant-a"), eq("teacher-1"),
                any(LearningAssignmentRequest.class), eq("batch-1"));
    }

    @Test
    void shouldReturnExistingBatchWhenClientRetriesSameIdempotencyKey() {
        EducationCourseService courses = mock(EducationCourseService.class);
        EducationEnrollmentRepository enrollments = mock(EducationEnrollmentRepository.class);
        LearningAssignmentRepository assignments = mock(LearningAssignmentRepository.class);
        LearningAssignmentService assignmentService = mock(LearningAssignmentService.class);
        EducationCourse course = course();
        LearningAssignment existing = new LearningAssignment("tenant-a", "teacher-1", "student-1",
                "函数作业", "完成练习", "数学", "高中一年级", "人教A版", "函数定义域", 0.8,
                Instant.now().plusSeconds(3600), course.getId(), "batch-1");
        when(courses.requireOwnerCourse("tenant-a", "teacher-1", course.getId())).thenReturn(course);
        when(assignments.findByTenantIdAndCourseIdAndBatchIdOrderByCreatedAtAsc(
                "tenant-a", course.getId(), "batch-1")).thenReturn(List.of(existing));

        var result = new LearningAssignmentBatchService(courses, enrollments, assignments,
                assignmentService, new SensitiveDataSanitizer()).assign(
                "tenant-a", "teacher-1", course.getId(),
                new EducationCourseAssignmentRequest("函数作业", "完成练习", "函数定义域", 0.8,
                        Instant.now().plusSeconds(3600)), "batch-1");

        assertEquals(true, result.reused());
        assertEquals(1, result.assignmentCount());
        verifyNoInteractions(enrollments, assignmentService);
    }

    private EducationCourse course() {
        return new EducationCourse("tenant-a", "teacher-1", "math-g1", "高一数学", "数学",
                "高中一年级", "人教A版");
    }
}
