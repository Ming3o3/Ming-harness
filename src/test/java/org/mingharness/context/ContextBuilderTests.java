package org.mingharness.context;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mingharness.config.ContextRetrievalProperties;
import org.mingharness.context.api.ContextEvidence;
import org.mingharness.context.api.ContextResult;
import org.mingharness.education.EducationKnowledgeSource;
import org.mingharness.education.EducationKnowledgeSourceRepository;
import org.mingharness.education.EducationDependencyGraph;
import org.mingharness.education.EducationDependencyPath;
import org.mingharness.education.EducationKnowledgeGraphService;
import org.mingharness.education.EducationRetrievalFilter;
import org.mingharness.education.EducationRetrievalStrategy;
import org.mingharness.observability.HarnessMetrics;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
        when(sourceRepository.findByTenantIdAndDeletedAtIsNullOrderByUpdatedAtDesc("tenant-a"))
                .thenReturn(List.of(mathSource, physicsSource));
        when(documentRepository.findByTenantIdAndIdInAndDeletedAtIsNullOrderByCreatedAtDesc(
                "tenant-a", List.of(math.getId()))).thenReturn(List.of(math));
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
    void shouldFindMatchingCourseSourceOutsideTheGenericRecentDocumentWindow() {
        KnowledgeDocumentRepository documentRepository = mock(KnowledgeDocumentRepository.class);
        MemoryEntryRepository memoryRepository = mock(MemoryEntryRepository.class);
        VectorContextRetriever vectorRetriever = mock(VectorContextRetriever.class);
        EducationKnowledgeSourceRepository sourceRepository = mock(EducationKnowledgeSourceRepository.class);
        HarnessMetrics metrics = new HarnessMetrics(new SimpleMeterRegistry());
        ContextBuilder builder = new ContextBuilder(documentRepository, memoryRepository, vectorRetriever,
                metrics, new ContextRetrievalProperties(20, 5, 1, 0.2), sourceRepository);
        KnowledgeDocument courseDocument = new KnowledgeDocument("tenant-a", "teacher", "函数课件",
                "函数定义域需要先排除分母为零的情况", "INTERNAL", "student");
        EducationKnowledgeSource courseSource = new EducationKnowledgeSource(
                "tenant-a", courseDocument.getId(), "数学", "高中一年级", "人教A版", "第一章",
                "理解函数定义域", "函数,定义域", "集合", 3, "TEXTBOOK");
        EducationRetrievalFilter filter = new EducationRetrievalFilter(
                "数学", "高中一年级", "人教A版", "函数", null, null);
        when(vectorRetriever.retrieve("tenant-a", "student", "函数", 4_000, filter))
                .thenReturn(new ContextResult("", List.of()));
        when(sourceRepository.findByTenantIdAndDeletedAtIsNullOrderByUpdatedAtDesc("tenant-a"))
                .thenReturn(List.of(courseSource));
        when(sourceRepository.findByTenantIdAndDocumentIdAndDeletedAtIsNull(
                "tenant-a", courseDocument.getId())).thenReturn(Optional.of(courseSource));
        when(documentRepository.findByTenantIdAndIdInAndDeletedAtIsNullOrderByCreatedAtDesc(
                "tenant-a", List.of(courseDocument.getId()))).thenReturn(List.of(courseDocument));

        ContextResult result = builder.build("tenant-a", "student", "函数", 4_000, filter);

        assertEquals(1, result.evidences().size());
        assertEquals(courseDocument.getId(), result.evidences().get(0).documentId());
        verify(documentRepository, never())
                .findTop100ByTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc("tenant-a");
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
        when(sourceRepository.findByTenantIdAndDeletedAtIsNullOrderByUpdatedAtDesc("tenant-a"))
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
        when(sourceRepository.findByTenantIdAndDeletedAtIsNullOrderByUpdatedAtDesc("tenant-a"))
                .thenReturn(List.of(hardSource, easySource));
        when(documentRepository.findByTenantIdAndIdInAndDeletedAtIsNullOrderByCreatedAtDesc(
                "tenant-a", List.of(hard.getId(), easy.getId()))).thenReturn(List.of());
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
        when(sourceRepository.findByTenantIdAndDeletedAtIsNullOrderByUpdatedAtDesc("tenant-a"))
                .thenReturn(List.of(narrowerGapSource, broaderGapSource));
        when(documentRepository.findByTenantIdAndIdInAndDeletedAtIsNullOrderByCreatedAtDesc(
                "tenant-a", List.of(narrowerGap.getId(), broaderGap.getId()))).thenReturn(List.of());
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

    @Test
    void shouldKeepTargetAnchorBeforePrerequisiteSupplement() {
        KnowledgeDocumentRepository documentRepository = mock(KnowledgeDocumentRepository.class);
        MemoryEntryRepository memoryRepository = mock(MemoryEntryRepository.class);
        VectorContextRetriever vectorRetriever = mock(VectorContextRetriever.class);
        EducationKnowledgeSourceRepository sourceRepository = mock(EducationKnowledgeSourceRepository.class);
        EducationKnowledgeGraphService graphService = mock(EducationKnowledgeGraphService.class);
        ContextBuilder builder = new ContextBuilder(documentRepository, memoryRepository, vectorRetriever,
                new HarnessMetrics(new SimpleMeterRegistry()),
                new ContextRetrievalProperties(20, 5, 1, 0.2), sourceRepository, graphService);

        KnowledgeDocument target = new KnowledgeDocument("tenant-a", "teacher", "函数目标讲解",
                "函数目标定义", "INTERNAL", "student");
        KnowledgeDocument supplement = new KnowledgeDocument("tenant-a", "teacher", "集合前置补强",
                "集合基础知识", "INTERNAL", "student");
        EducationKnowledgeSource targetSource = new EducationKnowledgeSource("tenant-a", target.getId(),
                "数学", "高中一年级", "人教A版", "函数", "函数目标",
                "函数", "", 3, "TEXTBOOK");
        EducationKnowledgeSource supplementSource = new EducationKnowledgeSource("tenant-a", supplement.getId(),
                "数学", "高中一年级", "人教A版", "函数", "集合前置",
                "集合", "", 2, "TEXTBOOK");
        EducationRetrievalFilter filter = new EducationRetrievalFilter(
                "数学", "高中一年级", "人教A版", "函数", null, null,
                Map.of("函数", 0.10, "集合", 0.0));
        EducationDependencyGraph graph = new EducationDependencyGraph("函数", List.of(
                new EducationDependencyPath("集合", 1, 0.0, 1.0)), false);

        when(graphService.resolve("tenant-a", filter)).thenReturn(graph);
        when(vectorRetriever.retrieve("tenant-a", "student", "函数", 4_000, filter))
                .thenReturn(new ContextResult("vector-context", List.of(
                        new ContextEvidence(supplement.getId(), "集合前置补强",
                                "document:" + supplement.getId(), "集合", 1.0, "", List.of()),
                        new ContextEvidence(target.getId(), "函数目标讲解",
                                "document:" + target.getId(), "函数", 0.1, "", List.of()))));
        when(sourceRepository.findByTenantIdAndDeletedAtIsNullOrderByUpdatedAtDesc("tenant-a"))
                .thenReturn(List.of(targetSource, supplementSource));
        when(documentRepository.findByTenantIdAndIdInAndDeletedAtIsNullOrderByCreatedAtDesc(
                "tenant-a", List.of(target.getId(), supplement.getId())))
                .thenReturn(List.of(target, supplement));
        when(sourceRepository.findByTenantIdAndDocumentIdAndDeletedAtIsNull("tenant-a", target.getId()))
                .thenReturn(Optional.of(targetSource));
        when(sourceRepository.findByTenantIdAndDocumentIdAndDeletedAtIsNull("tenant-a", supplement.getId()))
                .thenReturn(Optional.of(supplementSource));

        ContextResult result = builder.build("tenant-a", "student", "函数", 4_000, filter);

        assertEquals(2, result.evidences().size());
        assertEquals(target.getId(), result.evidences().get(0).documentId());
        assertTrue(result.evidences().get(0).rankingReason().contains("目标证据锚点"));
        assertEquals(supplement.getId(), result.evidences().get(1).documentId());
        assertEquals(List.of("集合"), result.evidences().get(1).prerequisiteGaps());
    }

    @Test
    void shouldExplainOnlyPrerequisiteGapsCoveredByEachSource() {
        KnowledgeDocumentRepository documentRepository = mock(KnowledgeDocumentRepository.class);
        MemoryEntryRepository memoryRepository = mock(MemoryEntryRepository.class);
        VectorContextRetriever vectorRetriever = mock(VectorContextRetriever.class);
        EducationKnowledgeSourceRepository sourceRepository = mock(EducationKnowledgeSourceRepository.class);
        EducationKnowledgeGraphService graphService = mock(EducationKnowledgeGraphService.class);
        HarnessMetrics metrics = new HarnessMetrics(new SimpleMeterRegistry());
        ContextBuilder builder = new ContextBuilder(documentRepository, memoryRepository, vectorRetriever,
                metrics, new ContextRetrievalProperties(20, 5, 1, 0.2), sourceRepository, graphService);

        KnowledgeDocument targetOnly = new KnowledgeDocument("tenant-a", "teacher", "目标讲解",
                "二次函数定义", "INTERNAL", "student");
        KnowledgeDocument prerequisite = new KnowledgeDocument("tenant-a", "teacher", "定义域补强",
                "定义域例题", "INTERNAL", "student");
        EducationKnowledgeSource targetSource = new EducationKnowledgeSource("tenant-a", targetOnly.getId(),
                "数学", "高中一年级", "人教A版", "函数", "目标讲解",
                "二次函数", "函数", 3, "TEXTBOOK");
        EducationKnowledgeSource prerequisiteSource = new EducationKnowledgeSource("tenant-a", prerequisite.getId(),
                "数学", "高中一年级", "人教A版", "函数", "定义域补强",
                "定义域", "集合", 3, "TEXTBOOK");
        EducationRetrievalFilter filter = new EducationRetrievalFilter(
                "数学", "高中一年级", "人教A版", null, null, null,
                Map.of("集合", 0.1, "定义域", 0.1));
        EducationDependencyGraph graph = new EducationDependencyGraph("二次函数", List.of(
                new EducationDependencyPath("集合", 2, 0.1, 0.9),
                new EducationDependencyPath("定义域", 1, 0.1, 0.9)), false);
        when(graphService.resolve("tenant-a", filter)).thenReturn(graph);
        when(vectorRetriever.retrieve("tenant-a", "student", "函数", 4_000, filter))
                .thenReturn(new ContextResult("vector-context", List.of(
                        new ContextEvidence(targetOnly.getId(), "目标讲解", "document:" + targetOnly.getId(), "目标"),
                        new ContextEvidence(prerequisite.getId(), "定义域补强", "document:" + prerequisite.getId(), "前置"))));
        when(sourceRepository.findByTenantIdAndDocumentIdAndDeletedAtIsNull("tenant-a", targetOnly.getId()))
                .thenReturn(Optional.of(targetSource));
        when(sourceRepository.findByTenantIdAndDocumentIdAndDeletedAtIsNull("tenant-a", prerequisite.getId()))
                .thenReturn(Optional.of(prerequisiteSource));

        ContextResult result = builder.build("tenant-a", "student", "函数", 4_000, filter);

        ContextEvidence prerequisiteEvidence = result.evidences().stream()
                .filter(item -> item.documentId().equals(prerequisite.getId())).findFirst().orElseThrow();
        ContextEvidence targetEvidence = result.evidences().stream()
                .filter(item -> item.documentId().equals(targetOnly.getId())).findFirst().orElseThrow();
        assertEquals(List.of("定义域"), prerequisiteEvidence.prerequisiteGaps());
        assertTrue(targetEvidence.prerequisiteGaps().isEmpty());
        assertTrue(prerequisiteEvidence.rankingBreakdown().graphCoverage() > 0.0);
        assertEquals("MID_MASTERY_BALANCED",
                prerequisiteEvidence.rankingBreakdown().weights().conditioning());
    }

    @Test
    void shouldRunVectorOnlyBaselineWithoutKeywordRecallOrLearnerStateGraph() {
        KnowledgeDocumentRepository documentRepository = mock(KnowledgeDocumentRepository.class);
        MemoryEntryRepository memoryRepository = mock(MemoryEntryRepository.class);
        VectorContextRetriever vectorRetriever = mock(VectorContextRetriever.class);
        EducationKnowledgeSourceRepository sourceRepository = mock(EducationKnowledgeSourceRepository.class);
        EducationKnowledgeGraphService graphService = mock(EducationKnowledgeGraphService.class);
        ContextBuilder builder = new ContextBuilder(documentRepository, memoryRepository, vectorRetriever,
                new HarnessMetrics(new SimpleMeterRegistry()),
                new ContextRetrievalProperties(20, 5, 1, 0.2), sourceRepository, graphService);
        EducationRetrievalFilter filter = new EducationRetrievalFilter(
                "数学", "高中一年级", "人教A版", "函数", null, null, Map.of("函数", 0.1));
        when(vectorRetriever.retrieve("tenant-a", "student", "函数", 2_000, filter))
                .thenReturn(new ContextResult("vector", List.of(new ContextEvidence(
                        "doc-1", "函数课件", "document:doc-1", "向量片段"))));

        ContextResult result = builder.build("tenant-a", "student", "函数", 2_000, filter,
                EducationRetrievalStrategy.VECTOR_ONLY);

        assertEquals(1, result.evidences().size());
        verify(vectorRetriever).retrieve("tenant-a", "student", "函数", 2_000, filter);
        verifyNoInteractions(documentRepository, memoryRepository, sourceRepository, graphService);
    }

    @Test
    void shouldRunKeywordOnlyBaselineWithoutVectorRecallOrLearnerStateGraph() {
        KnowledgeDocumentRepository documentRepository = mock(KnowledgeDocumentRepository.class);
        MemoryEntryRepository memoryRepository = mock(MemoryEntryRepository.class);
        VectorContextRetriever vectorRetriever = mock(VectorContextRetriever.class);
        EducationKnowledgeSourceRepository sourceRepository = mock(EducationKnowledgeSourceRepository.class);
        EducationKnowledgeGraphService graphService = mock(EducationKnowledgeGraphService.class);
        ContextBuilder builder = new ContextBuilder(documentRepository, memoryRepository, vectorRetriever,
                new HarnessMetrics(new SimpleMeterRegistry()),
                new ContextRetrievalProperties(20, 5, 1, 0.2), sourceRepository, graphService);
        KnowledgeDocument document = new KnowledgeDocument("tenant-a", "teacher", "函数课件",
                "函数定义域", "INTERNAL", "student");
        EducationKnowledgeSource source = new EducationKnowledgeSource("tenant-a", document.getId(),
                "数学", "高中一年级", "人教A版", "函数", "函数",
                "函数", "集合", 3, "TEXTBOOK");
        EducationRetrievalFilter filter = new EducationRetrievalFilter(
                "数学", "高中一年级", "人教A版", "函数", null, null, Map.of("函数", 0.1));
        when(sourceRepository.findByTenantIdAndDeletedAtIsNullOrderByUpdatedAtDesc("tenant-a"))
                .thenReturn(List.of(source));
        when(sourceRepository.findByTenantIdAndDocumentIdAndDeletedAtIsNull("tenant-a", document.getId()))
                .thenReturn(Optional.of(source));
        when(documentRepository.findByTenantIdAndIdInAndDeletedAtIsNullOrderByCreatedAtDesc(
                "tenant-a", List.of(document.getId()))).thenReturn(List.of(document));

        ContextResult result = builder.build("tenant-a", "student", "函数", 2_000, filter,
                EducationRetrievalStrategy.KEYWORD_ONLY);

        assertEquals(1, result.evidences().size());
        verify(vectorRetriever, never()).retrieve("tenant-a", "student", "函数", 2_000, filter);
        verifyNoInteractions(memoryRepository, graphService);
    }

    @Test
    void shouldSkipDependencyGraphForNoLearnerStateAblation() {
        KnowledgeDocumentRepository documentRepository = mock(KnowledgeDocumentRepository.class);
        MemoryEntryRepository memoryRepository = mock(MemoryEntryRepository.class);
        VectorContextRetriever vectorRetriever = mock(VectorContextRetriever.class);
        EducationKnowledgeSourceRepository sourceRepository = mock(EducationKnowledgeSourceRepository.class);
        EducationKnowledgeGraphService graphService = mock(EducationKnowledgeGraphService.class);
        ContextBuilder builder = new ContextBuilder(documentRepository, memoryRepository, vectorRetriever,
                new HarnessMetrics(new SimpleMeterRegistry()),
                new ContextRetrievalProperties(20, 5, 1, 0.2), sourceRepository, graphService);
        EducationRetrievalFilter filter = new EducationRetrievalFilter(
                "数学", "高中一年级", "人教A版", "函数", null, null, Map.of());
        when(vectorRetriever.retrieve("tenant-a", "student", "函数", 2_000, filter))
                .thenReturn(new ContextResult("", List.of()));
        when(sourceRepository.findByTenantIdAndDeletedAtIsNullOrderByUpdatedAtDesc("tenant-a"))
                .thenReturn(List.of());

        builder.build("tenant-a", "student", "函数", 2_000, filter,
                EducationRetrievalStrategy.NO_LEARNER_STATE);

        verifyNoInteractions(graphService);
    }

    @Test
    void shouldKeepLearnerStateButDisableAdaptiveWeightsForStaticWeightAblation() {
        KnowledgeDocumentRepository documentRepository = mock(KnowledgeDocumentRepository.class);
        MemoryEntryRepository memoryRepository = mock(MemoryEntryRepository.class);
        VectorContextRetriever vectorRetriever = mock(VectorContextRetriever.class);
        EducationKnowledgeSourceRepository sourceRepository = mock(EducationKnowledgeSourceRepository.class);
        EducationKnowledgeGraphService graphService = mock(EducationKnowledgeGraphService.class);
        ContextBuilder builder = new ContextBuilder(documentRepository, memoryRepository, vectorRetriever,
                new HarnessMetrics(new SimpleMeterRegistry()),
                new ContextRetrievalProperties(20, 5, 1, 0.2), sourceRepository, graphService);
        EducationKnowledgeSource source = new EducationKnowledgeSource("tenant-a", "doc-1",
                "数学", "高中一年级", "人教A版", "函数", "前置回顾",
                "函数", "集合", 3, "TEXTBOOK");
        EducationRetrievalFilter filter = new EducationRetrievalFilter(
                "数学", "高中一年级", "人教A版", "函数", null, null,
                java.util.Map.of("函数", 0.1, "集合", 0.1));
        when(vectorRetriever.retrieve("tenant-a", "student", "函数", 2_000, filter))
                .thenReturn(new ContextResult("vector", List.of(new ContextEvidence(
                        "doc-1", "前置回顾", "document:doc-1", "集合"))));
        when(sourceRepository.findByTenantIdAndDocumentIdAndDeletedAtIsNull("tenant-a", "doc-1"))
                .thenReturn(Optional.of(source));
        when(graphService.resolve("tenant-a", filter)).thenReturn(new EducationDependencyGraph(
                "函数", List.of(new EducationDependencyPath("集合", 1, 0.1, 0.9)), false));

        ContextResult result = builder.build("tenant-a", "student", "函数", 2_000, filter,
                EducationRetrievalStrategy.STATIC_WEIGHT);

        assertEquals("STATIC", result.evidences().get(0).rankingBreakdown().weights().conditioning());
        verify(graphService).resolve("tenant-a", filter);
    }

    @Test
    void shouldKeepAdaptiveLearnerWeightsButDisableDependencyGraphForGraphAblation() {
        KnowledgeDocument document = new KnowledgeDocument("tenant-a", "teacher", "函数讲解",
                "函数基础", "INTERNAL", "student");
        EducationKnowledgeSource source = new EducationKnowledgeSource("tenant-a", document.getId(),
                "数学", "高中一年级", "人教A版", "函数", "函数基础",
                "函数", "集合", 2, "TEXTBOOK");
        KnowledgeDocumentRepository documentRepository = mock(KnowledgeDocumentRepository.class);
        MemoryEntryRepository memoryRepository = mock(MemoryEntryRepository.class);
        VectorContextRetriever vectorRetriever = mock(VectorContextRetriever.class);
        EducationKnowledgeSourceRepository sourceRepository = mock(EducationKnowledgeSourceRepository.class);
        EducationKnowledgeGraphService graphService = mock(EducationKnowledgeGraphService.class);
        ContextBuilder builder = new ContextBuilder(documentRepository, memoryRepository, vectorRetriever,
                new HarnessMetrics(new SimpleMeterRegistry()),
                new ContextRetrievalProperties(20, 5, 1, 0.2), sourceRepository, graphService);
        EducationRetrievalFilter filter = new EducationRetrievalFilter(
                "数学", "高中一年级", "人教A版", "函数", null, null,
                Map.of("函数", 0.2, "集合", 0.1));
        when(vectorRetriever.retrieve("tenant-a", "student", "函数", 2_000, filter))
                .thenReturn(new ContextResult("vector", List.of(new ContextEvidence(
                        document.getId(), "函数讲解", "document:" + document.getId(), "基础"))));
        when(sourceRepository.findByTenantIdAndDocumentIdAndDeletedAtIsNull("tenant-a", document.getId()))
                .thenReturn(Optional.of(source));

        ContextResult result = builder.build("tenant-a", "student", "函数", 2_000, filter,
                EducationRetrievalStrategy.NO_DEPENDENCY_GRAPH);

        assertEquals(1, result.evidences().size());
        assertEquals("LOW_MASTERY_GAP_FIRST",
                result.evidences().get(0).rankingBreakdown().weights().conditioning());
        assertEquals(0.0, result.evidences().get(0).rankingBreakdown().graphCoverage());
        verifyNoInteractions(graphService);
    }
}
