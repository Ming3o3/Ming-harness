package org.mingharness.education;

import org.junit.jupiter.api.Test;
import org.mingharness.runtime.domain.Run;
import org.mingharness.runtime.repository.RunRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LearningAssignmentReconciliationServiceTests {

    @Test
    void shouldMoveSuccessfulAssignmentRunToAwaitingEvidence() {
        LearningAssignmentRepository assignments = mock(LearningAssignmentRepository.class);
        RunRepository runs = mock(RunRepository.class);
        AssessmentAttemptRepository attempts = mock(AssessmentAttemptRepository.class);
        LearningAssignmentNotificationService notifications = mock(LearningAssignmentNotificationService.class);
        LearningAssignment assignment = assignment();
        assignment.accept("profile-1", "goal-1", Instant.now());
        Run run = run(assignment);
        when(assignments.findByStatusInOrderByUpdatedAtAsc(any(), any())).thenReturn(List.of(assignment));
        when(runs.findTopByTenantIdAndUserIdAndEducationLearningAssignmentIdOrderByCreatedAtDesc(
                "tenant-a", "student-1", assignment.getId())).thenReturn(Optional.of(run));
        when(attempts.existsByTenantIdAndUserIdAndRunIdAndAssessmentType(
                "tenant-a", "student-1", run.getId(), AssessmentAttemptType.FORMATIVE))
                .thenReturn(false);
        when(assignments.save(any(LearningAssignment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LearningAssignmentReconciliationService service = new LearningAssignmentReconciliationService(
                assignments, runs, attempts, notifications);
        assertEquals(1, service.reconcile(Instant.now(), 100));
        assertEquals(LearningAssignmentStatus.AWAITING_EVIDENCE, assignment.getStatus());
        verify(notifications).ensureForEvidenceRequired(assignment, run.getId());
    }

    @Test
    void shouldLeaveAssignmentActiveWhenTheRunAlreadyHasFormativeEvidence() {
        LearningAssignmentRepository assignments = mock(LearningAssignmentRepository.class);
        RunRepository runs = mock(RunRepository.class);
        AssessmentAttemptRepository attempts = mock(AssessmentAttemptRepository.class);
        LearningAssignmentNotificationService notifications = mock(LearningAssignmentNotificationService.class);
        LearningAssignment assignment = assignment();
        assignment.accept("profile-1", "goal-1", Instant.now());
        Run run = run(assignment);
        when(assignments.findByStatusInOrderByUpdatedAtAsc(any(), any())).thenReturn(List.of(assignment));
        when(runs.findTopByTenantIdAndUserIdAndEducationLearningAssignmentIdOrderByCreatedAtDesc(
                "tenant-a", "student-1", assignment.getId())).thenReturn(Optional.of(run));
        when(attempts.existsByTenantIdAndUserIdAndRunIdAndAssessmentType(
                "tenant-a", "student-1", run.getId(), AssessmentAttemptType.FORMATIVE))
                .thenReturn(true);

        LearningAssignmentReconciliationService service = new LearningAssignmentReconciliationService(
                assignments, runs, attempts, notifications);
        assertEquals(0, service.reconcile(Instant.now(), 100));
        assertEquals(LearningAssignmentStatus.ACCEPTED, assignment.getStatus());
    }

    @Test
    void shouldReturnFailedAssignmentRunToRetryableState() {
        LearningAssignmentRepository assignments = mock(LearningAssignmentRepository.class);
        RunRepository runs = mock(RunRepository.class);
        AssessmentAttemptRepository attempts = mock(AssessmentAttemptRepository.class);
        LearningAssignmentNotificationService notifications = mock(LearningAssignmentNotificationService.class);
        LearningAssignment assignment = assignment();
        assignment.accept("profile-1", "goal-1", Instant.now());
        Run run = failedRun(assignment);
        when(assignments.findByStatusInOrderByUpdatedAtAsc(any(), any())).thenReturn(List.of(assignment));
        when(runs.findTopByTenantIdAndUserIdAndEducationLearningAssignmentIdOrderByCreatedAtDesc(
                "tenant-a", "student-1", assignment.getId())).thenReturn(Optional.of(run));
        when(assignments.save(any(LearningAssignment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LearningAssignmentReconciliationService service = new LearningAssignmentReconciliationService(
                assignments, runs, attempts, notifications);
        assertEquals(1, service.reconcile(Instant.now(), 100));
        assertEquals(LearningAssignmentStatus.RETRY_REQUIRED, assignment.getStatus());
        verify(notifications).ensureForRetryRequired(assignment, run.getId(), "模型调用失败");
    }

    @Test
    void shouldReconcileTerminalRunImmediatelyWithoutWaitingForScheduler() {
        LearningAssignmentRepository assignments = mock(LearningAssignmentRepository.class);
        RunRepository runs = mock(RunRepository.class);
        AssessmentAttemptRepository attempts = mock(AssessmentAttemptRepository.class);
        LearningAssignmentNotificationService notifications = mock(LearningAssignmentNotificationService.class);
        LearningAssignment assignment = assignment();
        assignment.accept("profile-1", "goal-1", Instant.now());
        Run run = failedRun(assignment);
        when(assignments.findByTenantIdAndId("tenant-a", assignment.getId()))
                .thenReturn(Optional.of(assignment));
        when(assignments.save(any(LearningAssignment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LearningAssignmentReconciliationService service = new LearningAssignmentReconciliationService(
                assignments, runs, attempts, notifications);
        assertEquals(1, service.reconcileRun(run));
        assertEquals(LearningAssignmentStatus.RETRY_REQUIRED, assignment.getStatus());
        verify(notifications).ensureForRetryRequired(assignment, run.getId(), "模型调用失败");
    }

    private LearningAssignment assignment() {
        return new LearningAssignment("tenant-a", "teacher-1", "student-1", "函数作业",
                "完成练习", "数学", "高中一年级", "人教A版", "函数", 0.8,
                Instant.now().plusSeconds(3600));
    }

    private Run run(LearningAssignment assignment) {
        Run run = new Run("tenant-a", "student-1", "函数作业", "完成练习", BigDecimal.ONE,
                "demo-model", "prompt-v1", "policy-v1", null,
                "education.read,education.write", true, 4);
        run.attachEducationConfiguration(new EducationRunConfiguration(true, "profile-1", "goal-1",
                assignment.getId(), assignment.getTitle(), assignment.getInstructions(), null,
                "函数目标", 0.2, 0.8, "数学", "高中一年级", "人教A版", "函数",
                null, null, "PRACTICE", "函数=0.20"));
        run.start();
        run.succeed("完成");
        return run;
    }

    private Run failedRun(LearningAssignment assignment) {
        Run run = new Run("tenant-a", "student-1", "函数作业", "完成练习", BigDecimal.ONE,
                "demo-model", "prompt-v1", "policy-v1", null,
                "education.read,education.write", true, 4);
        run.attachEducationConfiguration(new EducationRunConfiguration(true, "profile-1", "goal-1",
                assignment.getId(), assignment.getTitle(), assignment.getInstructions(), null,
                "函数目标", 0.2, 0.8, "数学", "高中一年级", "人教A版", "函数",
                null, null, "PRACTICE", "函数=0.20"));
        run.start();
        run.fail("模型调用失败");
        return run;
    }
}
