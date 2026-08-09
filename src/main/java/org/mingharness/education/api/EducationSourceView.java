package org.mingharness.education.api;

import org.mingharness.education.EducationKnowledgeSource;

import java.time.Instant;

public record EducationSourceView(
        String id,
        String tenantId,
        String documentId,
        String subject,
        String gradeLevel,
        String curriculumVersion,
        String chapter,
        String learningObjectives,
        String conceptTags,
        String prerequisiteConcepts,
        int difficultyLevel,
        String sourceType,
        Instant createdAt,
        Instant updatedAt
) {

    public static EducationSourceView from(EducationKnowledgeSource source) {
        return new EducationSourceView(source.getId(), source.getTenantId(), source.getDocumentId(),
                source.getSubject(), source.getGradeLevel(), source.getCurriculumVersion(),
                source.getChapter(), source.getLearningObjectives(), source.getConceptTags(),
                source.getPrerequisiteConcepts(), source.getDifficultyLevel(), source.getSourceType(),
                source.getCreatedAt(), source.getUpdatedAt());
    }
}
