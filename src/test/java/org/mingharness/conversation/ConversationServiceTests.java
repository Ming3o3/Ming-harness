package org.mingharness.conversation;

import org.mingharness.common.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mingharness.audit.AuditEventRepository;
import org.mingharness.conversation.api.ConversationDetail;
import org.mingharness.conversation.api.CreateConversationRequest;
import org.mingharness.conversation.api.SendConversationMessageRequest;
import org.mingharness.runtime.repository.RunRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证多轮消息、上下文延续和租户/用户边界。 */
@SpringBootTest
class ConversationServiceTests {

    @Autowired
    private ConversationService conversationService;
    @Autowired
    private ConversationRepository conversationRepository;
    @Autowired
    private ConversationMessageRepository messageRepository;
    @Autowired
    private RunRepository runRepository;
    @Autowired
    private AuditEventRepository auditEventRepository;

    @BeforeEach
    void cleanDatabase() {
        messageRepository.deleteAll();
        auditEventRepository.deleteAll();
        runRepository.deleteAll();
        conversationRepository.deleteAll();
    }

    @Test
    void shouldPersistUserAndAssistantBubblesForMultipleRounds() {
        ConversationDetail created = conversationService.create(
                "tenant-chat", "operator", new CreateConversationRequest("代码工作台"));

        ConversationDetail first = conversationService.send(
                created.conversation().id(), "tenant-chat", "operator",
                new SendConversationMessageRequest("请检查项目入口", null, 2),
                "chat-round-1", "run.create,run.execute");

        assertEquals(2, first.messages().size());
        assertEquals(ConversationMessageRole.USER, first.messages().get(0).role());
        assertEquals(ConversationMessageRole.ASSISTANT, first.messages().get(1).role());
        assertEquals(ConversationMessageStatus.COMPLETED, first.messages().get(1).status());
        assertTrue(first.messages().get(1).content().contains("请检查项目入口"));

        ConversationDetail second = conversationService.send(
                created.conversation().id(), "tenant-chat", "operator",
                new SendConversationMessageRequest("继续说明刚才的结果", null, 2),
                "chat-round-2", "run.create,run.execute");

        assertEquals(4, second.messages().size());
        assertEquals(4, second.conversation().messageCount());
        assertEquals(ConversationMessageStatus.COMPLETED, second.messages().get(3).status());
        assertTrue(runRepository.findById(second.messages().get(3).runId()).orElseThrow()
                .getInput().contains("请检查项目入口"));

        ConversationDetail replay = conversationService.send(
                created.conversation().id(), "tenant-chat", "operator",
                new SendConversationMessageRequest("继续说明刚才的结果", null, 2),
                "chat-round-2", "run.create,run.execute");
        assertEquals(4, replay.messages().size());
        assertEquals(second.messages().get(2).id(), replay.messages().get(2).id());
        assertEquals(second.messages().get(3).id(), replay.messages().get(3).id());

        assertThrows(BusinessException.class, () -> conversationService.send(
                created.conversation().id(), "tenant-chat", "operator",
                new SendConversationMessageRequest("使用同一个幂等键发送另一条消息", null, 2),
                "chat-round-2", "run.create,run.execute"));
    }

    @Test
    void shouldRejectOtherUserAndTenantAccess() {
        ConversationDetail created = conversationService.create(
                "tenant-chat", "operator", new CreateConversationRequest("隔离测试"));

        assertThrows(RuntimeException.class, () -> conversationService.detail(
                created.conversation().id(), "tenant-other", "operator"));
        assertThrows(RuntimeException.class, () -> conversationService.detail(
                created.conversation().id(), "tenant-chat", "other-user"));
    }
}
