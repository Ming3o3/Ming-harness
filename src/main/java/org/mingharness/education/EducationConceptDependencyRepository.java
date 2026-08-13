package org.mingharness.education;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EducationConceptDependencyRepository extends JpaRepository<EducationConceptDependency, String> {

    List<EducationConceptDependency>
    findByTenantIdAndSubjectAndGradeLevelAndCurriculumVersionOrderByConceptKeyAscPrerequisiteConceptAsc(
            String tenantId, String subject, String gradeLevel, String curriculumVersion);

    long deleteByTenantIdAndSourceDocumentId(String tenantId, String sourceDocumentId);
}
