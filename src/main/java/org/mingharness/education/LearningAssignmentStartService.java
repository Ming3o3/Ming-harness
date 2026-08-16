package org.mingharness.education;

import org.mingharness.common.BusinessException;
import org.mingharness.conversation.api.ConversationDetail;
import org.mingharness.education.api.ExecuteLearningActionRequest;
import org.mingharness.education.api.LearningAssignmentStartView;
import org.mingharness.education.api.LearningAssignmentView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


/** 将课程作业入口直接连接到第一步教育 Run，避免接受作业后还要绕行通用 Run 页面。 */
@Service
public class LearningAssignmentStartService {

    private final LearningAssignmentService assignmentService;
    private final EducationActionService actionService;
    private final LearningAssignmentFeedbackRepository feedbackRepository;

    public LearningAssignmentStartService(LearningAssignmentService assignmentService,
                                          EducationActionService actionService) {
        this(assignmentService, actionService, null, null);
    }

    public LearningAssignmentStartService(LearningAssignmentService assignmentService,
                                          EducationActionService actionService,
                                          LearningAssignmentFeedbackRepository feedbackRepository) {
        this(assignmentService, actionService, feedbackRepository, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public LearningAssignmentStartService(LearningAssignmentService assignmentService,
                                          EducationActionService actionService,
                                          LearningAssignmentFeedbackRepository feedbackRepository,
                                          LearningAssignmentFeedbackService feedbackService) {
        this.assignmentService = assignmentService;
        this.actionService = actionService;
        this.feedbackRepository = feedbackRepository;
    }

    @Transactional
    public LearningAssignmentStartView start(String tenantId, String learnerUserId,
                                             String assignmentId,
                                             ExecuteLearningActionRequest request,
                                             String permissions, String idempotencyKey) {
        LearningAssignment assignment = assignmentService.getForParticipant(
                tenantId, learnerUserId, assignmentId);
        if (!learnerUserId.equals(assignment.getLearnerUserId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "LEARNING_ASSIGNMENT_LEARNER_ONLY",
                    "只有被布置作业的学习者可以开始作业");
        }
        if (assignment.getStatus() == LearningAssignmentStatus.CANCELLED
                || assignment.getStatus() == LearningAssignmentStatus.COMPLETED) {
            throw new BusinessException(HttpStatus.CONFLICT, "LEARNING_ASSIGNMENT_NOT_STARTABLE",
                    "当前课程作业不能开始新的学习 Run");
        }
        if (assignment.getStatus() == LearningAssignmentStatus.OVERDUE
                && !hasOpenRetryIntervention(tenantId, assignment)) {
            throw new BusinessException(HttpStatus.CONFLICT, "LEARNING_ASSIGNMENT_OVERDUE",
                    "课程作业已逾期，必须先由教师重新安排截止时间或发起明确的重试干预");
        }
        if (assignment.getStatus() == LearningAssignmentStatus.ASSIGNED) {
            assignmentService.accept(tenantId, learnerUserId, assignmentId);
            assignment = assignmentService.getForParticipant(tenantId, learnerUserId, assignmentId);
        }
        if (assignment.getLearningGoalId() == null || assignment.getLearningGoalId().isBlank()) {
            throw new BusinessException(HttpStatus.CONFLICT, "LEARNING_ASSIGNMENT_GOAL_REQUIRED",
                    "课程作业尚未建立学习目标");
        }
        String requestedLanguage = request == null ? null : normalize(request.programmingLanguage());
        String assignmentLanguage = normalize(assignment.getProgrammingLanguage());
        if (requestedLanguage != null && !requestedLanguage.equalsIgnoreCase(assignmentLanguage == null ? "" : assignmentLanguage)) {
            throw new BusinessException(HttpStatus.CONFLICT, "LEARNING_ASSIGNMENT_LANGUAGE_MISMATCH",
                    "作业启动请求中的编程语言不能覆盖教师布置作业时冻结的语言上下文");
        }
        ExecuteLearningActionRequest effectiveRequest = request == null
                ? new ExecuteLearningActionRequest(null, null, null, assignment.getId(), assignment.getCourseId(), assignmentLanguage)
                : new ExecuteLearningActionRequest(request.conversationId(), request.modelName(),
                        request.maxTurns(), assignment.getId(), assignment.getCourseId(), assignmentLanguage);
        ConversationDetail conversation = actionService.execute(
                tenantId, learnerUserId, assignment.getLearningGoalId(), effectiveRequest,
                permissions, idempotencyKey);
        if (assignment.getStatus() == LearningAssignmentStatus.RETRY_REQUIRED) {
            assignment = assignmentService.markRetryStarted(tenantId, learnerUserId, assignment.getId());
        }
        return new LearningAssignmentStartView(
                LearningAssignmentView.from(assignment), assignment.getLearnerProfileId(),
                assignment.getLearningGoalId(), conversation);
    }

    private boolean hasOpenRetryIntervention(String tenantId, LearningAssignment assignment) {
        if (feedbackRepository == null) return false;
        return feedbackRepository.findByTenantIdAndLearningAssignmentIdOrderByCreatedAtDesc(
                        tenantId, assignment.getId(),
                        org.springframework.data.domain.PageRequest.of(0, 100)).stream()
                .anyMatch(feedback -> (feedback.getStatus() == LearningAssignmentFeedbackStatus.OPEN
                        || feedback.getStatus() == LearningAssignmentFeedbackStatus.ACKNOWLEDGED)
                        && (feedback.getAction() == LearningAssignmentFeedbackAction.RECOMMEND_RETRY
                        || feedback.getAction() == LearningAssignmentFeedbackAction.REQUEST_EVIDENCE));
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
