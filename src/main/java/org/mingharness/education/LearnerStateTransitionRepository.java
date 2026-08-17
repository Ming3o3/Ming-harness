package org.mingharness.education;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LearnerStateTransitionRepository extends JpaRepository<LearnerStateTransition, String> {

    List<LearnerStateTransition> findTop200ByTenantIdAndLearnerProfileIdOrderByCreatedAtDesc(
            String tenantId, String learnerProfileId);

    List<LearnerStateTransition> findTop200ByTenantIdAndLearnerProfileIdAndConceptKeyOrderByCreatedAtDesc(
            String tenantId, String learnerProfileId, String conceptKey);

    List<LearnerStateTransition> findByTenantIdAndLearnerProfileIdOrderByCreatedAtAsc(
            String tenantId, String learnerProfileId);

    List<LearnerStateTransition> findByTenantIdAndRunIdOrderByCreatedAtAsc(
            String tenantId, String runId);
}
