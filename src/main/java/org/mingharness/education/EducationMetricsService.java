package org.mingharness.education;

import org.mingharness.education.api.EducationMetricsView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 汇总作业、任务、测评和触达事实，给业务闭环提供可验证的结果视图。 */
@Service
public class EducationMetricsService {

    private final LearningAssignmentRepository assignmentRepository;
    private final LearningTaskRepository taskRepository;
    private final LearningTaskNotificationRepository notificationRepository;
    private final LearningAssignmentNotificationRepository assignmentNotificationRepository;
    private final LearningAssignmentFeedbackRepository feedbackRepository;
    private final AssessmentAttemptRepository assessmentRepository;

    public EducationMetricsService(LearningAssignmentRepository assignmentRepository,
                                   LearningTaskRepository taskRepository,
                                   LearningTaskNotificationRepository notificationRepository,
                                   AssessmentAttemptRepository assessmentRepository) {
        this(assignmentRepository, taskRepository, notificationRepository, null, null, assessmentRepository);
    }

    /** 兼容已有组件测试和旧扩展调用方。 */
    public EducationMetricsService(LearningAssignmentRepository assignmentRepository,
                                   LearningTaskRepository taskRepository,
                                   LearningTaskNotificationRepository notificationRepository,
                                   LearningAssignmentNotificationRepository assignmentNotificationRepository,
                                   AssessmentAttemptRepository assessmentRepository) {
        this(assignmentRepository, taskRepository, notificationRepository,
                assignmentNotificationRepository, null, assessmentRepository);
    }

    @Autowired
    public EducationMetricsService(LearningAssignmentRepository assignmentRepository,
                                   LearningTaskRepository taskRepository,
                                   LearningTaskNotificationRepository notificationRepository,
                                   LearningAssignmentNotificationRepository assignmentNotificationRepository,
                                   LearningAssignmentFeedbackRepository feedbackRepository,
                                   AssessmentAttemptRepository assessmentRepository) {
        this.assignmentRepository = assignmentRepository;
        this.taskRepository = taskRepository;
        this.notificationRepository = notificationRepository;
        this.assignmentNotificationRepository = assignmentNotificationRepository;
        this.feedbackRepository = feedbackRepository;
        this.assessmentRepository = assessmentRepository;
    }

