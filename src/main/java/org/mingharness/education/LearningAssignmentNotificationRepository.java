package org.mingharness.education;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LearningAssignmentNotificationRepository
        extends JpaRepository<LearningAssignmentNotification, String> {

    Optional<LearningAssignmentNotification> findByTenantIdAndUserIdAndLearningAssignmentIdAndEventKey(
            String tenantId, String userId, String learningAssignmentId, String eventKey);

    Optional<LearningAssignmentNotification> findByTenantIdAndUserIdAndId(
            String tenantId, String userId, String id);

    List<LearningAssignmentNotification> findByTenantIdAndUserIdOrderByCreatedAtDesc(
            String tenantId, String userId, Pageable pageable);

    List<LearningAssignmentNotification> findByTenantIdAndUserIdAndStatusOrderByCreatedAtDesc(
            String tenantId, String userId, LearningAssignmentNotificationStatus status,
            Pageable pageable);

    List<LearningAssignmentNotification> findByTenantIdAndUserIdAndStatus(
            String tenantId, String userId, LearningAssignmentNotificationStatus status);

    List<LearningAssignmentNotification> findByTenantIdAndUserIdAndLearningAssignmentIdAndStatus(
            String tenantId, String userId, String learningAssignmentId,
            LearningAssignmentNotificationStatus status);

    List<LearningAssignmentNotification> findByTenantIdAndLearningAssignmentIdAndNotificationTypeAndStatus(
            String tenantId, String learningAssignmentId, LearningAssignmentNotificationType notificationType,
            LearningAssignmentNotificationStatus status);

    long countByTenantIdAndUserIdAndStatus(
            String tenantId, String userId, LearningAssignmentNotificationStatus status);

    long countByTenantIdAndUserId(String tenantId, String userId);

    long countByTenantIdAndUserIdAndSeenAtIsNotNull(String tenantId, String userId);

    long countByTenantIdAndUserIdAndReadAtIsNotNull(String tenantId, String userId);
}
