package org.mingharness.education;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EducationCourseLearnerResultRepository
        extends JpaRepository<EducationCourseLearnerResult, String> {

    List<EducationCourseLearnerResult> findByTenantIdAndCourseResultIdOrderByLearnerUserIdAsc(
            String tenantId, String courseResultId);
}
