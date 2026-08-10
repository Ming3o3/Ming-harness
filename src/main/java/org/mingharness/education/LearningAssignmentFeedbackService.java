package org.mingharness.education;

import org.mingharness.common.BusinessException;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.education.api.LearningAssignmentFeedbackRequest;
import org.mingharness.education.api.LearningAssignmentFeedbackView;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

/** 管理教师反馈、可执行干预和学习者明确确认，形成证据后的下一步业务动作。 */
@Service
public class LearningAssignmentFeedbackService {

    private final LearningAssignmentFeedbackRepository feedbackRepository;
    private final LearningAssignmentService assignmentService;
    private final LearningAssignmentRepository assignmentRepository;
    private final LearningAssignmentNotificationService notificationService;
    private final SensitiveDataSanitizer sanitizer;

    public LearningAssignmentFeedbackService(LearningAssignmentFeedbackRepository feedbackRepository,
                                             LearningAssignmentService assignmentService,
                                             LearningAssignmentRepository assignmentRepository,
                                             LearningAssignmentNotificationService notificationService,
                                             SensitiveDataSanitizer sanitizer) {
        this.feedbackRepository = feedbackRepository;
        this.assignmentService = assignmentService;
        this.assignmentRepository = assignmentRepository;
        this.notificationService = notificationService;
        this.sanitizer = sanitizer;
    }

