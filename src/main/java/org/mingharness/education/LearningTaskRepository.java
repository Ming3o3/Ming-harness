package org.mingharness.education;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LearningTaskRepository extends JpaRepository<LearningTask, String> {

    Optional<LearningTask> findByTenantIdAndUserIdAndId(String tenantId, String userId, String id);

    Optional<LearningTask> findByTenantIdAndUserIdAndReviewPlanIdAndReviewSequence(
            String tenantId, String userId, String reviewPlanId, int reviewSequence);

    List<LearningTask> findByTenantIdAndUserIdAndStatusInOrderByScheduledAtAsc(
            String tenantId, String userId, Collection<LearningTaskStatus> statuses);

    List<LearningTask> findByTenantIdAndUserIdAndLearningGoalIdOrderByScheduledAtAsc(
            String tenantId, String userId, String learningGoalId);

    Optional<LearningTask> findFirstByTenantIdAndUserIdAndRunIdAndStatusIn(
            String tenantId, String userId, String runId, Collection<LearningTaskStatus> statuses);

    List<LearningTask> findByStatusInOrderByUpdatedAtAsc(Collection<LearningTaskStatus> statuses,
                                                          Pageable pageable);
}
