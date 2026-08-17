package org.mingharness.education;

import org.junit.jupiter.api.Test;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.education.api.LearningAssignmentSubmissionRequest;
import org.mingharness.runtime.domain.Run;
import org.mingharness.runtime.repository.RunRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LearningAssignmentSubmissionServiceTests {

    @Test
    void shouldBindLearnerSubmissionToLatestSuccessfulEducationRun() {
        LearningAssignmentService assignments = mock(LearningAssignmentService.class);
        LearningAssignmentSubmissionRepository submissions = mock(LearningAssignmentSubmissionRepository.class);
        RunRepository runs = mock(RunRepository.class);
        LearningAssignmentNotificationService notifications = mock(LearningAssignmentNotificationService.class);
        LearningAssignment assignment = assignment();
        Run run = successfulRun(assignment);
        when(assignments.getForParticipant("tenant-a", "student-1", assignment.getId()))
                .thenReturn(assignment);
        when(runs.findTopByTenantIdAndUserIdAndEducationLearningAssignmentIdOrderByCreatedAtDesc(
                "tenant-a", "student-1", assignment.getId())).thenReturn(Optional.of(run));
        when(submissions.findByTenantIdAndLearningAssignmentIdAndRunId(
                "tenant-a", assignment.getId(), run.getId())).thenReturn(Optional.empty());
        when(submissions.save(any(LearningAssignmentSubmission.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = new LearningAssignmentSubmissionService(assignments, submissions, runs,
                notifications, new SensitiveDataSanitizer()).submit("tenant-a", "student-1",
                assignment.getId(), new LearningAssignmentSubmissionRequest(null, "我的完整作答"));

        assertEquals(assignment.getId(), result.learningAssignmentId());
        assertEquals(run.getId(), result.runId());
        assertEquals("我的完整作答", result.content());
        verify(notifications).ensureForSubmission(any(LearningAssignmentSubmission.class), any());
    }

    @Test
    void shouldReturnExistingSubmissionForSameRunWithoutCreatingDuplicate() {
        LearningAssignmentService assignments = mock(LearningAssignmentService.class);
        LearningAssignmentSubmissionRepository submissions = mock(LearningAssignmentSubmissionRepository.class);
        RunRepository runs = mock(RunRepository.class);
        LearningAssignmentNotificationService notifications = mock(LearningAssignmentNotificationService.class);
        LearningAssignment assignment = assignment();
        Run run = successfulRun(assignment);
        LearningAssignmentSubmission existing = new LearningAssignmentSubmission(
                "tenant-a", assignment.getId(), "student-1", run.getId(), "原始提交", Instant.now());
        when(assignments.getForParticipant("tenant-a", "student-1", assignment.getId()))
                .thenReturn(assignment);
        when(runs.findTopByTenantIdAndUserIdAndEducationLearningAssignmentIdOrderByCreatedAtDesc(
                "tenant-a", "student-1", assignment.getId())).thenReturn(Optional.of(run));
        when(submissions.findByTenantIdAndLearningAssignmentIdAndRunId(
                "tenant-a", assignment.getId(), run.getId())).thenReturn(Optional.of(existing));

        var result = new LearningAssignmentSubmissionService(assignments, submissions, runs,
                notifications, new SensitiveDataSanitizer()).submit("tenant-a", "student-1",
                assignment.getId(), new LearningAssignmentSubmissionRequest(null, "重复提交"));

        assertEquals(existing.getId(), result.id());
    }

    @Test
    void shouldRejectSubmissionWhenRunIsNotSuccessful() {
        LearningAssignmentService assignments = mock(LearningAssignmentService.class);
        LearningAssignmentSubmissionRepository submissions = mock(LearningAssignmentSubmissionRepository.class);
        RunRepository runs = mock(RunRepository.class);
        LearningAssignment assignment = assignment();
        Run run = new Run("tenant-a", "student-1", "作业", "输入", BigDecimal.ONE,
                "demo-model", "prompt-v1", "policy-v1");
        when(assignments.getForParticipant("tenant-a", "student-1", assignment.getId()))
                .thenReturn(assignment);
        when(runs.findTopByTenantIdAndUserIdAndEducationLearningAssignmentIdOrderByCreatedAtDesc(
                "tenant-a", "student-1", assignment.getId())).thenReturn(Optional.of(run));

        var service = new LearningAssignmentSubmissionService(assignments, submissions, runs,
                null, new SensitiveDataSanitizer());
        var exception = assertThrows(org.mingharness.common.BusinessException.class, () -> service.submit(
                "tenant-a", "student-1", assignment.getId(),
                new LearningAssignmentSubmissionRequest(null, "未完成作答")));

        assertEquals("ASSIGNMENT_SUBMISSION_RUN_INVALID", exception.getCode());
    }

    @Test
    void shouldEvaluateCodeSubmissionWithFrozenAssignmentLanguage() {
        LearningAssignmentService assignments = mock(LearningAssignmentService.class);
        LearningAssignmentSubmissionRepository submissions = mock(LearningAssignmentSubmissionRepository.class);
        RunRepository runs = mock(RunRepository.class);
        LearningAssignmentNotificationService notifications = mock(LearningAssignmentNotificationService.class);
        EducationCodeEvaluator evaluator = mock(EducationCodeEvaluator.class);
        LearningAssignment assignment = new LearningAssignment("tenant-a", "teacher-1", "student-1",
                "Python 作业", "完成函数", "编程", "大一", "课程版", "函数", 0.8,
                Instant.now().plusSeconds(3600), null, null, null, "python");
        assignment.accept("profile-1", "goal-1", Instant.now());
        Run run = successfulRun(assignment);
        when(assignments.getForParticipant("tenant-a", "student-1", assignment.getId()))
                .thenReturn(assignment);
        when(runs.findTopByTenantIdAndUserIdAndEducationLearningAssignmentIdOrderByCreatedAtDesc(
                "tenant-a", "student-1", assignment.getId())).thenReturn(Optional.of(run));
        when(submissions.findByTenantIdAndLearningAssignmentIdAndRunId(
                "tenant-a", assignment.getId(), run.getId())).thenReturn(Optional.empty());
        when(evaluator.evaluate("PYTHON", "print('ok')")).thenReturn(
                new EducationCodeEvaluationResult(CodeEvaluationStatus.PASSED,
                        "语法/编译检查通过。", "", 0, 42));
        when(submissions.save(any(LearningAssignmentSubmission.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = new LearningAssignmentSubmissionService(assignments, submissions, runs,
                notifications, new SensitiveDataSanitizer(), evaluator).submit(
                "tenant-a", "student-1", assignment.getId(),
                new LearningAssignmentSubmissionRequest(null, "print('ok')", "python",
                        LearningAssignmentSubmissionType.CODE));

        assertEquals("CODE", result.submissionType());
        assertEquals("PYTHON", result.programmingLanguage());
        assertEquals("PASSED", result.codeEvaluationStatus());
        assertEquals("NONE", result.codeDiagnosticCategory());
        assertEquals(42, result.codeEvaluationDurationMs());
        verify(evaluator).evaluate("PYTHON", "print('ok')");
    }

    @Test
    void shouldRejectCodeSubmissionThatOverridesAssignmentLanguage() {
        LearningAssignmentService assignments = mock(LearningAssignmentService.class);
        LearningAssignmentSubmissionRepository submissions = mock(LearningAssignmentSubmissionRepository.class);
        RunRepository runs = mock(RunRepository.class);
        LearningAssignment assignment = new LearningAssignment("tenant-a", "teacher-1", "student-1",
                "Python 作业", "完成函数", "编程", "大一", "课程版", "函数", 0.8,
                Instant.now().plusSeconds(3600), null, null, null, "python");
        assignment.accept("profile-1", "goal-1", Instant.now());
        Run run = successfulRun(assignment);
        when(assignments.getForParticipant("tenant-a", "student-1", assignment.getId()))
                .thenReturn(assignment);
        when(runs.findTopByTenantIdAndUserIdAndEducationLearningAssignmentIdOrderByCreatedAtDesc(
                "tenant-a", "student-1", assignment.getId())).thenReturn(Optional.of(run));

        var exception = assertThrows(org.mingharness.common.BusinessException.class, () ->
                new LearningAssignmentSubmissionService(assignments, submissions, runs,
                        null, new SensitiveDataSanitizer(), mock(EducationCodeEvaluator.class)).submit(
                        "tenant-a", "student-1", assignment.getId(),
                        new LearningAssignmentSubmissionRequest(null, "console.log('x')", "javascript",
                                LearningAssignmentSubmissionType.CODE)));

        assertEquals("ASSIGNMENT_SUBMISSION_LANGUAGE_MISMATCH", exception.getCode());
    }

    private LearningAssignment assignment() {
        LearningAssignment assignment = new LearningAssignment("tenant-a", "teacher-1", "student-1",
                "函数作业", "完成练习", "数学", "高中一年级", "人教A版", "函数定义域", 0.8,
                Instant.now().plusSeconds(3600));
        assignment.accept("profile-1", "goal-1", Instant.now());
        return assignment;
    }

    private Run successfulRun(LearningAssignment assignment) {
        Run run = new Run("tenant-a", "student-1", "作业", "输入", BigDecimal.ONE,
                "demo-model", "prompt-v1", "policy-v1");
        run.attachEducationConfiguration(new EducationRunConfiguration(true, "profile-1", "goal-1",
                assignment.getId(), assignment.getTitle(), assignment.getInstructions(), null,
                null, assignment.getTitle(), 0.2, 0.8, assignment.getSubject(),
                assignment.getGradeLevel(), assignment.getCurriculumVersion(), assignment.getConceptKey(),
                null, null, "PRACTICE", ""));
        run.start();
        run.succeed("完成");
        return run;
    }
}
