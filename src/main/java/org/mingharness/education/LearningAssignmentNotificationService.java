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
        }
        return recipients;
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
