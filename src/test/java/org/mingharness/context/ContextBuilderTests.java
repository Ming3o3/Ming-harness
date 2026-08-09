package org.mingharness.context;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mingharness.config.ContextRetrievalProperties;
import org.mingharness.context.api.ContextEvidence;
import org.mingharness.context.api.ContextResult;
import org.mingharness.observability.HarnessMetrics;

import java.util.List;

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
}
