package org.mingharness.education;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LearnerMasteryRepository extends JpaRepository<LearnerMastery, String> {

    List<LearnerMastery> findByTenantIdAndLearnerProfileIdOrderByConceptKeyAsc(
            String tenantId, String learnerProfileId);

    Optional<LearnerMastery> findByTenantIdAndLearnerProfileIdAndConceptKey(
            String tenantId, String learnerProfileId, String conceptKey);
}
