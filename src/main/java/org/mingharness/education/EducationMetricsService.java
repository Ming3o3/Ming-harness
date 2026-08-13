package org.mingharness.education;

import org.mingharness.education.api.EducationMetricsView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 汇总作业、任务、测评和触达事实，给业务闭环提供可验证的结果视图。 */
@Service
public class EducationMetricsService {

    private final LearningAssignmentRepository assignmentRepository;
    private final LearningTaskRepository taskRepository;
    private final LearningTaskNotificationRepository notificationRepository;
    private final LearningAssignmentNotificationRepository assignmentNotificationRepository;
    private final LearningAssignmentFeedbackRepository feedbackRepository;
    private final LearningAssignmentSubmissionRepository submissionRepository;
    private final AssessmentAttemptRepository assessmentRepository;
    private final LearningAssignmentEvaluationRepository evaluationRepository;

    public EducationMetricsService(LearningAssignmentRepository assignmentRepository,
                                   LearningTaskRepository taskRepository,
                                   LearningTaskNotificationRepository notificationRepository,
                                   AssessmentAttemptRepository assessmentRepository) {
        this(assignmentRepository, taskRepository, notificationRepository, null, null, null,
                assessmentRepository, null);
    }

    /** 兼容已有组件测试和旧扩展调用方。 */
    public EducationMetricsService(LearningAssignmentRepository assignmentRepository,
                                   LearningTaskRepository taskRepository,
                                   LearningTaskNotificationRepository notificationRepository,
                                   LearningAssignmentNotificationRepository assignmentNotificationRepository,
                                   AssessmentAttemptRepository assessmentRepository) {
        this(assignmentRepository, taskRepository, notificationRepository,
                assignmentNotificationRepository, null, null, assessmentRepository, null);
    }

    public EducationMetricsService(LearningAssignmentRepository assignmentRepository,
                                   LearningTaskRepository taskRepository,
                                   LearningTaskNotificationRepository notificationRepository,
                                   LearningAssignmentNotificationRepository assignmentNotificationRepository,
                                   LearningAssignmentFeedbackRepository feedbackRepository,
                                   AssessmentAttemptRepository assessmentRepository) {
        this(assignmentRepository, taskRepository, notificationRepository,
                assignmentNotificationRepository, feedbackRepository, null, assessmentRepository, null);
    }

    public EducationMetricsService(LearningAssignmentRepository assignmentRepository,
                                   LearningTaskRepository taskRepository,
                                   LearningTaskNotificationRepository notificationRepository,
                                   LearningAssignmentNotificationRepository assignmentNotificationRepository,
                                   LearningAssignmentFeedbackRepository feedbackRepository,
                                   LearningAssignmentSubmissionRepository submissionRepository,
                                   AssessmentAttemptRepository assessmentRepository) {
        this(assignmentRepository, taskRepository, notificationRepository, assignmentNotificationRepository,
                feedbackRepository, submissionRepository, assessmentRepository, null);
    }

    @Autowired
    public EducationMetricsService(LearningAssignmentRepository assignmentRepository,
                                   LearningTaskRepository taskRepository,
                                   LearningTaskNotificationRepository notificationRepository,
                                   LearningAssignmentNotificationRepository assignmentNotificationRepository,
                                   LearningAssignmentFeedbackRepository feedbackRepository,
                                   LearningAssignmentSubmissionRepository submissionRepository,
                                   AssessmentAttemptRepository assessmentRepository,
                                   LearningAssignmentEvaluationRepository evaluationRepository) {
        this.assignmentRepository = assignmentRepository;
        this.taskRepository = taskRepository;
        this.notificationRepository = notificationRepository;
        this.assignmentNotificationRepository = assignmentNotificationRepository;
        this.feedbackRepository = feedbackRepository;
        this.submissionRepository = submissionRepository;
        this.assessmentRepository = assessmentRepository;
        this.evaluationRepository = evaluationRepository;
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
        long assignmentSubmissionTotal = submissionRepository == null ? 0
                : submissionRepository.countForParticipant(tenantId, userId);
        long assignmentSubmissionCovered = submissionRepository == null ? 0
                : submissionRepository.countCoveredAssignmentsForParticipant(tenantId, userId);
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

        List<LearningAssignmentEvaluation> evaluations = evaluationRepository == null
                ? List.of() : evaluationRepository.findByTenantIdAndParticipantOrderByCreatedAtAsc(
                tenantId, userId);
        if (evaluations == null) evaluations = List.of();
        Set<String> evaluatedAssignmentIds = new HashSet<>();
        for (LearningAssignmentEvaluation evaluation : evaluations) {
            evaluatedAssignmentIds.add(evaluation.getLearningAssignmentId());
        }
        double averageTeacherContentCorrectnessScore = averageTeacherScore(evaluations,
                ScoreDimension.CONTENT_CORRECTNESS);
        double averageTeacherEvidenceQualityScore = averageTeacherScore(evaluations,
                ScoreDimension.EVIDENCE_QUALITY);
        double averageTeacherTransferReadinessScore = averageTeacherScore(evaluations,
                ScoreDimension.TRANSFER_READINESS);

        return new EducationMetricsView(
                assignmentTotal, assignmentAccepted, assignmentCompleted,
                assignmentSubmissionTotal, assignmentSubmissionCovered,
                ratio(assignmentSubmissionCovered, assignmentTotal),
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
                ratio(correctReviewAssessmentTotal, reviewAssessmentTotal), assignmentRetryRequired,
                evaluations.size(), evaluatedAssignmentIds.size(),
                ratio(evaluatedAssignmentIds.size(), assignmentTotal),
                averageTeacherContentCorrectnessScore, averageTeacherEvidenceQualityScore,
                averageTeacherTransferReadinessScore);
    }

