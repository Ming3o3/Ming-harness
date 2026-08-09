package org.mingharness.education;

import org.mingharness.runtime.domain.Run;
import org.mingharness.runtime.domain.RunStatus;
import org.mingharness.runtime.repository.RunRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** 将教育 Run 的终态收敛为学习任务的可解释结果。 */
@Service
public class LearningTaskReconciliationService {

    private final LearningTaskRepository taskRepository;
    private final RunRepository runRepository;
    private final AssessmentAttemptRepository assessmentRepository;

    public LearningTaskReconciliationService(LearningTaskRepository taskRepository,
                                             RunRepository runRepository,
                                             AssessmentAttemptRepository assessmentRepository) {
        this.taskRepository = taskRepository;
        this.runRepository = runRepository;
        this.assessmentRepository = assessmentRepository;
    }

    @Transactional
    public int reconcile(Instant reference, int limit) {
        Instant now = reference == null ? Instant.now() : reference;
        List<LearningTask> candidates = taskRepository.findByStatusInOrderByUpdatedAtAsc(
                List.of(LearningTaskStatus.IN_PROGRESS), PageRequest.of(0, Math.max(1, Math.min(500, limit))));
        int changed = 0;
        for (LearningTask task : candidates) {
            if (task.getRunId() == null || task.getRunId().isBlank()) continue;
            Run run = runRepository.findById(task.getRunId()).orElse(null);
            if (run == null || !task.getTenantId().equals(run.getTenantId())
                    || !task.getUserId().equals(run.getUserId())) continue;
            if (run.getStatus() == RunStatus.SUCCEEDED) {
                boolean hasEvidence = assessmentRepository.existsByTenantIdAndUserIdAndRunIdAndAssessmentType(
                        task.getTenantId(), task.getUserId(), task.getRunId(), AssessmentAttemptType.REVIEW);
                if (!hasEvidence) {
                    task.awaitEvidence(now);
                    taskRepository.save(task);
                    changed++;
                }
            } else if (run.getStatus() == RunStatus.FAILED
                    || run.getStatus() == RunStatus.TIMED_OUT
                    || run.getStatus() == RunStatus.CANCELLED) {
                String reason = run.getError();
                if (reason == null || reason.isBlank()) reason = "教育 Run 状态为 " + run.getStatus();
                task.fail(reason, run.getId(), now);
                taskRepository.save(task);
                changed++;
            }
        }
        return changed;
    }
}
