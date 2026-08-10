package org.mingharness.education;

import org.mingharness.education.api.LearningAssignmentProgressView;
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

    public LearningAssignmentProgressService(LearningAssignmentService assignmentService,
                                             LearningGoalRepository goalRepository,
                                             LearnerMasteryRepository masteryRepository,
                                             LearningTaskRepository taskRepository,
                                             AssessmentAttemptRepository assessmentRepository) {
        this.assignmentService = assignmentService;
        this.goalRepository = goalRepository;
        this.masteryRepository = masteryRepository;
        this.taskRepository = taskRepository;
        this.assessmentRepository = assessmentRepository;
    }

    @Transactional
    public LearningAssignmentProgressView get(String tenantId, String userId, String assignmentId) {
        LearningAssignment assignment = assignmentService.getForParticipant(tenantId, userId, assignmentId);
        if (assignment.getLearningGoalId() == null) {
            return new LearningAssignmentProgressView(
                    assignment.getId(), assignment.getTitle(), assignment.getTeacherUserId(),
                    assignment.getLearnerUserId(), assignment.getStatus().name(), assignment.getDueAt(),
                    null, 0.0, 0.0, assignment.getTargetMastery(), 0.0,
                    0, 0, null, 0, 0, 0, 0, 0);
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

        return new LearningAssignmentProgressView(
                assignment.getId(), assignment.getTitle(), assignment.getTeacherUserId(),
                assignment.getLearnerUserId(), assignment.getStatus().name(), assignment.getDueAt(),
                goal.getId(), goal.getBaselineMastery(), currentMastery, goal.getTargetMastery(),
                progress(currentMastery, goal.getBaselineMastery(), goal.getTargetMastery()),
                assessments.size(), correctAssessments, lastAssessmentAt,
                tasks.size(), taskStarted, taskCompleted, taskAwaitingEvidence, taskFailed);
    }

    private double progress(double current, double baseline, double target) {
        if (target <= baseline) return current >= target ? 1.0 : 0.0;
        return Math.max(0.0, Math.min(1.0, (current - baseline) / (target - baseline)));
    }
}
