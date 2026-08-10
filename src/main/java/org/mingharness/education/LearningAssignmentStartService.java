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

    public LearningAssignmentStartService(LearningAssignmentService assignmentService,
                                          EducationActionService actionService) {
        this.assignmentService = assignmentService;
        this.actionService = actionService;
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
        if (assignment.getStatus() == LearningAssignmentStatus.ASSIGNED) {
            assignmentService.accept(tenantId, learnerUserId, assignmentId);
            assignment = assignmentService.getForParticipant(tenantId, learnerUserId, assignmentId);
        }
        if (assignment.getLearningGoalId() == null || assignment.getLearningGoalId().isBlank()) {
            throw new BusinessException(HttpStatus.CONFLICT, "LEARNING_ASSIGNMENT_GOAL_REQUIRED",
                    "课程作业尚未建立学习目标");
        }
        ConversationDetail conversation = actionService.execute(
                tenantId, learnerUserId, assignment.getLearningGoalId(), request,
                permissions, idempotencyKey);
        return new LearningAssignmentStartView(
                LearningAssignmentView.from(assignment), assignment.getLearnerProfileId(),
                assignment.getLearningGoalId(), conversation);
    }
}
