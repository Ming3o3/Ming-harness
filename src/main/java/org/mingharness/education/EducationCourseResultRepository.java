package org.mingharness.education;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EducationCourseResultRepository extends JpaRepository<EducationCourseResult, String> {

    Optional<EducationCourseResult> findByTenantIdAndCourseId(String tenantId, String courseId);
}