    @Transactional
    public LearningAssignmentFeedbackView create(String tenantId, String teacherUserId,
                                                 String assignmentId,
                                                 LearningAssignmentFeedbackRequest request) {
        LearningAssignment assignment = assignmentService.getForParticipant(tenantId, teacherUserId, assignmentId);
        if (!teacherUserId.equals(assignment.getTeacherUserId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "LEARNING_ASSIGNMENT_TEACHER_ONLY",
                    "只有布置者可以提交作业反馈");
        }
        LearningAssignmentFeedbackAction action = parseAction(request.action());
        Instant suggestedDueAt = request.suggestedDueAt();
        if (action == LearningAssignmentFeedbackAction.REQUEST_EVIDENCE
                && assignment.getStatus() == LearningAssignmentStatus.COMPLETED) {
            throw new BusinessException(HttpStatus.CONFLICT, "ASSIGNMENT_FEEDBACK_ACTION_CONFLICT",
                    "已完成的作业不能要求补充形成性证据，请改用普通反馈或建议保持度复习");
        }
        if (action == LearningAssignmentFeedbackAction.RECOMMEND_RETRY
                && assignment.getStatus() == LearningAssignmentStatus.COMPLETED) {
            throw new BusinessException(HttpStatus.CONFLICT, "ASSIGNMENT_FEEDBACK_ACTION_CONFLICT",
                    "已完成的作业不能重新启动形成性学习，请改用普通反馈或等待保持度复习");
        }
        if ((action == LearningAssignmentFeedbackAction.REQUEST_EVIDENCE
                || action == LearningAssignmentFeedbackAction.RECOMMEND_RETRY)
                && (assignment.getStatus() == LearningAssignmentStatus.ACCEPTED
                || assignment.getStatus() == LearningAssignmentStatus.OVERDUE)) {
            assignment.awaitEvidence(Instant.now());
            assignmentRepository.save(assignment);
            notificationService.ensureForState(assignment);
        }
        if (action == LearningAssignmentFeedbackAction.RESCHEDULE) {
            if (suggestedDueAt == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "ASSIGNMENT_FEEDBACK_DUE_REQUIRED",
                        "重新安排作业必须指定新的截止时间");
            }
            try {
                assignment.reschedule(suggestedDueAt);
            } catch (IllegalArgumentException | IllegalStateException exception) {
                throw new BusinessException(HttpStatus.CONFLICT, "ASSIGNMENT_RESCHEDULE_CONFLICT",
                        exception.getMessage());
            }
            assignmentRepository.save(assignment);
            notificationService.resolveForAssignmentState(
                    tenantId, assignment.getId(), LearningAssignmentNotificationType.OVERDUE);
            notificationService.ensureForState(assignment);
        } else if (suggestedDueAt != null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ASSIGNMENT_FEEDBACK_DUE_UNEXPECTED",
                    "只有重新安排动作可以指定新的截止时间");
        }
        LearningAssignmentFeedback feedback = feedbackRepository.save(new LearningAssignmentFeedback(
                tenantId, assignment.getId(), assignment.getTeacherUserId(), assignment.getLearnerUserId(),
                action, clean(request.message()), suggestedDueAt, Instant.now()));
        notificationService.ensureForFeedback(feedback, assignment);
        return LearningAssignmentFeedbackView.from(feedback);
    }

    @Transactional
    public List<LearningAssignmentFeedbackView> list(String tenantId, String userId, String assignmentId) {
        assignmentService.getForParticipant(tenantId, userId, assignmentId);
        return feedbackRepository.findByTenantIdAndLearningAssignmentIdOrderByCreatedAtDesc(
                        tenantId, assignmentId, PageRequest.of(0, 100)).stream()
                .map(LearningAssignmentFeedbackView::from).toList();
    }

    @Transactional
    public LearningAssignmentFeedbackView acknowledge(String tenantId, String learnerUserId,
                                                      String assignmentId, String feedbackId) {
        LearningAssignment assignment = assignmentService.getForParticipant(tenantId, learnerUserId, assignmentId);
        if (!learnerUserId.equals(assignment.getLearnerUserId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "LEARNING_ASSIGNMENT_LEARNER_ONLY",
                    "只有被布置作业的学习者可以确认反馈");
        }
        LearningAssignmentFeedback feedback = feedbackRepository
                .findByTenantIdAndLearningAssignmentIdAndId(tenantId, assignmentId, feedbackId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "LEARNING_ASSIGNMENT_FEEDBACK_NOT_FOUND", "作业反馈不存在"));
        if (!learnerUserId.equals(feedback.getLearnerUserId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "LEARNING_ASSIGNMENT_FEEDBACK_ACCESS_DENIED",
                    "无权确认该作业反馈");
        }
        feedback.acknowledge(Instant.now());
        LearningAssignmentFeedback saved = feedbackRepository.save(feedback);
        notificationService.markFeedbackRead(tenantId, learnerUserId, assignmentId, feedbackId);
        notificationService.ensureForFeedbackAcknowledged(saved, assignment);
        return LearningAssignmentFeedbackView.from(saved);
    }

    @Transactional
    public int resolveForRunStart(String tenantId, String learnerUserId, String assignmentId,
                                  Instant resolvedAt) {
        List<LearningAssignmentFeedback> actionable = feedbackRepository
                .findByTenantIdAndLearningAssignmentIdOrderByCreatedAtDesc(
                        tenantId, assignmentId, PageRequest.of(0, 100)).stream()
                .filter(feedback -> learnerUserId.equals(feedback.getLearnerUserId()))
                .filter(feedback -> feedback.getStatus() == LearningAssignmentFeedbackStatus.OPEN
                        || feedback.getStatus() == LearningAssignmentFeedbackStatus.ACKNOWLEDGED)
                .filter(feedback -> feedback.getAction() == LearningAssignmentFeedbackAction.REQUEST_EVIDENCE
                        || feedback.getAction() == LearningAssignmentFeedbackAction.RECOMMEND_RETRY)
                .toList();
        Instant at = resolvedAt == null ? Instant.now() : resolvedAt;
        actionable.forEach(feedback -> feedback.resolve(at));
        if (!actionable.isEmpty()) feedbackRepository.saveAll(actionable);
        return actionable.size();
    }

    private LearningAssignmentFeedbackAction parseAction(String value) {
        try {
            return LearningAssignmentFeedbackAction.valueOf(clean(value).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ASSIGNMENT_FEEDBACK_ACTION_INVALID",
                    "不支持的作业反馈动作: " + value);
        }
    }

    private String clean(String value) {
        return sanitizer.sanitize(value == null ? "" : value.trim());
    }
}
