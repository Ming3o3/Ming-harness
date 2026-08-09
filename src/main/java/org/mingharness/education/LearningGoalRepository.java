package org.mingharness.education;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LearningGoalRepository extends JpaRepository<LearningGoal, String> {

    List<LearningGoal> findByTenantIdAndUserIdOrderByUpdatedAtDesc(String tenantId, String userId);

    Optional<LearningGoal> findByIdAndTenantIdAndUserId(String id, String tenantId, String userId);

    List<LearningGoal> findByTenantIdAndUserIdAndLearnerProfileIdAndConceptKeyIgnoreCase(
            String tenantId, String userId, String learnerProfileId, String conceptKey);
}