    /**
     * 管理员治理页使用组织口径，而不是把管理员自己的 userId 当成教育参与者。
     * 这组指标只读聚合同租户事实，避免管理员看到课程总量却看到一组全为 0 的个人指标。
     */
    @Transactional(readOnly = true)
    public EducationMetricsView summarizeForGovernance(String tenantId) {
        List<LearningAssignment> assignments = assignmentRepository
                .findByTenantIdOrderByCreatedAtDesc(tenantId);
        List<LearningTask> tasks = taskRepository.findByTenantIdOrderByUpdatedAtAsc(tenantId);
        List<LearningTaskNotification> taskNotifications = notificationRepository
                .findByTenantIdOrderByCreatedAtAsc(tenantId);
        List<LearningAssignmentNotification> assignmentNotifications =
                assignmentNotificationRepository == null ? List.of()
                        : assignmentNotificationRepository.findByTenantIdOrderByCreatedAtAsc(tenantId);
        List<LearningAssignmentFeedback> feedbacks = feedbackRepository == null ? List.of()
                : feedbackRepository.findByTenantIdOrderByCreatedAtAsc(tenantId);
        List<LearningAssignmentSubmission> submissions = submissionRepository == null ? List.of()
                : submissionRepository.findByTenantIdOrderBySubmittedAtAsc(tenantId);
        List<AssessmentAttempt> assessments = assessmentRepository.findByTenantIdOrderByCreatedAtAsc(tenantId);
        List<LearningAssignmentEvaluation> evaluations = evaluationRepository == null ? List.of()
                : evaluationRepository.findByTenantIdOrderByCreatedAtAsc(tenantId);

        long assignmentAccepted = assignments.stream().filter(item ->
                item.getStatus() == LearningAssignmentStatus.ACCEPTED
                        || item.getStatus() == LearningAssignmentStatus.AWAITING_EVIDENCE
                        || item.getStatus() == LearningAssignmentStatus.RETRY_REQUIRED
                        || item.getStatus() == LearningAssignmentStatus.OVERDUE
                        || item.getStatus() == LearningAssignmentStatus.COMPLETED).count();
        long assignmentCompleted = assignments.stream()
                .filter(item -> item.getStatus() == LearningAssignmentStatus.COMPLETED).count();
        long assignmentRetryRequired = assignments.stream()
                .filter(item -> item.getStatus() == LearningAssignmentStatus.RETRY_REQUIRED).count();
        Set<String> submittedAssignmentIds = submissions.stream()
                .map(LearningAssignmentSubmission::getLearningAssignmentId)
                .collect(java.util.stream.Collectors.toSet());
        long assignmentReviewPending = assignments.stream()
                .filter(item -> item.getReviewStatus() == LearningAssignmentReviewStatus.PENDING).count();
        long assignmentReviewVerified = assignments.stream()
                .filter(item -> item.getReviewStatus() == LearningAssignmentReviewStatus.VERIFIED).count();
        long assignmentReviewRevisionRequired = assignments.stream()
                .filter(item -> item.getReviewStatus() == LearningAssignmentReviewStatus.REVISION_REQUIRED).count();

        long taskStarted = tasks.stream().filter(item -> item.getStartedAt() != null).count();
        long taskCompleted = tasks.stream().filter(item -> item.getCompletedAt() != null).count();
        long taskAwaitingEvidence = tasks.stream()
                .filter(item -> item.getStatus() == LearningTaskStatus.AWAITING_EVIDENCE).count();
        long taskFailed = tasks.stream().filter(item -> item.getStatus() == LearningTaskStatus.FAILED).count();
        long taskRetryCount = tasks.stream().mapToLong(LearningTask::getFailureCount).sum();
        long retriedTaskTotal = tasks.stream().filter(item -> item.getFailureCount() > 0).count();
        long retriedTaskCompleted = tasks.stream()
                .filter(item -> item.getFailureCount() > 0 && item.getCompletedAt() != null).count();
        long taskEvidenceCovered = tasks.stream()
                .filter(item -> item.getStartedAt() != null && item.getRunId() != null)
                .filter(task -> assessments.stream().anyMatch(attempt ->
                        tenantId.equals(attempt.getTenantId())
                                && task.getUserId().equals(attempt.getUserId())
                                && task.getRunId().equals(attempt.getRunId())))
                .count();

        long notificationSeen = taskNotifications.stream().filter(item -> item.getSeenAt() != null).count()
                + assignmentNotifications.stream().filter(item -> item.getSeenAt() != null).count();
        long notificationRead = taskNotifications.stream().filter(item -> item.getReadAt() != null).count()
                + assignmentNotifications.stream().filter(item -> item.getReadAt() != null).count();
        long notificationTotal = taskNotifications.size() + assignmentNotifications.size();
        long assessmentTotal = assessments.size();
        long formativeAssessmentTotal = assessments.stream()
                .filter(item -> item.getAssessmentType() == AssessmentAttemptType.FORMATIVE).count();
        long reviewAssessmentTotal = assessments.stream()
                .filter(item -> item.getAssessmentType() == AssessmentAttemptType.REVIEW).count();
        long correctAssessmentTotal = assessments.stream().filter(AssessmentAttempt::isCorrect).count();
        long correctReviewAssessmentTotal = assessments.stream()
                .filter(item -> item.getAssessmentType() == AssessmentAttemptType.REVIEW)
                .filter(AssessmentAttempt::isCorrect).count();
        long feedbackAcknowledged = feedbacks.stream()
                .filter(item -> item.getAcknowledgedAt() != null).count();
        long feedbackResolved = feedbacks.stream()
                .filter(item -> item.getStatus() == LearningAssignmentFeedbackStatus.RESOLVED).count();
        Set<String> evaluatedAssignmentIds = evaluations.stream()
                .map(LearningAssignmentEvaluation::getLearningAssignmentId)
                .collect(java.util.stream.Collectors.toSet());

        return new EducationMetricsView(
                assignments.size(), assignmentAccepted, assignmentCompleted,
                submissions.size(), submittedAssignmentIds.size(),
                ratio(submittedAssignmentIds.size(), assignments.size()),
                ratio(assignmentAccepted, assignments.size()), ratio(assignmentCompleted, assignments.size()),
                tasks.size(), taskStarted, taskCompleted, taskAwaitingEvidence, taskFailed, taskRetryCount,
                ratio(taskStarted, tasks.size()), ratio(taskCompleted, tasks.size()), taskEvidenceCovered,
                ratio(taskEvidenceCovered, taskStarted), notificationTotal, notificationSeen, notificationRead,
                ratio(notificationRead, notificationTotal), assessmentTotal, formativeAssessmentTotal,
                reviewAssessmentTotal, correctAssessmentTotal, ratio(correctAssessmentTotal, assessmentTotal),
                assignmentReviewPending, assignmentReviewVerified, assignmentReviewRevisionRequired,
                ratio(assignmentReviewVerified,
                        assignmentReviewPending + assignmentReviewRevisionRequired + assignmentReviewVerified),
                feedbacks.size(), feedbackAcknowledged, ratio(feedbackAcknowledged, feedbacks.size()),
                feedbackResolved, ratio(feedbackResolved, feedbacks.size()),
                averageAcknowledgementLatencySeconds(feedbacks), retriedTaskTotal, retriedTaskCompleted,
                ratio(retriedTaskCompleted, retriedTaskTotal), averageMasteryGain(assessments),
                ratio(correctReviewAssessmentTotal, reviewAssessmentTotal), assignmentRetryRequired,
                evaluations.size(), evaluatedAssignmentIds.size(), ratio(evaluatedAssignmentIds.size(), assignments.size()),
                averageTeacherScore(evaluations, ScoreDimension.CONTENT_CORRECTNESS),
                averageTeacherScore(evaluations, ScoreDimension.EVIDENCE_QUALITY),
                averageTeacherScore(evaluations, ScoreDimension.TRANSFER_READINESS));
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

    private double averageTeacherScore(List<LearningAssignmentEvaluation> evaluations,
                                       ScoreDimension dimension) {
        return evaluations.stream().mapToInt(evaluation -> switch (dimension) {
            case CONTENT_CORRECTNESS -> evaluation.getContentCorrectnessScore();
            case EVIDENCE_QUALITY -> evaluation.getEvidenceQualityScore();
            case TRANSFER_READINESS -> evaluation.getTransferReadinessScore();
        }).average().orElse(0.0);
    }

    private enum ScoreDimension {
        CONTENT_CORRECTNESS,
        EVIDENCE_QUALITY,
        TRANSFER_READINESS
    }

    private double ratio(long numerator, long denominator) {
        return denominator <= 0 ? 0.0 : (double) numerator / denominator;
    }
}
