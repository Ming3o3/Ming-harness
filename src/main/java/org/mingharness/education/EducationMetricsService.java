package org.mingharness.education;

import org.mingharness.education.api.EducationMetricsView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 汇总作业、任务、测评和触达事实，给业务闭环提供可验证的结果视图。 */
@Service
public class EducationMetricsService {

    private final LearningAssignmentRepository assignmentRepository;
    private final LearningTaskRepository taskRepository;
    private final LearningTaskNotificationRepository notificationRepository;
    private final LearningAssignmentNotificationRepository assignmentNotificationRepository;
    private final AssessmentAttemptRepository assessmentRepository;

    public EducationMetricsService(LearningAssignmentRepository assignmentRepository,
                                   LearningTaskRepository taskRepository,
                                   LearningTaskNotificationRepository notificationRepository,
                                   AssessmentAttemptRepository assessmentRepository) {
        this(assignmentRepository, taskRepository, notificationRepository, null, assessmentRepository);
    }

    @Autowired
    public EducationMetricsService(LearningAssignmentRepository assignmentRepository,
                                   LearningTaskRepository taskRepository,
                                   LearningTaskNotificationRepository notificationRepository,
                                   LearningAssignmentNotificationRepository assignmentNotificationRepository,
                                   AssessmentAttemptRepository assessmentRepository) {
        this.assignmentRepository = assignmentRepository;
        this.taskRepository = taskRepository;
        this.notificationRepository = notificationRepository;
        this.assignmentNotificationRepository = assignmentNotificationRepository;
        this.assessmentRepository = assessmentRepository;
    }

    @Transactional(readOnly = true)
    public EducationMetricsView summarize(String tenantId, String userId) {
        long assignmentTotal = assignmentRepository.countForParticipant(tenantId, userId);
        long assignmentAccepted = assignmentRepository.countForParticipantByStatus(
                tenantId, userId, LearningAssignmentStatus.ACCEPTED)
                + assignmentRepository.countForParticipantByStatus(
                tenantId, userId, LearningAssignmentStatus.OVERDUE)
                + assignmentRepository.countForParticipantByStatus(
                tenantId, userId, LearningAssignmentStatus.COMPLETED);
        long assignmentCompleted = assignmentRepository.countForParticipantByStatus(
                tenantId, userId, LearningAssignmentStatus.COMPLETED);

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
        long taskEvidenceCovered = taskRepository.countStartedWithAssessmentEvidence(tenantId, userId);

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

        return new EducationMetricsView(
                assignmentTotal, assignmentAccepted, assignmentCompleted,
                ratio(assignmentAccepted, assignmentTotal), ratio(assignmentCompleted, assignmentTotal),
                taskTotal, taskStarted, taskCompleted, taskAwaitingEvidence, taskFailed, taskRetryCount,
                ratio(taskStarted, taskTotal), ratio(taskCompleted, taskTotal), taskEvidenceCovered,
                ratio(taskEvidenceCovered, taskStarted),
                notificationTotal, notificationSeen, notificationRead,
                ratio(notificationRead, notificationTotal),
                assessmentTotal, formativeAssessmentTotal, reviewAssessmentTotal,
                correctAssessmentTotal, ratio(correctAssessmentTotal, assessmentTotal));
    }

    private double ratio(long numerator, long denominator) {
        return denominator <= 0 ? 0.0 : (double) numerator / denominator;
    }
}
