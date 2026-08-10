package org.mingharness.education;

import org.mingharness.common.BusinessException;
import org.mingharness.education.api.LearningAssignmentNotificationView;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** 将课程作业状态变化转换为幂等的学习者/教师站内触达。 */
@Service
public class LearningAssignmentNotificationService {

    private final LearningAssignmentNotificationRepository notificationRepository;
    private final LearningAssignmentRepository assignmentRepository;

    public LearningAssignmentNotificationService(
            LearningAssignmentNotificationRepository notificationRepository,
            LearningAssignmentRepository assignmentRepository) {
        this.notificationRepository = notificationRepository;
        this.assignmentRepository = assignmentRepository;
    }

    @Transactional
    public void ensureForState(LearningAssignment assignment) {
        if (assignment == null) return;
        for (RecipientSpec recipient : recipients(assignment)) {
            if (notificationRepository
                    .findByTenantIdAndUserIdAndLearningAssignmentIdAndEventKey(
                            assignment.getTenantId(), recipient.userId(), assignment.getId(),
                            recipient.eventKey()).isEmpty()) {
                notificationRepository.save(new LearningAssignmentNotification(
                        assignment.getTenantId(), recipient.userId(), assignment.getId(),
                        recipient.type(), recipient.eventKey(), recipient.title(), recipient.body(),
                        Instant.now()));
            }
        }
    }

    @Transactional
    public void ensureForFeedback(LearningAssignmentFeedback feedback, LearningAssignment assignment) {
        if (feedback == null || assignment == null) return;
        String eventKey = "FEEDBACK:" + feedback.getId();
        if (notificationRepository.findByTenantIdAndUserIdAndLearningAssignmentIdAndEventKey(
                assignment.getTenantId(), assignment.getLearnerUserId(), assignment.getId(), eventKey).isPresent()) {
            return;
        }
        String title = switch (feedback.getAction()) {
            case COMMENT -> "教师有新的作业反馈";
            case REQUEST_EVIDENCE -> "教师要求补充作业证据";
            case RECOMMEND_RETRY -> "教师建议重新学习作业知识点";
            case RESCHEDULE -> "教师已重新安排作业截止时间";
        };
        String body = feedback.getMessage();
        if (feedback.getSuggestedDueAt() != null) {
            body += " 截止时间已调整为 " + feedback.getSuggestedDueAt() + "。";
        }
        notificationRepository.save(new LearningAssignmentNotification(
                assignment.getTenantId(), assignment.getLearnerUserId(), assignment.getId(),
                LearningAssignmentNotificationType.FEEDBACK, eventKey, title, body, Instant.now()));
    }

    @Transactional
    public void ensureForFeedbackAcknowledged(LearningAssignmentFeedback feedback,
                                              LearningAssignment assignment) {
        if (feedback == null || assignment == null) return;
        String eventKey = "FEEDBACK_ACKNOWLEDGED:" + feedback.getId();
        if (notificationRepository.findByTenantIdAndUserIdAndLearningAssignmentIdAndEventKey(
                assignment.getTenantId(), assignment.getTeacherUserId(), assignment.getId(), eventKey).isPresent()) {
            return;
        }
        notificationRepository.save(new LearningAssignmentNotification(
                assignment.getTenantId(), assignment.getTeacherUserId(), assignment.getId(),
                LearningAssignmentNotificationType.FEEDBACK_ACKNOWLEDGED, eventKey,
                "学习者已确认教师反馈", assignment.getLearnerUserId() + "已确认作业反馈。", Instant.now()));
    }

    /** Run 成功但缺少形成性测评时，按 Run 维度幂等通知学习者和教师。 */
    @Transactional
    public void ensureForEvidenceRequired(LearningAssignment assignment, String runId) {
        if (assignment == null || runId == null || runId.isBlank()) return;
        String eventKey = "EVIDENCE_REQUIRED:" + runId;
        saveIfAbsent(assignment, assignment.getLearnerUserId(), eventKey,
                "课程作业待补证据", assignment.getTitle() + "对应的学习 Run 已完成，请补充形成性测评证据。");
        saveIfAbsent(assignment, assignment.getTeacherUserId(), eventKey,
                "课程作业缺少测评证据", assignment.getLearnerUserId() + "的作业“"
                        + assignment.getTitle() + "”对应 Run 已完成，但尚未形成测评证据。");
    }

