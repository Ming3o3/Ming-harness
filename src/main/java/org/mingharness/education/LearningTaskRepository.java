package org.mingharness.education;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    long countByTenantIdAndUserId(String tenantId, String userId);

    long countByTenantIdAndUserIdAndStatus(String tenantId, String userId, LearningTaskStatus status);

    long countByTenantIdAndUserIdAndStartedAtIsNotNull(String tenantId, String userId);

    long countByTenantIdAndUserIdAndCompletedAtIsNotNull(String tenantId, String userId);

    @Query("select coalesce(sum(t.failureCount), 0) from LearningTask t "
            + "where t.tenantId = :tenantId and t.userId = :userId")
    long sumFailureCount(@Param("tenantId") String tenantId, @Param("userId") String userId);

    @Query("select count(t) from LearningTask t where t.tenantId = :tenantId "
            + "and t.userId = :userId and t.startedAt is not null "
            + "and exists (select a.id from AssessmentAttempt a "
            + "where a.tenantId = t.tenantId and a.userId = t.userId and a.runId = t.runId)")
    long countStartedWithAssessmentEvidence(@Param("tenantId") String tenantId,
                                            @Param("userId") String userId);
}
