package org.mingharness.education;

import org.mingharness.common.BusinessException;
import org.mingharness.education.api.LearningTaskNotificationView;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** 将需要用户行动的学习任务状态转成可查询、可确认、可审计的站内触达记录。 */
@Service
public class LearningTaskNotificationService {

    private final LearningTaskNotificationRepository notificationRepository;
    private final LearningTaskRepository taskRepository;

    public LearningTaskNotificationService(LearningTaskNotificationRepository notificationRepository,
                                            LearningTaskRepository taskRepository) {
        this.notificationRepository = notificationRepository;
        this.taskRepository = taskRepository;
    }

    /** 根据当前任务状态幂等创建一条需要行动的通知。 */
    @Transactional
    public void ensureForTaskState(LearningTask task) {
        if (task == null) return;
        NotificationSpec spec = spec(task);
        if (spec == null) return;
        if (notificationRepository.findByTenantIdAndUserIdAndLearningTaskIdAndEventKey(
                task.getTenantId(), task.getUserId(), task.getId(), spec.eventKey()).isEmpty()) {
            notificationRepository.save(new LearningTaskNotification(
                    task.getTenantId(), task.getUserId(), task.getId(), spec.type(), spec.eventKey(),
                    spec.title(), spec.body(), Instant.now()));
        }
    }

    /** 任务开始、延期或完成后关闭当前未读提醒，但不修改任务结果。 */
    @Transactional
    public void resolveForTask(String tenantId, String userId, String taskId, Instant resolvedAt) {
        List<LearningTaskNotification> unread = notificationRepository
                .findByTenantIdAndUserIdAndLearningTaskIdAndStatus(
                        tenantId, userId, taskId, LearningTaskNotificationStatus.UNREAD);
        Instant at = resolvedAt == null ? Instant.now() : resolvedAt;
        unread.forEach(notification -> notification.markRead(at));
        if (!unread.isEmpty()) notificationRepository.saveAll(unread);
    }

    @Transactional
    public NotificationPage list(String tenantId, String userId, boolean unreadOnly, int limit) {
        int boundedLimit = Math.max(1, Math.min(100, limit));
        PageRequest page = PageRequest.of(0, boundedLimit);
        List<LearningTaskNotification> notifications = unreadOnly
                ? notificationRepository.findByTenantIdAndUserIdAndStatusOrderByCreatedAtDesc(
                tenantId, userId, LearningTaskNotificationStatus.UNREAD, page)
                : notificationRepository.findByTenantIdAndUserIdOrderByCreatedAtDesc(tenantId, userId, page);
        Instant surfacedAt = Instant.now();
        List<LearningTaskNotification> unseen = notifications.stream()
                .filter(notification -> notification.getSeenAt() == null)
                .toList();
        unseen.forEach(notification -> notification.markSeen(surfacedAt));
        if (!unseen.isEmpty()) notificationRepository.saveAll(unseen);
        List<LearningTaskNotificationView> views = notifications.stream()
                .map(notification -> LearningTaskNotificationView.from(notification,
                        taskRepository.findByTenantIdAndUserIdAndId(
                                tenantId, userId, notification.getLearningTaskId()).orElse(null)))
                .toList();
        return new NotificationPage(views,
                notificationRepository.countByTenantIdAndUserIdAndStatus(
                        tenantId, userId, LearningTaskNotificationStatus.UNREAD));
    }

    @Transactional
    public LearningTaskNotificationView markRead(String tenantId, String userId, String notificationId) {
        LearningTaskNotification notification = notificationRepository
                .findByTenantIdAndUserIdAndId(tenantId, userId, notificationId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "LEARNING_NOTIFICATION_NOT_FOUND", "学习任务通知不存在"));
        notification.markRead(Instant.now());
        LearningTask task = taskRepository.findByTenantIdAndUserIdAndId(
                tenantId, userId, notification.getLearningTaskId()).orElse(null);
        return LearningTaskNotificationView.from(notificationRepository.save(notification), task);
    }

    @Transactional
    public long markAllRead(String tenantId, String userId) {
        List<LearningTaskNotification> notifications = notificationRepository
                .findByTenantIdAndUserIdAndStatus(
                        tenantId, userId, LearningTaskNotificationStatus.UNREAD);
        Instant now = Instant.now();
        notifications.forEach(notification -> notification.markRead(now));
        if (!notifications.isEmpty()) notificationRepository.saveAll(notifications);
        return notifications.size();
    }

    private NotificationSpec spec(LearningTask task) {
        return switch (task.getStatus()) {
            case OPEN -> new NotificationSpec(
                    LearningTaskNotificationType.DUE,
                    "OPEN:D" + task.getDeferCount() + ":F" + task.getFailureCount(),
                    "复习任务待处理",
                    task.getTitle() + "已到期，请开始本次保持度复习。"
            );
            case AWAITING_EVIDENCE -> new NotificationSpec(
                    LearningTaskNotificationType.EVIDENCE_REQUIRED,
                    "AWAITING_EVIDENCE:" + safeKey(task.getRunId(), task.getFailureCount()),
                    "复习任务待补证据",
                    task.getTitle() + "已执行完成，但还缺少测评证据；请补充作答或评分依据。"
            );
            case FAILED -> new NotificationSpec(
                    LearningTaskNotificationType.FAILED,
                    "FAILED:" + safeKey(task.getLastFailedRunId(), task.getFailureCount()),
                    "复习任务执行失败",
                    task.getTitle() + "执行失败，可查看原因并重试任务。"
            );
            default -> null;
        };
    }

    private String safeKey(String value, int fallback) {
        return value == null || value.isBlank() ? Integer.toString(fallback) : value;
    }

    private record NotificationSpec(LearningTaskNotificationType type, String eventKey,
                                    String title, String body) {
    }

    public record NotificationPage(List<LearningTaskNotificationView> notifications,
                                   long unreadCount) {
    }
}
