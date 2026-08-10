package org.mingharness.education;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AssessmentAttemptRepository extends JpaRepository<AssessmentAttempt, String> {

    List<AssessmentAttempt> findByTenantIdAndUserIdAndLearningGoalIdOrderByCreatedAtAsc(
            String tenantId, String userId, String learningGoalId);

    List<AssessmentAttempt> findByTenantIdAndUserIdAndLearningAssignmentIdOrderByCreatedAtAsc(
            String tenantId, String userId, String learningAssignmentId);

    Optional<AssessmentAttempt> findTop1ByTenantIdAndUserIdAndLearningGoalIdOrderByCreatedAtDesc(
            String tenantId, String userId, String learningGoalId);

    List<AssessmentAttempt> findByTenantIdAndUserIdAndRunIdOrderByCreatedAtAsc(
            String tenantId, String userId, String runId);

    boolean existsByTenantIdAndUserIdAndRunIdAndAssessmentType(
            String tenantId, String userId, String runId, AssessmentAttemptType assessmentType);

    long countByTenantIdAndUserId(String tenantId, String userId);

    long countByTenantIdAndUserIdAndAssessmentType(
            String tenantId, String userId, AssessmentAttemptType assessmentType);

    long countByTenantIdAndUserIdAndAssessmentTypeAndCorrectTrue(
            String tenantId, String userId, AssessmentAttemptType assessmentType);

    @Query("select a from AssessmentAttempt a where a.tenantId = :tenantId "
            + "and a.userId = :userId order by a.createdAt asc")
    List<AssessmentAttempt> findByTenantIdAndUserIdOrderByCreatedAtAsc(
            @Param("tenantId") String tenantId, @Param("userId") String userId);

    long countByTenantIdAndUserIdAndCorrectTrue(String tenantId, String userId);
}