    @Transactional
    public void resolveForAssignmentEvidenceRequired(String tenantId, String assignmentId) {
        List<LearningAssignmentNotification> notifications = notificationRepository
                .findByTenantIdAndLearningAssignmentIdAndNotificationTypeAndStatus(
                        tenantId, assignmentId, LearningAssignmentNotificationType.EVIDENCE_REQUIRED,
                        LearningAssignmentNotificationStatus.UNREAD);
        if (notifications.isEmpty()) return;
        Instant now = Instant.now();
        notifications.forEach(notification -> notification.markRead(now));
        notificationRepository.saveAll(notifications);
    }

    @Transactional
    public void markFeedbackRead(String tenantId, String userId, String assignmentId, String feedbackId) {
        notificationRepository.findByTenantIdAndUserIdAndLearningAssignmentIdAndEventKey(
                        tenantId, userId, assignmentId, "FEEDBACK:" + feedbackId)
                .ifPresent(notification -> {
                    notification.markRead(Instant.now());
                    notificationRepository.save(notification);
                });
    }

    @Transactional
    public void resolveForAssignmentState(String tenantId, String assignmentId,
                                          LearningAssignmentNotificationType notificationType) {
        List<LearningAssignmentNotification> notifications = notificationRepository
                .findByTenantIdAndLearningAssignmentIdAndNotificationTypeAndStatus(
                        tenantId, assignmentId, notificationType, LearningAssignmentNotificationStatus.UNREAD);
        if (notifications.isEmpty()) return;
        Instant now = Instant.now();
        notifications.forEach(notification -> notification.markRead(now));
        notificationRepository.saveAll(notifications);
    }

    @Transactional
    public NotificationPage list(String tenantId, String userId, boolean unreadOnly, int limit) {
        int boundedLimit = Math.max(1, Math.min(100, limit));
        PageRequest page = PageRequest.of(0, boundedLimit);
        List<LearningAssignmentNotification> notifications = unreadOnly
                ? notificationRepository.findByTenantIdAndUserIdAndStatusOrderByCreatedAtDesc(
                tenantId, userId, LearningAssignmentNotificationStatus.UNREAD, page)
                : notificationRepository.findByTenantIdAndUserIdOrderByCreatedAtDesc(
                tenantId, userId, page);
        Instant now = Instant.now();
        List<LearningAssignmentNotification> unseen = notifications.stream()
                .filter(item -> item.getSeenAt() == null).toList();
        unseen.forEach(item -> item.markSeen(now));
        if (!unseen.isEmpty()) notificationRepository.saveAll(unseen);
        List<LearningAssignmentNotificationView> views = notifications.stream()
                .map(item -> LearningAssignmentNotificationView.from(item,
                        assignmentRepository.findByTenantIdAndId(
                                tenantId, item.getLearningAssignmentId()).orElse(null)))
                .toList();
        return new NotificationPage(views, notificationRepository.countByTenantIdAndUserIdAndStatus(
                tenantId, userId, LearningAssignmentNotificationStatus.UNREAD));
    }

