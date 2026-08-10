package org.mingharness.education;

import org.junit.jupiter.api.Test;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.education.api.EducationCourseCompletionRequest;
import org.mingharness.education.api.LearningAssignmentSubmissionRequest;
import org.mingharness.runtime.domain.Run;
import org.mingharness.runtime.repository.RunRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 课程级验收：成功教育 Run、学习者提交、教师确认最终收敛为结课事实。 */
class EducationCourseBusinessFlowTests {

    @Test
    void shouldCloseCourseAfterRunSubmissionAndTeacherVerification() {
        EducationCourse course = new EducationCourse("tenant-a", "teacher-1", "math-g1",
                "高一数学", "数学", "高中一年级", "人教A版");
        LearningAssignment assignment = new LearningAssignment("tenant-a", "teacher-1", "student-1",
                "函数定义域作业", "完成题目并写出判定依据", "数学", "高中一年级", "人教A版",
                "函数定义域", 0.8, Instant.now().plusSeconds(3600), course.getId(), "batch-1");
        assignment.accept("profile-1", "goal-1", Instant.now());

        LearningAssignmentService assignmentService = mock(LearningAssignmentService.class);
        LearningAssignmentSubmissionRepository submissions = mock(LearningAssignmentSubmissionRepository.class);
        RunRepository runs = mock(RunRepository.class);
        LearningAssignmentNotificationService notifications = mock(LearningAssignmentNotificationService.class);
        Run run = successfulRun(course, assignment);
        when(assignmentService.getForParticipant("tenant-a", "student-1", assignment.getId()))
                .thenReturn(assignment);
        when(runs.findTopByTenantIdAndUserIdAndEducationLearningAssignmentIdOrderByCreatedAtDesc(
                "tenant-a", "student-1", assignment.getId())).thenReturn(Optional.of(run));
        when(submissions.findByTenantIdAndLearningAssignmentIdAndRunId(
                "tenant-a", assignment.getId(), run.getId())).thenReturn(Optional.empty());
        when(submissions.save(any(LearningAssignmentSubmission.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        LearningAssignmentSubmissionService submissionService = new LearningAssignmentSubmissionService(
                assignmentService, submissions, runs, notifications, new SensitiveDataSanitizer());
        assignment.complete(Instant.now());
        submissionService.submit("tenant-a", "student-1", assignment.getId(),
                new LearningAssignmentSubmissionRequest(null, "学生写出定义域判定依据"));
        assignment.verifyByTeacher("teacher-1", "证据充分", Instant.now());

        EducationCourseRepository courses = mock(EducationCourseRepository.class);
        LearningAssignmentRepository assignments = mock(LearningAssignmentRepository.class);
        EducationEnrollmentRepository enrollments = mock(EducationEnrollmentRepository.class);
        when(courses.findByTenantIdAndId("tenant-a", course.getId())).thenReturn(Optional.of(course));
        when(assignments.findByTenantIdAndCourseIdOrderByCreatedAtDesc(
                "tenant-a", course.getId())).thenReturn(List.of(assignment));
        when(submissions.existsByTenantIdAndLearningAssignmentId("tenant-a", assignment.getId()))
                .thenReturn(true);
        when(courses.save(any(EducationCourse.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(enrollments.countByTenantIdAndCourseIdAndStatus(
                "tenant-a", course.getId(), EducationEnrollmentStatus.ACTIVE)).thenReturn(1L);

        var completed = new EducationCourseCompletionService(courses, assignments, submissions,
                enrollments, new SensitiveDataSanitizer()).complete("tenant-a", "teacher-1",
                course.getId(), new EducationCourseCompletionRequest("本期课程完成"));

        assertEquals("COMPLETED", completed.status());
        assertEquals("本期课程完成", completed.completionNote());
    }

    private Run successfulRun(EducationCourse course, LearningAssignment assignment) {
        Run run = new Run("tenant-a", "student-1", assignment.getTitle(), assignment.getInstructions(),
                BigDecimal.ONE, "demo-model", "prompt-v1", "policy-v1");
        run.attachEducationConfiguration(new EducationRunConfiguration(true, "profile-1", "goal-1",
                assignment.getId(), assignment.getTitle(), assignment.getInstructions(), null,
                null, assignment.getTitle(), 0.2, 0.8, course.getSubject(), course.getGradeLevel(),
                course.getCurriculumVersion(), assignment.getConceptKey(), null, null, "PRACTICE", ""));
        run.start();
        run.succeed("完成");
        return run;
    }
}
