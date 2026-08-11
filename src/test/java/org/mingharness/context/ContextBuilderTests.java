package org.mingharness.context;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mingharness.config.ContextRetrievalProperties;
import org.mingharness.context.api.ContextEvidence;
import org.mingharness.context.api.ContextResult;
import org.mingharness.education.EducationKnowledgeSource;
import org.mingharness.education.EducationKnowledgeSourceRepository;
import org.mingharness.education.EducationRetrievalFilter;
import org.mingharness.observability.HarnessMetrics;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContextBuilderTests {

    @Test
    void shouldUseRrfToPromoteAParentFoundByBothRetrievers() {
        KnowledgeDocumentRepository documentRepository = mock(KnowledgeDocumentRepository.class);
        MemoryEntryRepository memoryRepository = mock(MemoryEntryRepository.class);
        VectorContextRetriever vectorRetriever = mock(VectorContextRetriever.class);
        HarnessMetrics metrics = new HarnessMetrics(new SimpleMeterRegistry());
        ContextBuilder builder = new ContextBuilder(documentRepository, memoryRepository, vectorRetriever,
                metrics, new ContextRetrievalProperties(20, 5, 1, 0.2, true, 60, 1.0, 0.7));

        KnowledgeDocument vectorOnly = new KnowledgeDocument(
                "tenant-a", "owner", "向量来源", "没有关键词", "INTERNAL", "");
        KnowledgeDocument shared = new KnowledgeDocument(
                "tenant-a", "owner", "共享来源", "命中 回滚", "INTERNAL", "");
        KnowledgeDocument keywordOnly = new KnowledgeDocument(
                "tenant-a", "owner", "关键词来源", "命中", "INTERNAL", "");
        when(vectorRetriever.retrieve("tenant-a", "operator", "命中 回滚", 4_000))
                .thenReturn(new ContextResult("vector-context", List.of(
                        new ContextEvidence(vectorOnly.getId(), "向量来源",
                                "document:" + vectorOnly.getId() + "#window:0#chunk:0", "向量"),
                        new ContextEvidence(shared.getId(), "共享来源",
                                "document:" + shared.getId() + "#window:0#chunk:0", "共享"))));
        when(documentRepository.findTop100ByTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc("tenant-a"))
                .thenReturn(List.of(shared, keywordOnly, vectorOnly));
        when(memoryRepository.findTop100ByTenantIdAndUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                "tenant-a", "operator")).thenReturn(List.of());

        ContextResult result = builder.build("tenant-a", "operator", "命中 回滚", 4_000);

        assertEquals(3, result.evidences().size());
        assertEquals(shared.getId(), result.evidences().get(0).documentId());
        assertEquals(vectorOnly.getId(), result.evidences().get(1).documentId());
        assertEquals(keywordOnly.getId(), result.evidences().get(2).documentId());
        assertTrue(result.evidences().stream().noneMatch(evidence -> evidence.citation()
                .equals("document:" + shared.getId())));
    }

    @Test
    void shouldNotSupplementKeywordCopyWhenVectorAlreadyReturnedParentWindow() {
        KnowledgeDocumentRepository documentRepository = mock(KnowledgeDocumentRepository.class);
        MemoryEntryRepository memoryRepository = mock(MemoryEntryRepository.class);
        VectorContextRetriever vectorRetriever = mock(VectorContextRetriever.class);
        HarnessMetrics metrics = new HarnessMetrics(new SimpleMeterRegistry());
        ContextBuilder builder = new ContextBuilder(documentRepository, memoryRepository, vectorRetriever, metrics);

        KnowledgeDocument vectorDocument = new KnowledgeDocument(
                "tenant-a", "owner", "发布回滚", "回滚步骤和验证结果", "INTERNAL", "");
        KnowledgeDocument keywordOnlyDocument = new KnowledgeDocument(
                "tenant-a", "owner", "回滚监控", "回滚后检查监控", "INTERNAL", "");
        when(vectorRetriever.retrieve("tenant-a", "operator", "回滚", 2_000))
                .thenReturn(new ContextResult("vector-context", List.of(new ContextEvidence(
                        vectorDocument.getId(), "发布回滚",
                        "document:" + vectorDocument.getId() + "#window:0#chunk:1",
                        "标题\n回滚步骤和验证结果"))));
        when(documentRepository.findTop100ByTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc("tenant-a"))
                .thenReturn(List.of(vectorDocument, keywordOnlyDocument));
        when(memoryRepository.findTop100ByTenantIdAndUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                "tenant-a", "operator")).thenReturn(List.of());

        ContextResult result = builder.build("tenant-a", "operator", "回滚", 2_000);

        assertEquals(2, result.evidences().size());
        assertTrue(result.evidences().stream().anyMatch(evidence -> evidence.citation()
                .equals("document:" + vectorDocument.getId() + "#window:0#chunk:1")));
        assertTrue(result.evidences().stream().anyMatch(evidence -> evidence.documentId()
                .equals(keywordOnlyDocument.getId())));
        assertTrue(result.evidences().stream().noneMatch(evidence -> evidence.citation()
                .equals("document:" + vectorDocument.getId())));
    }

    @Test
    void shouldApplyEducationCourseConstraintsBeforeKeywordRecall() {
        KnowledgeDocumentRepository documentRepository = mock(KnowledgeDocumentRepository.class);
        MemoryEntryRepository memoryRepository = mock(MemoryEntryRepository.class);
        VectorContextRetriever vectorRetriever = mock(VectorContextRetriever.class);
        EducationKnowledgeSourceRepository sourceRepository = mock(EducationKnowledgeSourceRepository.class);
        HarnessMetrics metrics = new HarnessMetrics(new SimpleMeterRegistry());
        ContextBuilder builder = new ContextBuilder(documentRepository, memoryRepository, vectorRetriever,
                metrics, new ContextRetrievalProperties(20, 5, 1, 0.2), sourceRepository);

        KnowledgeDocument math = new KnowledgeDocument("tenant-a", "teacher", "函数课件",
                "函数定义域与值域", "INTERNAL", "student");
        KnowledgeDocument physics = new KnowledgeDocument("tenant-a", "teacher", "力学课件",
                "函数在物理中的应用", "INTERNAL", "student");
        EducationKnowledgeSource mathSource = new EducationKnowledgeSource("tenant-a", math.getId(),
                "数学", "高中一年级", "人教A版", "第一章", "理解函数",
                "函数,定义域", "集合", 3, "TEXTBOOK");
        EducationKnowledgeSource physicsSource = new EducationKnowledgeSource("tenant-a", physics.getId(),
                "物理", "高中一年级", "人教版", "第一章", "理解力学",
                "函数", "代数", 3, "TEXTBOOK");
        EducationRetrievalFilter filter = new EducationRetrievalFilter(
                "数学", "高中一年级", "人教A版", "函数", null, null);
        when(vectorRetriever.retrieve("tenant-a", "student", "函数", 4_000, filter))
                .thenReturn(new ContextResult("", List.of()));
        when(documentRepository.findTop100ByTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc("tenant-a"))
                .thenReturn(List.of(math, physics));
        when(memoryRepository.findTop100ByTenantIdAndUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                "tenant-a", "student")).thenReturn(List.of());
        when(sourceRepository.findByTenantIdAndDocumentIdAndDeletedAtIsNull("tenant-a", math.getId()))
                .thenReturn(Optional.of(mathSource));
        when(sourceRepository.findByTenantIdAndDocumentIdAndDeletedAtIsNull("tenant-a", physics.getId()))
                .thenReturn(Optional.of(physicsSource));

        ContextResult result = builder.build("tenant-a", "student", "函数", 4_000, filter);

        assertEquals(1, result.evidences().size());
        assertEquals(math.getId(), result.evidences().get(0).documentId());
    }

    @Test
    void shouldNotUsePersonalMemoryAsEducationKnowledge() {
        KnowledgeDocumentRepository documentRepository = mock(KnowledgeDocumentRepository.class);
        MemoryEntryRepository memoryRepository = mock(MemoryEntryRepository.class);
        VectorContextRetriever vectorRetriever = mock(VectorContextRetriever.class);
        EducationKnowledgeSourceRepository sourceRepository = mock(EducationKnowledgeSourceRepository.class);
        HarnessMetrics metrics = new HarnessMetrics(new SimpleMeterRegistry());
        ContextBuilder builder = new ContextBuilder(documentRepository, memoryRepository, vectorRetriever,
                metrics, new ContextRetrievalProperties(20, 5, 1, 0.2), sourceRepository);
        EducationRetrievalFilter filter = new EducationRetrievalFilter(
                "数学", "高中一年级", "人教A版", "函数", null, null);
        MemoryEntry memory = new MemoryEntry("tenant-a", "student", "note",
                "函数练习的私人笔记", "run-1", Instant.now().plusSeconds(3600));

        when(vectorRetriever.retrieve("tenant-a", "student", "函数", 4_000, filter))
                .thenReturn(new ContextResult("", List.of()));
        when(documentRepository.findTop100ByTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc("tenant-a"))
                .thenReturn(List.of());
        when(memoryRepository.findTop100ByTenantIdAndUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                "tenant-a", "student")).thenReturn(List.of(memory));

        ContextResult result = builder.build("tenant-a", "student", "函数", 4_000, filter);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldRerankEducationEvidenceByLearnerMasteryAndPrerequisiteGap() {
        KnowledgeDocumentRepository documentRepository = mock(KnowledgeDocumentRepository.class);
        MemoryEntryRepository memoryRepository = mock(MemoryEntryRepository.class);
        VectorContextRetriever vectorRetriever = mock(VectorContextRetriever.class);
        EducationKnowledgeSourceRepository sourceRepository = mock(EducationKnowledgeSourceRepository.class);
        HarnessMetrics metrics = new HarnessMetrics(new SimpleMeterRegistry());
        ContextBuilder builder = new ContextBuilder(documentRepository, memoryRepository, vectorRetriever,
                metrics, new ContextRetrievalProperties(20, 5, 1, 0.2), sourceRepository);

        KnowledgeDocument hard = new KnowledgeDocument("tenant-a", "teacher", "函数综合提升",
                "函数综合题", "INTERNAL", "student");
        KnowledgeDocument easy = new KnowledgeDocument("tenant-a", "teacher", "函数基础讲解",
                "函数基础", "INTERNAL", "student");
        EducationKnowledgeSource hardSource = new EducationKnowledgeSource("tenant-a", hard.getId(),
                "数学", "高中一年级", "人教A版", "函数", "掌握函数综合应用",
                "函数", "集合,定义域", 5, "TEXTBOOK");
        EducationKnowledgeSource easySource = new EducationKnowledgeSource("tenant-a", easy.getId(),
                "数学", "高中一年级", "人教A版", "函数", "掌握函数基础",
                "函数", "集合", 2, "TEXTBOOK");
        EducationRetrievalFilter filter = new EducationRetrievalFilter(
                "数学", "高中一年级", "人教A版", "函数", null, null,
                Map.of("函数", 0.20, "集合", 0.10, "定义域", 0.05));
        when(vectorRetriever.retrieve("tenant-a", "student", "函数", 4_000, filter))
                .thenReturn(new ContextResult("vector-context", List.of(
                        new ContextEvidence(hard.getId(), "函数综合提升", "document:" + hard.getId(), "综合"),
                        new ContextEvidence(easy.getId(), "函数基础讲解", "document:" + easy.getId(), "基础"))));
        when(documentRepository.findTop100ByTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc("tenant-a"))
                .thenReturn(List.of());
        when(memoryRepository.findTop100ByTenantIdAndUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                "tenant-a", "student")).thenReturn(List.of());
        when(sourceRepository.findByTenantIdAndDocumentIdAndDeletedAtIsNull("tenant-a", hard.getId()))
                .thenReturn(java.util.Optional.of(hardSource));
        when(sourceRepository.findByTenantIdAndDocumentIdAndDeletedAtIsNull("tenant-a", easy.getId()))
                .thenReturn(java.util.Optional.of(easySource));

        ContextResult result = builder.build("tenant-a", "student", "函数", 4_000, filter);

        assertEquals(2, result.evidences().size());
        assertEquals(easy.getId(), result.evidences().get(0).documentId());
        assertEquals(hard.getId(), result.evidences().get(1).documentId());
    }

    @Test
    void shouldUseChinesePrerequisiteDelimitersWhenRerankingByMastery() {
        KnowledgeDocumentRepository documentRepository = mock(KnowledgeDocumentRepository.class);
        MemoryEntryRepository memoryRepository = mock(MemoryEntryRepository.class);
        VectorContextRetriever vectorRetriever = mock(VectorContextRetriever.class);
        EducationKnowledgeSourceRepository sourceRepository = mock(EducationKnowledgeSourceRepository.class);
        HarnessMetrics metrics = new HarnessMetrics(new SimpleMeterRegistry());
        ContextBuilder builder = new ContextBuilder(documentRepository, memoryRepository, vectorRetriever,
                metrics, new ContextRetrievalProperties(20, 5, 1, 0.2), sourceRepository);

        KnowledgeDocument broaderGap = new KnowledgeDocument("tenant-a", "teacher", "函数前置补强",
                "集合与定义域", "INTERNAL", "student");
        KnowledgeDocument narrowerGap = new KnowledgeDocument("tenant-a", "teacher", "函数基础回顾",
                "集合复习", "INTERNAL", "student");
        EducationKnowledgeSource broaderGapSource = new EducationKnowledgeSource("tenant-a", broaderGap.getId(),
                "数学", "高中一年级", "人教A版", "函数", "函数前置补强",
                "函数", "集合；定义域", 4, "TEXTBOOK");
        EducationKnowledgeSource narrowerGapSource = new EducationKnowledgeSource("tenant-a", narrowerGap.getId(),
                "数学", "高中一年级", "人教A版", "函数", "函数基础回顾",
                "函数", "集合", 4, "TEXTBOOK");
        EducationRetrievalFilter filter = new EducationRetrievalFilter(
                "数学", "高中一年级", "人教A版", "函数", null, null,
                Map.of("函数", 0.80, "集合", 0.50, "定义域", 0.0));
        when(vectorRetriever.retrieve("tenant-a", "student", "函数", 4_000, filter))
                .thenReturn(new ContextResult("vector-context", List.of(
                        new ContextEvidence(narrowerGap.getId(), "函数基础回顾",
                                "document:" + narrowerGap.getId(), "集合"),
                        new ContextEvidence(broaderGap.getId(), "函数前置补强",
                                "document:" + broaderGap.getId(), "集合与定义域"))));
        when(documentRepository.findTop100ByTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc("tenant-a"))
                .thenReturn(List.of());
        when(memoryRepository.findTop100ByTenantIdAndUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                "tenant-a", "student")).thenReturn(List.of());
        when(sourceRepository.findByTenantIdAndDocumentIdAndDeletedAtIsNull("tenant-a", broaderGap.getId()))
                .thenReturn(Optional.of(broaderGapSource));
        when(sourceRepository.findByTenantIdAndDocumentIdAndDeletedAtIsNull("tenant-a", narrowerGap.getId()))
                .thenReturn(Optional.of(narrowerGapSource));

        ContextResult result = builder.build("tenant-a", "student", "函数", 4_000, filter);

        assertEquals(broaderGap.getId(), result.evidences().get(0).documentId());
        assertEquals(narrowerGap.getId(), result.evidences().get(1).documentId());
    }
}
