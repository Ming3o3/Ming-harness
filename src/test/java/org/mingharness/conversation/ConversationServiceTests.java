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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证多轮消息、上下文延续和租户/用户边界。 */
@SpringBootTest
class ConversationServiceTests {

    private static final Path WORKSPACE_ROOT = createWorkspaceRoot();

    @Autowired
    private ConversationService conversationService;
    @Autowired
    private ConversationRepository conversationRepository;
    @Autowired
    private ConversationMessageRepository messageRepository;
    @Autowired
    private ConversationAttachmentRepository attachmentRepository;
    @Autowired
    private RunRepository runRepository;
    @Autowired
    private AuditEventRepository auditEventRepository;

    @DynamicPropertySource
    static void configureWorkspace(DynamicPropertyRegistry registry) {
        registry.add("harness.workspace.enabled", () -> true);
        registry.add("harness.workspace.root", () -> WORKSPACE_ROOT.toString());
    }

    @BeforeEach
    void cleanDatabase() {
        attachmentRepository.deleteAll();
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

    @Test
    void shouldImportTextAttachmentAndPassWorkspacePathToAgent() throws IOException {
        ConversationDetail created = conversationService.create(
                "tenant-chat", "operator", new CreateConversationRequest("附件测试"));
        MockMultipartFile source = new MockMultipartFile("files", "Example.java", "text/plain",
                "class Example { }".getBytes());

        var uploaded = conversationService.upload(created.conversation().id(), "tenant-chat", "operator",
                List.of(source));
        assertEquals(1, uploaded.size());
        assertEquals("Example.java", uploaded.get(0).originalName());
        assertTrue(Files.readString(WORKSPACE_ROOT.resolve(uploaded.get(0).workspacePath()))
                .contains("class Example"));

        ConversationDetail detail = conversationService.send(created.conversation().id(), "tenant-chat", "operator",
                new SendConversationMessageRequest("请读取附件", null, 2,
                        List.of(uploaded.get(0).id())),
                "chat-attachment-1", "run.create,run.execute,workspace.read");
        var userMessage = detail.messages().get(0);
        assertEquals(1, userMessage.attachments().size());
        assertEquals(uploaded.get(0).workspacePath(), userMessage.attachments().get(0).workspacePath());
        assertTrue(runRepository.findById(userMessage.runId()).orElseThrow().getInput()
                .contains(uploaded.get(0).workspacePath()));
    }

    @Test
    void shouldImportFolderAsOneAttachmentAndPreserveRelativePaths() throws IOException {
        ConversationDetail created = conversationService.create(
                "tenant-chat", "operator", new CreateConversationRequest("目录附件测试"));
        List<MultipartFile> files = List.of(
                new MockMultipartFile("files", "App.java", "text/plain", "class App { }".getBytes()),
                new MockMultipartFile("files", "README.md", "text/markdown", "# Demo".getBytes()));

        var uploaded = conversationService.upload(created.conversation().id(), "tenant-chat", "operator", files,
                List.of("demo-project/src/App.java", "demo-project/README.md"));
        assertEquals(1, uploaded.size());
        assertEquals("demo-project", uploaded.get(0).originalName());
        assertTrue(uploaded.get(0).directory());
        assertEquals(2, uploaded.get(0).fileCount());
        Path importedRoot = WORKSPACE_ROOT.resolve(uploaded.get(0).workspacePath());
        assertTrue(Files.readString(importedRoot.resolve("src/App.java")).contains("class App"));
        assertTrue(Files.readString(importedRoot.resolve("README.md")).contains("# Demo"));

        ConversationDetail detail = conversationService.send(created.conversation().id(), "tenant-chat", "operator",
                new SendConversationMessageRequest("请分析导入的项目", null, 2, List.of(uploaded.get(0).id())),
                "chat-folder-attachment", "run.create,run.execute,workspace.read");
        String runInput = runRepository.findById(detail.messages().get(0).runId()).orElseThrow().getInput();
        assertTrue(runInput.contains(uploaded.get(0).workspacePath()));
        assertTrue(runInput.contains("workspace.list"));
    }

    @Test
    void shouldRejectCrossConversationAndBinaryAttachment() {
        ConversationDetail first = conversationService.create(
                "tenant-chat", "operator", new CreateConversationRequest("附件归属"));
        ConversationDetail second = conversationService.create(
                "tenant-chat", "other-user", new CreateConversationRequest("其他用户"));
        var uploaded = conversationService.upload(first.conversation().id(), "tenant-chat", "operator",
                List.of(new MockMultipartFile("files", "note.txt", "text/plain", "内容".getBytes())));

        assertThrows(BusinessException.class, () -> conversationService.send(
                second.conversation().id(), "tenant-chat", "other-user",
                new SendConversationMessageRequest("请读取附件", null, 2, List.of(uploaded.get(0).id())),
                "chat-cross-attachment", "run.create"));
        assertThrows(BusinessException.class, () -> conversationService.upload(
                first.conversation().id(), "tenant-chat", "operator",
                List.of(new MockMultipartFile("files", "binary.bin", "application/octet-stream",
                        new byte[]{1, 0, 2}))));
    }

    @Test
    void shouldDiscardUnboundAttachmentAfterMessageSubmissionFailure() {
        ConversationDetail created = conversationService.create(
                "tenant-chat", "operator", new CreateConversationRequest("附件回收"));
        var uploaded = conversationService.upload(created.conversation().id(), "tenant-chat", "operator",
                List.of(new MockMultipartFile("files", "temporary.txt", "text/plain", "临时内容".getBytes())),
                List.of("temporary-folder/temporary.txt"));

        conversationService.discardPendingAttachment(created.conversation().id(), uploaded.get(0).id(),
                "tenant-chat", "operator");
        assertFalse(Files.exists(WORKSPACE_ROOT.resolve(uploaded.get(0).workspacePath())));
        assertThrows(BusinessException.class, () -> conversationService.send(
                created.conversation().id(), "tenant-chat", "operator",
                new SendConversationMessageRequest("已回收附件", null, 2, List.of(uploaded.get(0).id())),
                "chat-discarded-attachment", "run.create"));
    }

    private static Path createWorkspaceRoot() {
        try {
            return Files.createTempDirectory("ming-harness-conversation-test-");
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
