package org.mingharness.education;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LearningAssignmentSubmissionRepository
        extends JpaRepository<LearningAssignmentSubmission, String> {

    List<LearningAssignmentSubmission> findByTenantIdAndLearningAssignmentIdOrderBySubmittedAtDesc(
            String tenantId, String learningAssignmentId);

    Optional<LearningAssignmentSubmission> findByTenantIdAndLearningAssignmentIdAndRunId(
            String tenantId, String learningAssignmentId, String runId);

    long countByTenantIdAndLearningAssignmentId(String tenantId, String learningAssignmentId);

    boolean existsByTenantIdAndLearningAssignmentId(String tenantId, String learningAssignmentId);

    Optional<LearningAssignmentSubmission> findTopByTenantIdAndLearningAssignmentIdOrderBySubmittedAtDesc(
            String tenantId, String learningAssignmentId);
}
