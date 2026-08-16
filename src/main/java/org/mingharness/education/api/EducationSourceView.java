package org.mingharness.education.api;

import org.mingharness.education.EducationKnowledgeSource;
import org.mingharness.context.KnowledgeDocument;

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
        String programmingLanguage,
        String documentTitle,
        String documentOwnerUserId,
        Instant createdAt,
        Instant updatedAt
) {

    public static EducationSourceView from(EducationKnowledgeSource source) {
        return from(source, null);
    }

    public static EducationSourceView from(EducationKnowledgeSource source, KnowledgeDocument document) {
        return new EducationSourceView(source.getId(), source.getTenantId(), source.getDocumentId(),
                source.getSubject(), source.getGradeLevel(), source.getCurriculumVersion(),
                source.getChapter(), source.getLearningObjectives(), source.getConceptTags(),
                source.getPrerequisiteConcepts(), source.getDifficultyLevel(), source.getSourceType(),
                source.getProgrammingLanguage(),
                document == null ? null : document.getTitle(),
                document == null ? null : document.getOwnerUserId(),
                source.getCreatedAt(), source.getUpdatedAt());
    }
}