    @Transactional
    public LearningAssignmentNotificationView markRead(String tenantId, String userId,
                                                        String notificationId) {
        LearningAssignmentNotification notification = notificationRepository
                .findByTenantIdAndUserIdAndId(tenantId, userId, notificationId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "LEARNING_ASSIGNMENT_NOTIFICATION_NOT_FOUND", "课程作业通知不存在"));
        notification.markRead(Instant.now());
        LearningAssignment assignment = assignmentRepository.findByTenantIdAndId(
                tenantId, notification.getLearningAssignmentId()).orElse(null);
        return LearningAssignmentNotificationView.from(notificationRepository.save(notification), assignment);
    }

    @Transactional
    public long markAllRead(String tenantId, String userId) {
        List<LearningAssignmentNotification> notifications = notificationRepository
                .findByTenantIdAndUserIdAndStatus(
                        tenantId, userId, LearningAssignmentNotificationStatus.UNREAD);
        Instant now = Instant.now();
        notifications.forEach(item -> item.markRead(now));
        if (!notifications.isEmpty()) notificationRepository.saveAll(notifications);
        return notifications.size();
    }

    private List<RecipientSpec> recipients(LearningAssignment assignment) {
        LearningAssignmentNotificationType type = switch (assignment.getStatus()) {
            case ASSIGNED -> LearningAssignmentNotificationType.ASSIGNED;
            case ACCEPTED -> LearningAssignmentNotificationType.ACCEPTED;
            case AWAITING_EVIDENCE -> LearningAssignmentNotificationType.EVIDENCE_REQUIRED;
            case OVERDUE -> LearningAssignmentNotificationType.OVERDUE;
            case COMPLETED -> LearningAssignmentNotificationType.COMPLETED;
            case CANCELLED -> LearningAssignmentNotificationType.CANCELLED;
        };
        String eventKey = type.name();
        List<RecipientSpec> recipients = new ArrayList<>();
        switch (type) {
            case ASSIGNED -> recipients.add(spec(assignment.getLearnerUserId(), type, eventKey,
                    "收到课程作业", assignment.getTitle() + "已布置，请接受作业并开始学习。"));
            case ACCEPTED -> recipients.add(spec(assignment.getTeacherUserId(), type, eventKey,
                    "学习者已接受作业", assignment.getLearnerUserId() + "已接受课程作业“"
                            + assignment.getTitle() + "”。"));
            case EVIDENCE_REQUIRED -> {
                recipients.add(spec(assignment.getLearnerUserId(), type, eventKey,
                        "课程作业待补证据", assignment.getTitle() + "需要补充形成性测评证据。"));
                recipients.add(spec(assignment.getTeacherUserId(), type, eventKey,
                        "课程作业缺少测评证据", assignment.getLearnerUserId() + "的作业“"
                                + assignment.getTitle() + "”需要补充测评证据。"));
            }
            case OVERDUE -> {
                recipients.add(spec(assignment.getLearnerUserId(), type, eventKey,
                        "课程作业已逾期", assignment.getTitle() + "已超过截止时间，请联系教师重新安排。"));
                recipients.add(spec(assignment.getTeacherUserId(), type, eventKey,
                        "课程作业逾期", assignment.getLearnerUserId() + "的作业“"
                                + assignment.getTitle() + "”已逾期。"));
            }
            case COMPLETED -> recipients.add(spec(assignment.getTeacherUserId(), type, eventKey,
                    "课程作业已完成", assignment.getLearnerUserId() + "已完成课程作业“"
                            + assignment.getTitle() + "”。"));
            case CANCELLED -> {
                recipients.add(spec(assignment.getLearnerUserId(), type, eventKey,
                        "课程作业已取消", assignment.getTitle() + "已被布置者取消。"));
                recipients.add(spec(assignment.getTeacherUserId(), type, eventKey,
                        "课程作业已取消", "课程作业“" + assignment.getTitle() + "”已取消。"));
            }
            case FEEDBACK -> {
                // 反馈通知由 ensureForFeedback 根据反馈 ID 单独创建。
            }
            case FEEDBACK_ACKNOWLEDGED -> {
                // 回执通知由 ensureForFeedbackAcknowledged 根据反馈 ID 单独创建。
            }
        }
        return recipients;
    }

    private void saveIfAbsent(LearningAssignment assignment, String userId, String eventKey,
                              String title, String body) {
        if (notificationRepository.findByTenantIdAndUserIdAndLearningAssignmentIdAndEventKey(
                assignment.getTenantId(), userId, assignment.getId(), eventKey).isPresent()) return;
        notificationRepository.save(new LearningAssignmentNotification(
                assignment.getTenantId(), userId, assignment.getId(),
                LearningAssignmentNotificationType.EVIDENCE_REQUIRED, eventKey,
                title, body, Instant.now()));
    }

    private RecipientSpec spec(String userId, LearningAssignmentNotificationType type,
                               String eventKey, String title, String body) {
        return new RecipientSpec(userId, type, eventKey, title, body);
    }

    private record RecipientSpec(String userId, LearningAssignmentNotificationType type,
                                 String eventKey, String title, String body) {
    }

    public record NotificationPage(List<LearningAssignmentNotificationView> notifications,
                                   long unreadCount) {
    }
}
