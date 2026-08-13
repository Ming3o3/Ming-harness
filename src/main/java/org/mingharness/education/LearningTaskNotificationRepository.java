package org.mingharness.education;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LearningTaskNotificationRepository extends JpaRepository<LearningTaskNotification, String> {

    Optional<LearningTaskNotification> findByTenantIdAndUserIdAndLearningTaskIdAndEventKey(
            String tenantId, String userId, String learningTaskId, String eventKey);

    Optional<LearningTaskNotification> findByTenantIdAndUserIdAndId(
            String tenantId, String userId, String id);

    List<LearningTaskNotification> findByTenantIdAndUserIdAndStatusOrderByCreatedAtDesc(
            String tenantId, String userId, LearningTaskNotificationStatus status, Pageable pageable);

    List<LearningTaskNotification> findByTenantIdAndUserIdAndStatus(
            String tenantId, String userId, LearningTaskNotificationStatus status);

    List<LearningTaskNotification> findByTenantIdAndUserIdOrderByCreatedAtDesc(
            String tenantId, String userId, Pageable pageable);

    List<LearningTaskNotification> findByTenantIdOrderByCreatedAtAsc(String tenantId);

    List<LearningTaskNotification> findByTenantIdAndUserIdAndLearningTaskIdAndStatus(
            String tenantId, String userId, String learningTaskId, LearningTaskNotificationStatus status);

    long countByTenantIdAndUserIdAndStatus(
            String tenantId, String userId, LearningTaskNotificationStatus status);

    long countByTenantIdAndUserId(String tenantId, String userId);

    long countByTenantIdAndUserIdAndSeenAtIsNotNull(String tenantId, String userId);

    long countByTenantIdAndUserIdAndReadAtIsNotNull(String tenantId, String userId);
}