    @Transactional(readOnly = true)
    public EducationMetricsView summarize(String tenantId, String userId) {
        long assignmentTotal = assignmentRepository.countForParticipant(tenantId, userId);
        long assignmentAccepted = assignmentRepository.countForParticipantByStatus(
                tenantId, userId, LearningAssignmentStatus.ACCEPTED)
                + assignmentRepository.countForParticipantByStatus(
                tenantId, userId, LearningAssignmentStatus.AWAITING_EVIDENCE)
                + assignmentRepository.countForParticipantByStatus(
                tenantId, userId, LearningAssignmentStatus.RETRY_REQUIRED)
                + assignmentRepository.countForParticipantByStatus(
                tenantId, userId, LearningAssignmentStatus.OVERDUE)
                + assignmentRepository.countForParticipantByStatus(
                tenantId, userId, LearningAssignmentStatus.COMPLETED);
        long assignmentCompleted = assignmentRepository.countForParticipantByStatus(
                tenantId, userId, LearningAssignmentStatus.COMPLETED);
        long assignmentRetryRequired = assignmentRepository.countForParticipantByStatus(
                tenantId, userId, LearningAssignmentStatus.RETRY_REQUIRED);
        long assignmentReviewPending = assignmentRepository.countForParticipantByReviewStatus(
                tenantId, userId, LearningAssignmentReviewStatus.PENDING);
        long assignmentReviewVerified = assignmentRepository.countForParticipantByReviewStatus(
                tenantId, userId, LearningAssignmentReviewStatus.VERIFIED);
        long assignmentReviewRevisionRequired = assignmentRepository.countForParticipantByReviewStatus(
                tenantId, userId, LearningAssignmentReviewStatus.REVISION_REQUIRED);

        long taskTotal = taskRepository.countByTenantIdAndUserId(tenantId, userId);
        long taskStarted = taskRepository.countByTenantIdAndUserIdAndStartedAtIsNotNull(
                tenantId, userId);
        long taskCompleted = taskRepository.countByTenantIdAndUserIdAndCompletedAtIsNotNull(
                tenantId, userId);
        long taskAwaitingEvidence = taskRepository.countByTenantIdAndUserIdAndStatus(
                tenantId, userId, LearningTaskStatus.AWAITING_EVIDENCE);
        long taskFailed = taskRepository.countByTenantIdAndUserIdAndStatus(
                tenantId, userId, LearningTaskStatus.FAILED);
        long taskRetryCount = taskRepository.sumFailureCount(tenantId, userId);
        long retriedTaskTotal = taskRepository.countRetriedTasks(tenantId, userId);
        long retriedTaskCompleted = taskRepository.countRetriedTasksCompleted(tenantId, userId);
        long taskEvidenceCovered = taskRepository.countStartedWithAssessmentEvidence(tenantId, userId);

        List<LearningAssignmentFeedback> feedbacks = feedbackRepository == null
                ? List.of() : feedbackRepository.findByTenantIdAndParticipantOrderByCreatedAtAsc(tenantId, userId);
        long feedbackTotal = feedbacks.size();
        long feedbackAcknowledged = feedbacks.stream()
                .filter(item -> item.getAcknowledgedAt() != null).count();
        long feedbackResolved = feedbacks.stream()
                .filter(item -> item.getStatus() == LearningAssignmentFeedbackStatus.RESOLVED).count();
        long feedbackAcknowledgementLatencySeconds = averageAcknowledgementLatencySeconds(feedbacks);

        long notificationTotal = notificationRepository.countByTenantIdAndUserId(tenantId, userId);
        long notificationSeen = notificationRepository.countByTenantIdAndUserIdAndSeenAtIsNotNull(
                tenantId, userId);
        long notificationRead = notificationRepository.countByTenantIdAndUserIdAndReadAtIsNotNull(
                tenantId, userId);
        if (assignmentNotificationRepository != null) {
            notificationTotal += assignmentNotificationRepository.countByTenantIdAndUserId(tenantId, userId);
            notificationSeen += assignmentNotificationRepository.countByTenantIdAndUserIdAndSeenAtIsNotNull(
                    tenantId, userId);
            notificationRead += assignmentNotificationRepository.countByTenantIdAndUserIdAndReadAtIsNotNull(
                    tenantId, userId);
        }

        long assessmentTotal = assessmentRepository.countByTenantIdAndUserId(tenantId, userId);
        long formativeAssessmentTotal = assessmentRepository.countByTenantIdAndUserIdAndAssessmentType(
                tenantId, userId, AssessmentAttemptType.FORMATIVE);
        long reviewAssessmentTotal = assessmentRepository.countByTenantIdAndUserIdAndAssessmentType(
                tenantId, userId, AssessmentAttemptType.REVIEW);
        long correctAssessmentTotal = assessmentRepository.countByTenantIdAndUserIdAndCorrectTrue(
                tenantId, userId);
        long correctReviewAssessmentTotal = assessmentRepository
                .countByTenantIdAndUserIdAndAssessmentTypeAndCorrectTrue(
                        tenantId, userId, AssessmentAttemptType.REVIEW);
        List<AssessmentAttempt> participantAssessments = assessmentRepository
                .findByTenantIdAndUserIdOrderByCreatedAtAsc(tenantId, userId);
        double averageMasteryGain = averageMasteryGain(
                participantAssessments == null ? List.of() : participantAssessments);

        return new EducationMetricsView(
                assignmentTotal, assignmentAccepted, assignmentCompleted,
                ratio(assignmentAccepted, assignmentTotal), ratio(assignmentCompleted, assignmentTotal),
                taskTotal, taskStarted, taskCompleted, taskAwaitingEvidence, taskFailed, taskRetryCount,
                ratio(taskStarted, taskTotal), ratio(taskCompleted, taskTotal), taskEvidenceCovered,
                ratio(taskEvidenceCovered, taskStarted),
                notificationTotal, notificationSeen, notificationRead,
                ratio(notificationRead, notificationTotal),
                assessmentTotal, formativeAssessmentTotal, reviewAssessmentTotal,
                correctAssessmentTotal, ratio(correctAssessmentTotal, assessmentTotal),
                assignmentReviewPending, assignmentReviewVerified, assignmentReviewRevisionRequired,
                ratio(assignmentReviewVerified,
                        assignmentReviewPending + assignmentReviewRevisionRequired + assignmentReviewVerified),
                feedbackTotal, feedbackAcknowledged, ratio(feedbackAcknowledged, feedbackTotal),
                feedbackResolved, ratio(feedbackResolved, feedbackTotal),
                feedbackAcknowledgementLatencySeconds, retriedTaskTotal, retriedTaskCompleted,
                ratio(retriedTaskCompleted, retriedTaskTotal), averageMasteryGain,
                ratio(correctReviewAssessmentTotal, reviewAssessmentTotal), assignmentRetryRequired);
    }

    private long averageAcknowledgementLatencySeconds(List<LearningAssignmentFeedback> feedbacks) {
        List<Long> latencies = feedbacks.stream()
                .filter(item -> item.getAcknowledgedAt() != null)
                .map(item -> Duration.between(item.getCreatedAt(), item.getAcknowledgedAt()).getSeconds())
                .filter(value -> value >= 0)
                .toList();
        return latencies.isEmpty() ? 0L
                : Math.round(latencies.stream().mapToLong(Long::longValue).average().orElse(0.0));
    }

    private double averageMasteryGain(List<AssessmentAttempt> attempts) {
        Map<String, double[]> perGoal = new HashMap<>();
        for (AssessmentAttempt attempt : attempts) {
            double[] values = perGoal.computeIfAbsent(attempt.getLearningGoalId(), key ->
                    new double[]{attempt.getMasteryBefore(), attempt.getMasteryAfter()});
            values[1] = attempt.getMasteryAfter();
        }
        return perGoal.values().stream()
                .mapToDouble(values -> values[1] - values[0])
                .average().orElse(0.0);
    }

    private double ratio(long numerator, long denominator) {
        return denominator <= 0 ? 0.0 : (double) numerator / denominator;
    }
}
