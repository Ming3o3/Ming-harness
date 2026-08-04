package org.mingharness.context;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mingharness.common.BusinessException;
import org.mingharness.context.api.CreateDocumentRequest;
import org.mingharness.context.api.CreateMemoryRequest;
import org.mingharness.context.api.ContextResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class ContextServiceTests {

    @Autowired
    private ContextService contextService;
    @Autowired
    private ContextBuilder contextBuilder;
    @Autowired
    private KnowledgeDocumentRepository documentRepository;
    @Autowired
    private MemoryEntryRepository memoryRepository;

    @BeforeEach
    void cleanDatabase() {
        memoryRepository.deleteAll();
        documentRepository.deleteAll();
    }

    @Test
    void shouldFilterDocumentsByTenantAndUserBeforeRetrieval() {
        contextService.createDocument("tenant-a", "owner", new CreateDocumentRequest(
                "订单规则", "订单状态必须经过审核", "INTERNAL", "operator"));
        contextService.createDocument("tenant-a", "owner", new CreateDocumentRequest(
                "私有规则", "只有 other 可以看到的订单规则", "INTERNAL", "other"));
        contextService.createDocument("tenant-b", "owner", new CreateDocumentRequest(
                "其他组织", "订单规则不能跨组织读取", "INTERNAL", "operator"));

        ContextResult result = contextBuilder.build("tenant-a", "operator", "订单规则", 4_000);

        assertEquals(1, result.evidences().size());
        assertEquals("订单规则", result.evidences().get(0).title());
    }

    @Test
    void shouldRejectSensitiveLongTermMemory() {
        assertThrows(BusinessException.class, () -> contextService.createMemory("tenant-a", "operator",
                new CreateMemoryRequest("profile", "api_key=do-not-store", null, null)));
        assertThrows(BusinessException.class, () -> contextService.createMemory("tenant-a", "operator",
                new CreateMemoryRequest("profile", "Authorization: Bearer do-not-store", null, null)));
    }

    @Test
    void shouldSanitizeDocumentBeforeItCanBeRetrievedIntoModelContext() {
        KnowledgeDocument document = contextService.createDocument("tenant-a", "owner", new CreateDocumentRequest(
                "接口说明", "api_key=do-not-persist", "INTERNAL", "operator"));

        assertEquals("api_key=[REDACTED]", document.getContent());
        ContextResult result = contextBuilder.build("tenant-a", "operator", "接口说明", 4_000);
        assertEquals(1, result.evidences().size());
        assertEquals("api_key=[REDACTED]", result.evidences().get(0).excerpt());
    }

    @Test
    void shouldRetrieveOnlyActiveMemoryOwnedByCurrentUser() {
        MemoryEntry visible = contextService.createMemory("tenant-a", "operator",
                new CreateMemoryRequest("preference", "项目默认使用 Java 17", "run-visible", Instant.now().plusSeconds(60)));
        contextService.createMemory("tenant-a", "other",
                new CreateMemoryRequest("preference", "其他用户的 Java 17 约束", "run-other", Instant.now().plusSeconds(60)));
        contextService.createMemory("tenant-a", "operator",
                new CreateMemoryRequest("stale", "过期的 Java 17 约束", "run-stale", Instant.now().minusSeconds(60)));

        ContextResult result = contextBuilder.build("tenant-a", "operator", "Java 17", 4_000);

        assertEquals(1, result.evidences().size());
        assertEquals(visible.getId(), result.evidences().get(0).documentId());
        assertTrue(result.text().contains("memory:" + visible.getId()));
        assertTrue(result.text().contains("项目默认使用 Java 17"));
    }

    @Test
    void shouldExpireAndDeleteUserMemory() {
        MemoryEntry memory = contextService.createMemory("tenant-a", "operator",
                new CreateMemoryRequest("preference", "偏好中文回答", "run-1", Instant.now().plusSeconds(60)));

        assertEquals(1, contextService.listMemories("tenant-a", "operator").size());
        contextService.deleteMemory("tenant-a", "operator", memory.getId());
        assertEquals(0, contextService.listMemories("tenant-a", "operator").size());
    }
}
