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
                List.of(LearningAssignmentStatus.ACCEPTED, LearningAssignmentStatus.OVERDUE),
                PageRequest.of(0, boundedLimit));
        int changed = 0;
        for (LearningAssignment assignment : candidates) {
            Run run = runRepository
                    .findTopByTenantIdAndUserIdAndEducationLearningAssignmentIdOrderByCreatedAtDesc(
                            assignment.getTenantId(), assignment.getLearnerUserId(), assignment.getId())
                    .orElse(null);
            if (!isBoundToAssignment(run, assignment) || run.getStatus() != RunStatus.SUCCEEDED) continue;
            boolean hasFormativeEvidence = assessmentRepository
                    .existsByTenantIdAndUserIdAndRunIdAndAssessmentType(
                            assignment.getTenantId(), assignment.getLearnerUserId(), run.getId(),
                            AssessmentAttemptType.FORMATIVE);
            if (hasFormativeEvidence) continue;
            assignment.awaitEvidence(now);
            assignmentRepository.save(assignment);
            notificationService.ensureForEvidenceRequired(assignment, run.getId());
            changed++;
        }
        return changed;
    }

    private boolean isBoundToAssignment(Run run, LearningAssignment assignment) {
        return run != null
                && assignment.getTenantId().equals(run.getTenantId())
                && assignment.getLearnerUserId().equals(run.getUserId())
                && assignment.getId().equals(run.getEducationLearningAssignmentId());
    }
}
