package org.mingharness.education;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EducationCourseRepository extends JpaRepository<EducationCourse, String> {

    Optional<EducationCourse> findByTenantIdAndId(String tenantId, String id);

    Optional<EducationCourse> findByTenantIdAndCode(String tenantId, String code);

    List<EducationCourse> findByTenantIdAndOwnerUserIdOrderByUpdatedAtDesc(String tenantId, String ownerUserId);

    List<EducationCourse> findByTenantIdAndIdInOrderByUpdatedAtDesc(String tenantId, List<String> ids);
}
