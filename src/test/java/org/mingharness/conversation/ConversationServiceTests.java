package org.mingharness.conversation;

import org.mingharness.common.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mingharness.audit.AuditEventRepository;
import org.mingharness.conversation.api.ConversationDetail;
import org.mingharness.conversation.api.CreateConversationRequest;
import org.mingharness.conversation.api.SendConversationMessageRequest;
import org.mingharness.education.LearnerMasteryRepository;
import org.mingharness.education.LearnerProfile;
import org.mingharness.education.LearnerProfileRepository;
import org.mingharness.education.api.EducationRunOptions;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证多轮消息、上下文延续和组织/用户边界。 */
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
    private ConversationContextRepository contextRepository;
    @Autowired
    private ConversationAttachmentRepository attachmentRepository;
    @Autowired
    private RunRepository runRepository;
    @Autowired
    private AuditEventRepository auditEventRepository;
    @Autowired
    private LearnerMasteryRepository learnerMasteryRepository;
    @Autowired
    private LearnerProfileRepository learnerProfileRepository;

    @DynamicPropertySource
    static void configureWorkspace(DynamicPropertyRegistry registry) {
        registry.add("harness.workspace.enabled", () -> true);
        registry.add("harness.workspace.root", () -> WORKSPACE_ROOT.toString());
    }

    @BeforeEach
    void cleanDatabase() {
        attachmentRepository.deleteAll();
        contextRepository.deleteAll();
        messageRepository.deleteAll();
        auditEventRepository.deleteAll();
        runRepository.deleteAll();
        learnerMasteryRepository.deleteAll();
        learnerProfileRepository.deleteAll();
        conversationRepository.deleteAll();
    }

    @Test
    void shouldCompactOldConversationHistoryBeforeCreatingNextRun() {
        Conversation conversation = conversationRepository.save(
                new Conversation("tenant-chat", "operator", "上下文压缩测试"));
        String longAnswer = "这是需要保留在完整消息历史中的旧事实。".repeat(450);
        for (int round = 0; round < 4; round++) {
            int userSequence = round * 2 + 1;
            messageRepository.save(new ConversationMessage(
                    conversation.getId(), null, "tenant-chat", "operator",
                    ConversationMessageRole.USER, ConversationMessageStatus.COMPLETED,
                    userSequence, "第 " + round + " 轮用户问题：" + longAnswer));
            messageRepository.save(new ConversationMessage(
                    conversation.getId(), null, "tenant-chat", "operator",
                    ConversationMessageRole.ASSISTANT, ConversationMessageStatus.COMPLETED,
                    userSequence + 1, "第 " + round + " 轮助手结论：" + longAnswer));
        }

        ConversationDetail detail = conversationService.send(
                conversation.getId(), "tenant-chat", "operator",
                new SendConversationMessageRequest("请基于前面的结论继续处理当前问题", null, 2),
                "chat-compaction-1", "run.create,run.execute,workspace.read");

        String runInput = runRepository.findById(detail.messages().get(8).runId())
                .orElseThrow().getInput();
        ConversationContext context = contextRepository.findById(conversation.getId()).orElse(null);
        assertNotNull(context);
        assertTrue(context.getCompactedThroughSequence() > 0);
        assertTrue(context.getSummary().contains("用户#") || context.getSummary().contains("助手#"));
        assertTrue(runInput.length() <= 10_000);
        assertTrue(runInput.contains("对话历史摘要"));
        assertTrue(runInput.contains("请基于前面的结论继续处理当前问题"));
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
        assertTrue(runRepository.findById(first.messages().get(0).runId()).orElseThrow().getInput()
                .contains("当前会话已连接到本地代码工作区"));

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
    void shouldSnapshotEducationConfigurationForConversationRun() {
        LearnerProfile profile = learnerProfileRepository.save(new LearnerProfile(
                "tenant-chat", "operator", "数学", "高中一年级", "人教A版", "掌握函数基础", "zh-CN"));
        ConversationDetail created = conversationService.create(
                "tenant-chat", "operator", new CreateConversationRequest("教育对话"));

        EducationRunOptions education = new EducationRunOptions(
                true, profile.getId(), null, null, null, "函数", 2, 4, "SOCRATIC");
        ConversationDetail detail = conversationService.send(
                created.conversation().id(), "tenant-chat", "operator",
                new SendConversationMessageRequest("请用提问方式帮助我理解函数", null, 4, List.of(), education),
                "chat-education-1", "run.create,run.execute,education.read,education.write");

        var run = runRepository.findById(detail.messages().get(0).runId()).orElseThrow();
        assertTrue(run.isEducationMode());
        assertEquals(profile.getId(), run.getEducationLearnerProfileId());
        assertEquals("数学", run.getEducationSubject());
        assertEquals("高中一年级", run.getEducationGradeLevel());
        assertEquals("人教A版", run.getEducationCurriculumVersion());
        assertEquals("SOCRATIC", run.getEducationPedagogicalMode());
        assertEquals("函数", run.getEducationConceptKey());
        assertEquals(2, run.getEducationMinDifficulty());
        assertEquals(4, run.getEducationMaxDifficulty());
    }

    @Test
    void shouldRejectEducationConversationWithoutEducationPermissions() {
        LearnerProfile profile = learnerProfileRepository.save(new LearnerProfile(
                "tenant-chat", "operator", "数学", "高中一年级", "人教A版", null, "zh-CN"));
        ConversationDetail created = conversationService.create(
                "tenant-chat", "operator", new CreateConversationRequest("教育权限"));
        EducationRunOptions education = new EducationRunOptions(
                true, profile.getId(), null, null, null, "函数", null, null, "AUTO");

        BusinessException error = assertThrows(BusinessException.class, () -> conversationService.send(
                created.conversation().id(), "tenant-chat", "operator",
                new SendConversationMessageRequest("请讲解函数", null, 2, List.of(), education),
                "chat-education-permission", "run.create,run.execute"));
        assertEquals("EDUCATION_PERMISSION_REQUIRED", error.getCode());
    }

    @Test
    void shouldAutoTitleDefaultConversationFromFirstMessageWithoutOverwritingCustomTitle() {
        ConversationDetail defaultConversation = conversationService.create(
                "tenant-chat", "operator", new CreateConversationRequest(null));
        ConversationDetail titled = conversationService.send(
                defaultConversation.conversation().id(), "tenant-chat", "operator",
                new SendConversationMessageRequest("请修复登录页提交按钮在移动端溢出的问题", null, 2),
                "chat-auto-title-1", "run.create,run.execute");

        assertEquals("请修复登录页提交按钮在移动端溢出的问题", titled.conversation().title());

        ConversationDetail customConversation = conversationService.create(
                "tenant-chat", "operator", new CreateConversationRequest("我自己的标题"));
        ConversationDetail preserved = conversationService.send(
                customConversation.conversation().id(), "tenant-chat", "operator",
                new SendConversationMessageRequest("这条消息不应覆盖已有标题", null, 2),
                "chat-auto-title-2", "run.create,run.execute");
        assertEquals("我自己的标题", preserved.conversation().title());
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
    void shouldRenameOnlyTheCurrentUsersConversation() {
        ConversationDetail created = conversationService.create(
                "tenant-chat", "operator", new CreateConversationRequest("新的对话"));

        ConversationDetail renamed = conversationService.rename(
                created.conversation().id(), "tenant-chat", "operator", "修复登录超时");

        assertEquals("修复登录超时", renamed.conversation().title());
        assertEquals("修复登录超时", conversationService.list("tenant-chat", "operator").get(0).title());
        assertThrows(BusinessException.class, () -> conversationService.rename(
                created.conversation().id(), "tenant-chat", "other-user", "越权修改"));
        assertThrows(BusinessException.class, () -> conversationService.rename(
                created.conversation().id(), "tenant-chat", "operator", "   "));
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
