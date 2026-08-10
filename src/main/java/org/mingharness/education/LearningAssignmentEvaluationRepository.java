package org.mingharness.education;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LearningAssignmentEvaluationRepository
        extends JpaRepository<LearningAssignmentEvaluation, String> {

    List<LearningAssignmentEvaluation> findByTenantIdAndLearningAssignmentIdOrderByCreatedAtDesc(
            String tenantId, String learningAssignmentId);

    @Query("select e from LearningAssignmentEvaluation e where e.tenantId = :tenantId "
            + "and (e.evaluatorUserId = :userId or e.learnerUserId = :userId) "
            + "order by e.createdAt asc")
    List<LearningAssignmentEvaluation> findByTenantIdAndParticipantOrderByCreatedAtAsc(
            @Param("tenantId") String tenantId, @Param("userId") String userId);

    List<LearningAssignmentEvaluation> findByTenantIdAndCourseIdOrderByCreatedAtAsc(
            String tenantId, String courseId);
}
