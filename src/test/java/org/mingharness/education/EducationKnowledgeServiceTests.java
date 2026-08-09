package org.mingharness.education;

import org.junit.jupiter.api.Test;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.context.KnowledgeDocument;
import org.mingharness.context.KnowledgeDocumentRepository;
import org.mingharness.education.api.EducationSourceRequest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EducationKnowledgeServiceTests {

    @Test
    void shouldNormalizeCourseTagsAndKeepDocumentBodyOutsideEducationMetadata() {
        EducationKnowledgeSourceRepository sources = mock(EducationKnowledgeSourceRepository.class);
        KnowledgeDocumentRepository documents = mock(KnowledgeDocumentRepository.class);
        KnowledgeDocument document = new KnowledgeDocument("tenant-a", "teacher-1", "函数课件",
                "函数正文", "INTERNAL", "student-1");
        when(documents.findById(document.getId())).thenReturn(Optional.of(document));
        when(sources.findByTenantIdAndDocumentIdAndDeletedAtIsNull("tenant-a", document.getId()))
                .thenReturn(Optional.empty());
        when(sources.save(any(EducationKnowledgeSource.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EducationKnowledgeService service = new EducationKnowledgeService(sources, documents,
                new SensitiveDataSanitizer());
        EducationKnowledgeSource source = service.upsertSource("tenant-a", "teacher-1",
                new EducationSourceRequest(document.getId(), " 数学 ", "高中一年级", " 人教A版 ",
                        "第一章", "理解函数", "函数, 定义域,函数", "集合", 4, null));

        assertEquals("数学", source.getSubject());
        assertEquals("人教A版", source.getCurriculumVersion());
        assertEquals("函数,定义域", source.getConceptTags());
        assertEquals("集合", source.getPrerequisiteConcepts());
        assertEquals(4, source.getDifficultyLevel());
        assertEquals("TEXTBOOK", source.getSourceType());
        assertEquals("函数正文", document.getContent());
    }
}
