package org.mingharness.education;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface LearningReviewPlanRepository extends JpaRepository<LearningReviewPlan, String> {

    Optional<LearningReviewPlan> findByTenantIdAndUserIdAndLearningGoalId(
            String tenantId, String userId, String learningGoalId);

    Optional<LearningReviewPlan> findByIdAndTenantIdAndUserId(
            String id, String tenantId, String userId);

    @Query("""
            select plan from LearningReviewPlan plan
            where plan.status = :status and plan.nextReviewAt <= :now
            order by plan.nextReviewAt asc
            """)
    List<LearningReviewPlan> findDueByStatus(@Param("status") LearningReviewPlanStatus status,
                                             @Param("now") Instant now,
                                             Pageable pageable);

    /** 调度器按计划加锁，保证多实例不会为同一复习发生重复任务。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select plan from LearningReviewPlan plan where plan.id = :id")
    Optional<LearningReviewPlan> findByIdForUpdate(@Param("id") String id);
}
