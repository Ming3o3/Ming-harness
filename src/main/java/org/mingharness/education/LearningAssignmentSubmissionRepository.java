package org.mingharness.education;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LearningAssignmentSubmissionRepository
        extends JpaRepository<LearningAssignmentSubmission, String> {

    List<LearningAssignmentSubmission> findByTenantIdAndLearningAssignmentIdOrderBySubmittedAtDesc(
            String tenantId, String learningAssignmentId);

    Optional<LearningAssignmentSubmission> findByTenantIdAndLearningAssignmentIdAndRunId(
            String tenantId, String learningAssignmentId, String runId);

    List<LearningAssignmentSubmission> findByTenantIdOrderBySubmittedAtAsc(String tenantId);

    long countByTenantIdAndLearningAssignmentId(String tenantId, String learningAssignmentId);

    boolean existsByTenantIdAndLearningAssignmentId(String tenantId, String learningAssignmentId);

    Optional<LearningAssignmentSubmission> findTopByTenantIdAndLearningAssignmentIdOrderBySubmittedAtDesc(
            String tenantId, String learningAssignmentId);

    @Query("select count(s) from LearningAssignmentSubmission s, LearningAssignment a "
            + "where s.tenantId = :tenantId and a.tenantId = :tenantId "
            + "and s.learningAssignmentId = a.id "
            + "and (a.teacherUserId = :userId or a.learnerUserId = :userId)")
    long countForParticipant(@Param("tenantId") String tenantId, @Param("userId") String userId);

    @Query("select count(distinct s.learningAssignmentId) from LearningAssignmentSubmission s, "
            + "LearningAssignment a where s.tenantId = :tenantId and a.tenantId = :tenantId "
            + "and s.learningAssignmentId = a.id "
            + "and (a.teacherUserId = :userId or a.learnerUserId = :userId)")
    long countCoveredAssignmentsForParticipant(@Param("tenantId") String tenantId,
                                               @Param("userId") String userId);
}
