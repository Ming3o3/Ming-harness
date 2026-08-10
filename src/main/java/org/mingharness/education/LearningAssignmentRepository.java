package org.mingharness.education;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LearningAssignmentRepository extends JpaRepository<LearningAssignment, String> {

    Optional<LearningAssignment> findByTenantIdAndId(String tenantId, String id);

    List<LearningAssignment> findByTenantIdAndTeacherUserIdOrderByCreatedAtDesc(
            String tenantId, String teacherUserId);

    List<LearningAssignment> findByTenantIdAndLearnerUserIdOrderByCreatedAtDesc(
            String tenantId, String learnerUserId);

    List<LearningAssignment> findByTenantIdAndCourseIdAndBatchIdOrderByCreatedAtAsc(
            String tenantId, String courseId, String batchId);

    List<LearningAssignment> findByTenantIdAndCourseIdOrderByCreatedAtDesc(
            String tenantId, String courseId);

    List<LearningAssignment> findByTenantIdAndLearningGoalId(String tenantId, String learningGoalId);

    List<LearningAssignment> findByTenantIdAndLearnerUserIdAndLearningGoalIdOrderByCreatedAtDesc(
            String tenantId, String learnerUserId, String learningGoalId);

    List<LearningAssignment> findByStatusInAndDueAtLessThanEqualOrderByDueAtAsc(
            Collection<LearningAssignmentStatus> statuses, Instant reference, Pageable pageable);

    List<LearningAssignment> findByTenantIdAndStatusInAndDueAtLessThanEqualOrderByDueAtAsc(
            String tenantId, Collection<LearningAssignmentStatus> statuses,
            Instant reference, Pageable pageable);

    List<LearningAssignment> findByStatusInOrderByUpdatedAtAsc(
            Collection<LearningAssignmentStatus> statuses, Pageable pageable);

    @Query("select count(a) from LearningAssignment a where a.tenantId = :tenantId "
            + "and (a.teacherUserId = :userId or a.learnerUserId = :userId)")
    long countForParticipant(@Param("tenantId") String tenantId, @Param("userId") String userId);

    @Query("select count(a) from LearningAssignment a where a.tenantId = :tenantId "
            + "and (a.teacherUserId = :userId or a.learnerUserId = :userId) "
            + "and a.status = :status")
    long countForParticipantByStatus(@Param("tenantId") String tenantId,
                                     @Param("userId") String userId,
                                     @Param("status") LearningAssignmentStatus status);

    @Query("select count(a) from LearningAssignment a where a.tenantId = :tenantId "
            + "and (a.teacherUserId = :userId or a.learnerUserId = :userId) "
            + "and a.reviewStatus = :reviewStatus")
    long countForParticipantByReviewStatus(@Param("tenantId") String tenantId,
                                           @Param("userId") String userId,
                                           @Param("reviewStatus") LearningAssignmentReviewStatus reviewStatus);
}
