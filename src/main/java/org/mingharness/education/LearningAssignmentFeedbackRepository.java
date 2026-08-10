package org.mingharness.education;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LearningAssignmentFeedbackRepository extends JpaRepository<LearningAssignmentFeedback, String> {

    List<LearningAssignmentFeedback> findByTenantIdAndLearningAssignmentIdOrderByCreatedAtDesc(
            String tenantId, String learningAssignmentId, Pageable pageable);

    Optional<LearningAssignmentFeedback> findByTenantIdAndLearningAssignmentIdAndId(
            String tenantId, String learningAssignmentId, String id);

    @Query("select f from LearningAssignmentFeedback f where f.tenantId = :tenantId "
            + "and (f.teacherUserId = :userId or f.learnerUserId = :userId) order by f.createdAt asc")
    List<LearningAssignmentFeedback> findByTenantIdAndParticipantOrderByCreatedAtAsc(
            @Param("tenantId") String tenantId, @Param("userId") String userId);
}
