package org.mingharness.education;

import org.mingharness.education.api.AssessmentAttemptView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 将作业绑定目标的测评证据投影给教师和学习者，避免教师只能看到聚合数字。 */
@Service
public class LearningAssignmentEvidenceService {

    private final LearningAssignmentService assignmentService;
    private final AssessmentAttemptRepository assessmentRepository;

    public LearningAssignmentEvidenceService(LearningAssignmentService assignmentService,
                                             AssessmentAttemptRepository assessmentRepository) {
        this.assignmentService = assignmentService;
        this.assessmentRepository = assessmentRepository;
    }

    @Transactional
    public List<AssessmentAttemptView> list(String tenantId, String userId, String assignmentId) {
        LearningAssignment assignment = assignmentService.getForParticipant(tenantId, userId, assignmentId);
        return listForAssignment(tenantId, assignment);
    }

    /** 管理员治理页只读查看同租户作业测评证据。 */
    @Transactional(readOnly = true)
    public List<AssessmentAttemptView> listForGovernance(String tenantId, String assignmentId) {
        return listForAssignment(tenantId, assignmentService.getForGovernance(tenantId, assignmentId));
    }

    private List<AssessmentAttemptView> listForAssignment(String tenantId, LearningAssignment assignment) {
        if (assignment.getLearningGoalId() == null) return List.of();
        List<AssessmentAttempt> attempts = assessmentRepository
                .findByTenantIdAndUserIdAndLearningAssignmentIdOrderByCreatedAtAsc(
                        tenantId, assignment.getLearnerUserId(), assignment.getId());
        // 兼容迁移前的历史证据：旧记录没有作业 ID，只能安全地回退到作业唯一绑定目标。
        if (attempts.isEmpty()) {
            attempts = assessmentRepository
                    .findByTenantIdAndUserIdAndLearningGoalIdOrderByCreatedAtAsc(
                            tenantId, assignment.getLearnerUserId(), assignment.getLearningGoalId());
        }
        return attempts.stream().map(AssessmentAttemptView::from).toList();
    }
}
