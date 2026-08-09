package org.mingharness.education;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LearningReviewPlanRepository extends JpaRepository<LearningReviewPlan, String> {

    Optional<LearningReviewPlan> findByTenantIdAndUserIdAndLearningGoalId(
            String tenantId, String userId, String learningGoalId);

    Optional<LearningReviewPlan> findByIdAndTenantIdAndUserId(
            String id, String tenantId, String userId);
}
