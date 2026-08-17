package org.mingharness.education;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LearningAssignmentTestCaseRepository extends JpaRepository<LearningAssignmentTestCase, String> {

    List<LearningAssignmentTestCase> findByTenantIdAndLearningAssignmentIdAndEnabledTrueOrderBySequenceAscCreatedAtAsc(
            String tenantId, String learningAssignmentId);

    List<LearningAssignmentTestCase> findByTenantIdAndLearningAssignmentIdOrderBySequenceAscCreatedAtAsc(
            String tenantId, String learningAssignmentId);

    Optional<LearningAssignmentTestCase> findByTenantIdAndLearningAssignmentIdAndCaseKey(
            String tenantId, String learningAssignmentId, String caseKey);
}
