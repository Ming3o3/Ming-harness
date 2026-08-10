package org.mingharness.education;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EducationEnrollmentRepository extends JpaRepository<EducationEnrollment, String> {

    Optional<EducationEnrollment> findByTenantIdAndCourseIdAndLearnerUserId(
            String tenantId, String courseId, String learnerUserId);

    List<EducationEnrollment> findByTenantIdAndCourseIdOrderByEnrolledAtAsc(String tenantId, String courseId);

    List<EducationEnrollment> findByTenantIdAndLearnerUserIdAndStatus(
            String tenantId, String learnerUserId, EducationEnrollmentStatus status);

    long countByTenantIdAndCourseIdAndStatus(
            String tenantId, String courseId, EducationEnrollmentStatus status);

    List<EducationEnrollment> findByTenantIdAndCourseIdAndStatus(
            String tenantId, String courseId, EducationEnrollmentStatus status);

    boolean existsByTenantIdAndCourseIdAndLearnerUserIdAndStatus(
            String tenantId, String courseId, String learnerUserId, EducationEnrollmentStatus status);

    List<EducationEnrollment> findByTenantIdAndCourseIdInAndStatus(
            String tenantId, Collection<String> courseIds, EducationEnrollmentStatus status);
}
