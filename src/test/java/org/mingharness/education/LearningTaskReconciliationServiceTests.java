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

class LearningTaskReconciliationServiceTests {

    @Test
    void shouldMoveSuccessfulRunWithoutEvidenceToAwaitingEvidence() {
        LearningTaskRepository tasks = mock(LearningTaskRepository.class);
        RunRepository runs = mock(RunRepository.class);
        AssessmentAttemptRepository assessments = mock(AssessmentAttemptRepository.class);
        LearningTask task = task("run-1");
        Run run = run("run-1");
        run.start();
        run.succeed("已完成讲解");
        when(tasks.findByStatusInOrderByUpdatedAtAsc(any(), any())).thenReturn(List.of(task));
        when(runs.findById("run-1")).thenReturn(Optional.of(run));
        when(assessments.existsByTenantIdAndUserIdAndRunIdAndAssessmentType(
                "tenant-a", "student-1", "run-1", AssessmentAttemptType.REVIEW)).thenReturn(false);

        LearningTaskReconciliationService service = new LearningTaskReconciliationService(tasks, runs, assessments);
        assertEquals(1, service.reconcile(Instant.now(), 100));
        assertEquals(LearningTaskStatus.AWAITING_EVIDENCE, task.getStatus());
        verify(tasks).save(task);
    }

    @Test
    void shouldMarkFailedRunRetryable() {
        LearningTaskRepository tasks = mock(LearningTaskRepository.class);
        RunRepository runs = mock(RunRepository.class);
        AssessmentAttemptRepository assessments = mock(AssessmentAttemptRepository.class);
        LearningTask task = task("run-2");
        Run run = run("run-2");
        run.start();
        run.fail("模型调用失败");
        when(tasks.findByStatusInOrderByUpdatedAtAsc(any(), any())).thenReturn(List.of(task));
        when(runs.findById("run-2")).thenReturn(Optional.of(run));

        LearningTaskReconciliationService service = new LearningTaskReconciliationService(tasks, runs, assessments);
        assertEquals(1, service.reconcile(Instant.now(), 100));
        assertEquals(LearningTaskStatus.FAILED, task.getStatus());
        assertEquals("模型调用失败", task.getFailureReason());
        verify(tasks).save(task);
    }

    private LearningTask task(String runId) {
        LearningTask task = new LearningTask("tenant-a", "student-1", LearningTaskType.REVIEW,
                "goal-1", "plan-1", 0, "保持度复习", "请完成复习题", Instant.now());
        task.start("conversation-1", runId, Instant.now());
        return task;
    }

    private Run run(String runId) {
        return new Run("tenant-a", "student-1", "复习", "请复习", BigDecimal.ONE,
                "demo-model", "prompt-v1", "policy-v1");
    }
}
