package org.mingharness.education;

import org.mingharness.education.api.LearningAssignmentProgressView;
import org.mingharness.runtime.repository.RunRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** 将作业绑定的学习目标、测评和任务汇总为教师可行动的进度反馈。 */
@Service
public class LearningAssignmentProgressService {

    private final LearningAssignmentService assignmentService;
    private final LearningGoalRepository goalRepository;
    private final LearnerMasteryRepository masteryRepository;
    private final LearningTaskRepository taskRepository;
    private final AssessmentAttemptRepository assessmentRepository;
    private final LearningAssignmentFeedbackRepository feedbackRepository;
    private final RunRepository runRepository;

    /** 保留旧构造器，兼容已有组件测试和扩展调用方。 */
    public LearningAssignmentProgressService(LearningAssignmentService assignmentService,
                                             LearningGoalRepository goalRepository,
                                             LearnerMasteryRepository masteryRepository,
                                             LearningTaskRepository taskRepository,
                                             AssessmentAttemptRepository assessmentRepository) {
        this(assignmentService, goalRepository, masteryRepository, taskRepository, assessmentRepository,
                null, null);
    }

    @Autowired
    public LearningAssignmentProgressService(LearningAssignmentService assignmentService,
                                             LearningGoalRepository goalRepository,
                                             LearnerMasteryRepository masteryRepository,
                                             LearningTaskRepository taskRepository,
                                             AssessmentAttemptRepository assessmentRepository,
                                             LearningAssignmentFeedbackRepository feedbackRepository,
                                             RunRepository runRepository) {
        this.assignmentService = assignmentService;
        this.goalRepository = goalRepository;
        this.masteryRepository = masteryRepository;
        this.taskRepository = taskRepository;
        this.assessmentRepository = assessmentRepository;
        this.feedbackRepository = feedbackRepository;
        this.runRepository = runRepository;
    }

    @Transactional
    public LearningAssignmentProgressView get(String tenantId, String userId, String assignmentId) {
        LearningAssignment assignment = assignmentService.getForParticipant(tenantId, userId, assignmentId);
        return getForAssignment(tenantId, assignment);
    }

    /** 管理员治理页只读查看同租户作业进度。 */
    @Transactional
    public LearningAssignmentProgressView getForGovernance(String tenantId, String assignmentId) {
        return getForAssignment(tenantId, assignmentService.getForGovernance(tenantId, assignmentId));
    }

    private LearningAssignmentProgressView getForAssignment(String tenantId, LearningAssignment assignment) {
        if (assignment.getLearningGoalId() == null) {
            return new LearningAssignmentProgressView(
                    assignment.getId(), assignment.getTitle(), assignment.getTeacherUserId(),
                    assignment.getLearnerUserId(), assignment.getStatus().name(), assignment.getDueAt(),
                    null, 0.0, 0.0, assignment.getTargetMastery(), 0.0,
                    0, 0, null, 0, 0, 0, 0, 0,
                    0.0, 0, 0, 0.0, 0, 0, 0.0, 0, 0, null);
        }

        LearningGoal goal = goalRepository.findByIdAndTenantIdAndUserId(
                        assignment.getLearningGoalId(), tenantId, assignment.getLearnerUserId())
                .orElseThrow(() -> new IllegalStateException("课程作业绑定的学习目标不存在"));
        double currentMastery = masteryRepository
                .findByTenantIdAndLearnerProfileIdAndConceptKey(
                        tenantId, goal.getLearnerProfileId(), goal.getConceptKey())
                .map(LearnerMastery::getMasteryScore)
                .orElse(goal.getBaselineMastery());
        List<LearningTask> tasks = taskRepository
                .findByTenantIdAndUserIdAndLearningGoalIdOrderByScheduledAtAsc(
                        tenantId, assignment.getLearnerUserId(), goal.getId());
        List<AssessmentAttempt> assessments = assessmentRepository
                .findByTenantIdAndUserIdAndLearningGoalIdOrderByCreatedAtAsc(
                        tenantId, assignment.getLearnerUserId(), goal.getId());
        long taskStarted = tasks.stream().filter(task -> task.getStartedAt() != null).count();
        long taskCompleted = tasks.stream().filter(task -> task.getCompletedAt() != null).count();
        long taskAwaitingEvidence = tasks.stream()
                .filter(task -> task.getStatus() == LearningTaskStatus.AWAITING_EVIDENCE).count();
        long taskFailed = tasks.stream()
                .filter(task -> task.getStatus() == LearningTaskStatus.FAILED).count();
        long correctAssessments = assessments.stream().filter(AssessmentAttempt::isCorrect).count();
        Instant lastAssessmentAt = assessments.isEmpty()
                ? null : assessments.get(assessments.size() - 1).getCreatedAt();
        long runTotal = runRepository == null ? 0 : runRepository
                .countByTenantIdAndUserIdAndEducationLearningAssignmentId(
                        tenantId, assignment.getLearnerUserId(), assignment.getId());
        long runWithAssessmentEvidence = assessments.stream()
                .map(AssessmentAttempt::getRunId).filter(java.util.Objects::nonNull).distinct().count();
        List<LearningAssignmentFeedback> feedbacks = feedbackRepository == null
                ? List.of()
                : feedbackRepository.findByTenantIdAndLearningAssignmentIdOrderByCreatedAtDesc(
                        tenantId, assignment.getId(), PageRequest.of(0, 100));
        long feedbackAcknowledged = feedbacks.stream()
                .filter(item -> item.getAcknowledgedAt() != null).count();
        long feedbackEvidenceRequests = feedbacks.stream()
                .filter(item -> item.getAction() == LearningAssignmentFeedbackAction.REQUEST_EVIDENCE).count();
        long feedbackRetryRecommendations = feedbacks.stream()
                .filter(item -> item.getAction() == LearningAssignmentFeedbackAction.RECOMMEND_RETRY).count();
        Instant lastFeedbackAt = feedbacks.isEmpty() ? null : feedbacks.get(0).getCreatedAt();

        return new LearningAssignmentProgressView(
                assignment.getId(), assignment.getTitle(), assignment.getTeacherUserId(),
                assignment.getLearnerUserId(), assignment.getStatus().name(), assignment.getDueAt(),
                goal.getId(), goal.getBaselineMastery(), currentMastery, goal.getTargetMastery(),
                progress(currentMastery, goal.getBaselineMastery(), goal.getTargetMastery()),
                assessments.size(), correctAssessments, lastAssessmentAt,
                tasks.size(), taskStarted, taskCompleted, taskAwaitingEvidence, taskFailed,
                currentMastery - goal.getBaselineMastery(), runTotal, runWithAssessmentEvidence,
                ratio(runWithAssessmentEvidence, runTotal), feedbacks.size(), feedbackAcknowledged,
                ratio(feedbackAcknowledged, feedbacks.size()), feedbackEvidenceRequests,
                feedbackRetryRecommendations, lastFeedbackAt);
    }

    private double progress(double current, double baseline, double target) {
        if (target <= baseline) return current >= target ? 1.0 : 0.0;
        return Math.max(0.0, Math.min(1.0, (current - baseline) / (target - baseline)));
    }

    private double ratio(long numerator, long denominator) {
        return denominator <= 0 ? 0.0 : (double) numerator / denominator;
    }
}
