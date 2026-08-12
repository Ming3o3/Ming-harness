package org.mingharness.education;

import org.mingharness.runtime.domain.Run;
import org.mingharness.runtime.domain.RunStatus;
import org.mingharness.runtime.repository.RunRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** 将初始课程作业 Run 的终态收敛为“待补证据”，避免成功但无测评的作业假装闭环。 */
@Service
public class LearningAssignmentReconciliationService {

    private final LearningAssignmentRepository assignmentRepository;
    private final RunRepository runRepository;
    private final AssessmentAttemptRepository assessmentRepository;
    private final LearningAssignmentNotificationService notificationService;

    public LearningAssignmentReconciliationService(
            LearningAssignmentRepository assignmentRepository,
            RunRepository runRepository,
            AssessmentAttemptRepository assessmentRepository,
            LearningAssignmentNotificationService notificationService) {
        this.assignmentRepository = assignmentRepository;
        this.runRepository = runRepository;
        this.assessmentRepository = assessmentRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public int reconcile(Instant reference, int limit) {
        Instant now = reference == null ? Instant.now() : reference;
        int boundedLimit = Math.max(1, Math.min(500, limit));
        List<LearningAssignment> candidates = assignmentRepository.findByStatusInOrderByUpdatedAtAsc(
                List.of(LearningAssignmentStatus.ACCEPTED, LearningAssignmentStatus.OVERDUE,
                        LearningAssignmentStatus.RETRY_REQUIRED),
                PageRequest.of(0, boundedLimit));
        int changed = 0;
        for (LearningAssignment assignment : candidates) {
            Run run = runRepository
                    .findTopByTenantIdAndUserIdAndEducationLearningAssignmentIdOrderByCreatedAtDesc(
                            assignment.getTenantId(), assignment.getLearnerUserId(), assignment.getId())
                    .orElse(null);
            changed += reconcileCandidate(assignment, run, now);
        }
        return changed;
    }

    /**
     * 将一个已经落库的教育 Run 立即收敛到对应作业状态。
     *
     * <p>定时扫描仍然保留，用来兜底处理跨进程延迟；Run 终态写入路径调用此方法后，
     * 学习者不会在一整轮调度间隔内看到“学习中”而不知道该重试。</p>
     */
    @Transactional
    public int reconcileRun(Run run) {
        if (run == null || !isTerminal(run.getStatus())
                || run.getEducationLearningAssignmentId() == null
                || run.getEducationLearningAssignmentId().isBlank()) return 0;
        LearningAssignment assignment = assignmentRepository.findByTenantIdAndId(
                        run.getTenantId(), run.getEducationLearningAssignmentId())
                .orElse(null);
        if (assignment == null) return 0;
        return reconcileCandidate(assignment, run, Instant.now());
    }

    private int reconcileCandidate(LearningAssignment assignment, Run run, Instant now) {
        if (!isBoundToAssignment(run, assignment)) return 0;
        if (run.getStatus() == RunStatus.FAILED
                || run.getStatus() == RunStatus.TIMED_OUT
                || run.getStatus() == RunStatus.CANCELLED) {
            if (assignment.getStatus() == LearningAssignmentStatus.RETRY_REQUIRED) return 0;
            String reason = run.getError();
            if (reason == null || reason.isBlank()) reason = "Run 状态为 " + run.getStatus();
            assignment.requireRetry(now);
            assignmentRepository.save(assignment);
            notificationService.ensureForRetryRequired(assignment, run.getId(), reason);
            return 1;
        }
        if (run.getStatus() != RunStatus.SUCCEEDED) return 0;
        boolean hasFormativeEvidence = assessmentRepository
                .existsByTenantIdAndUserIdAndRunIdAndAssessmentType(
                        assignment.getTenantId(), assignment.getLearnerUserId(), run.getId(),
                        AssessmentAttemptType.FORMATIVE);
        if (hasFormativeEvidence) return 0;
        assignment.awaitEvidence(now);
        assignmentRepository.save(assignment);
        notificationService.ensureForEvidenceRequired(assignment, run.getId());
        return 1;
    }

    private boolean isTerminal(RunStatus status) {
        return status == RunStatus.SUCCEEDED
                || status == RunStatus.FAILED
                || status == RunStatus.TIMED_OUT
                || status == RunStatus.CANCELLED;
    }

    private boolean isBoundToAssignment(Run run, LearningAssignment assignment) {
        return run != null
                && assignment.getTenantId().equals(run.getTenantId())
                && assignment.getLearnerUserId().equals(run.getUserId())
                && assignment.getId().equals(run.getEducationLearningAssignmentId());
    }
}
