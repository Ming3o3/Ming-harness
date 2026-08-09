package org.mingharness.education;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssessmentAttemptRepository extends JpaRepository<AssessmentAttempt, String> {

    List<AssessmentAttempt> findByTenantIdAndUserIdAndLearningGoalIdOrderByCreatedAtAsc(
            String tenantId, String userId, String learningGoalId);

    Optional<AssessmentAttempt> findTop1ByTenantIdAndUserIdAndLearningGoalIdOrderByCreatedAtDesc(
            String tenantId, String userId, String learningGoalId);

    List<AssessmentAttempt> findByTenantIdAndUserIdAndRunIdOrderByCreatedAtAsc(
            String tenantId, String userId, String runId);
}
