package org.mingharness.education;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EducationKnowledgeSourceRepository extends JpaRepository<EducationKnowledgeSource, String> {

    Optional<EducationKnowledgeSource> findByTenantIdAndDocumentIdAndDeletedAtIsNull(
            String tenantId, String documentId);

    Optional<EducationKnowledgeSource> findByTenantIdAndDocumentId(String tenantId, String documentId);

    List<EducationKnowledgeSource> findByTenantIdAndDeletedAtIsNullOrderByUpdatedAtDesc(String tenantId);

    List<EducationKnowledgeSource> findByTenantIdAndSubjectAndGradeLevelAndCurriculumVersionAndDeletedAtIsNull(
            String tenantId, String subject, String gradeLevel, String curriculumVersion);
}
