package org.mingharness.education;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LearningAssignmentFeedbackRepository extends JpaRepository<LearningAssignmentFeedback, String> {

    List<LearningAssignmentFeedback> findByTenantIdAndLearningAssignmentIdOrderByCreatedAtDesc(
            String tenantId, String learningAssignmentId, Pageable pageable);

    Optional<LearningAssignmentFeedback> findByTenantIdAndLearningAssignmentIdAndId(
            String tenantId, String learningAssignmentId, String id);
}
