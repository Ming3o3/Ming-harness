package org.mingharness.education;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LearningAssignmentRepository extends JpaRepository<LearningAssignment, String> {

    Optional<LearningAssignment> findByTenantIdAndId(String tenantId, String id);

    List<LearningAssignment> findByTenantIdAndTeacherUserIdOrderByCreatedAtDesc(
            String tenantId, String teacherUserId);

    List<LearningAssignment> findByTenantIdAndLearnerUserIdOrderByCreatedAtDesc(
            String tenantId, String learnerUserId);

    List<LearningAssignment> findByTenantIdAndLearningGoalId(String tenantId, String learningGoalId);
}
