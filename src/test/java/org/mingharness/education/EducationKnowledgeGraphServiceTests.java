package org.mingharness.education;

import org.junit.jupiter.api.Test;
import org.mingharness.common.SensitiveDataSanitizer;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.mockito.InOrder;

class EducationKnowledgeGraphServiceTests {

    @Test
    void shouldMaterializeOneEdgePerConceptAndPrerequisite() {
        EducationConceptDependencyRepository repository = mock(EducationConceptDependencyRepository.class);
        EducationKnowledgeGraphService service = new EducationKnowledgeGraphService(repository,
                new SensitiveDataSanitizer());
        EducationKnowledgeSource source = new EducationKnowledgeSource(
                "tenant-a", "doc-1", "数学", "高中一年级", "人教A版", "函数", "",
                "二次函数,函数图像", "函数概念,集合", 3, "TEXTBOOK");

        service.replaceDerivedEdges(source);

        InOrder persistence = inOrder(repository);
        persistence.verify(repository).deleteByTenantIdAndSourceDocumentId("tenant-a", "doc-1");
        persistence.verify(repository).flush();
        persistence.verify(repository).saveAll(any());
    }

    @Test
    void shouldResolveTransitivePrerequisitesAndUseFrozenMastery() {
        EducationConceptDependencyRepository repository = mock(EducationConceptDependencyRepository.class);
        EducationKnowledgeGraphService service = new EducationKnowledgeGraphService(repository,
                new SensitiveDataSanitizer());
        when(repository
                .findByTenantIdAndSubjectAndGradeLevelAndCurriculumVersionOrderByConceptKeyAscPrerequisiteConceptAsc(
                        "tenant-a", "数学", "高中一年级", "人教A版"))
                .thenReturn(List.of(
                        edge("二次函数", "函数", "doc-2"),
                        edge("函数", "集合", "doc-1"),
                        edge("集合", "数集", "doc-0")));

        EducationDependencyGraph graph = service.resolve("tenant-a", new EducationRetrievalFilter(
                "数学", "高中一年级", "人教A版", "二次函数", null, null,
                Map.of("函数", 0.2, "集合", 0.8, "数集", 0.1)));

        assertEquals(3, graph.prerequisites().size());
        assertEquals("函数", graph.prerequisites().get(0).conceptKey());
        assertEquals(1, graph.prerequisites().get(0).depth());
        assertEquals(0.2, graph.prerequisites().get(0).masteryScore());
        assertEquals(2, graph.prerequisites().get(1).depth());
        assertTrue(graph.prerequisites().stream().anyMatch(path -> path.conceptKey().equals("数集")
                && path.deficit() > 0.8));
    }

    @Test
    void shouldRejectCycleAcrossKnowledgeSourcesBeforePersistingEdges() {
        EducationConceptDependencyRepository repository = mock(EducationConceptDependencyRepository.class);
        EducationKnowledgeGraphService service = new EducationKnowledgeGraphService(repository,
                new SensitiveDataSanitizer());
        when(repository
                .findByTenantIdAndSubjectAndGradeLevelAndCurriculumVersionOrderByConceptKeyAscPrerequisiteConceptAsc(
                        "tenant-a", "编程", "大一", "课程版"))
                .thenReturn(List.of(edge("B", "A", "doc-existing")));
        EducationKnowledgeSource source = new EducationKnowledgeSource(
                "tenant-a", "doc-new", "编程", "大一", "课程版", "循环", "",
                "A", "B", 3, "TEXTBOOK");

        var exception = assertThrows(org.mingharness.common.BusinessException.class,
                () -> service.replaceDerivedEdges(source));

        assertEquals("EDUCATION_DEPENDENCY_CYCLE", exception.getCode());
        org.mockito.Mockito.verify(repository, never()).saveAll(any());
    }

    private EducationConceptDependency edge(String concept, String prerequisite, String documentId) {
        return new EducationConceptDependency("tenant-a", "数学", "高中一年级", "人教A版",
                concept, prerequisite, documentId, "PREREQUISITE", 1.0);
    }
}
