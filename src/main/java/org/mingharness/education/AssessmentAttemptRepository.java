package org.mingharness.education;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssessmentAttemptRepository extends JpaRepository<AssessmentAttempt, String> {

    List<AssessmentAttempt> findByTenantIdAndUserIdAndLearningGoalIdOrderByCreatedAtAsc(
            String tenantId, String userId, String learningGoalId);

    List<AssessmentAttempt> findByTenantIdAndUserIdAndRunIdOrderByCreatedAtAsc(
            String tenantId, String userId, String runId);
}
