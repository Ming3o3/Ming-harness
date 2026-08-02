package org.mingharness.conversation;

import org.mingharness.common.BusinessException;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.conversation.api.ConversationDetail;
import org.mingharness.conversation.api.ConversationAttachmentView;
import org.mingharness.conversation.api.ConversationMessageView;
import org.mingharness.conversation.api.ConversationSummary;
import org.mingharness.conversation.api.CreateConversationRequest;
import org.mingharness.conversation.api.SendConversationMessageRequest;
import org.mingharness.runtime.api.CreateRunRequest;
import org.mingharness.runtime.api.RunSummary;
import org.mingharness.runtime.application.RunService;
import org.mingharness.runtime.application.TenantPolicyService;
import org.mingharness.runtime.domain.Run;
import org.mingharness.runtime.domain.RunStatus;
import org.mingharness.runtime.repository.RunRepository;
import org.mingharness.tool.WorkspaceToolSupport;
import org.mingharness.workspace.WorkspaceDirectoryService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** 聊天会话应用服务：每条用户消息创建一个可审计、可恢复的 Agent Run。 */
@Service
public class ConversationService {

    /** 单条历史消息进入摘要时保留的短片段长度；完整正文仍保存在消息表中。 */
    private static final int MESSAGE_SUMMARY_CHARS = 240;
    /** 摘要目标约占一次运行输入预算的四分之一，为最近消息和当前问题留出空间。 */
    private static final int SUMMARY_BUDGET_DIVISOR = 4;
    private static final int MAX_ATTACHMENTS_PER_MESSAGE = 8;
    /** 文件夹按文件数限制，避免一次拖入大型项目占满服务内存和工作区。 */
    private static final int MAX_FILES_PER_UPLOAD = 200;
    private static final long MAX_UPLOAD_BYTES = 20L * 1024 * 1024;

    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository messageRepository;
    private final ConversationContextRepository contextRepository;
    private final ConversationAttachmentRepository attachmentRepository;
    private final RunRepository runRepository;
    private final RunService runService;
    private final TenantPolicyService tenantPolicyService;
    private final SensitiveDataSanitizer sanitizer;
    private final WorkspaceToolSupport workspace;
    private final WorkspaceDirectoryService workspaceDirectoryService;

    public ConversationService(ConversationRepository conversationRepository,
                               ConversationMessageRepository messageRepository,
                               ConversationContextRepository contextRepository,
                               ConversationAttachmentRepository attachmentRepository,
                               RunRepository runRepository,
                               RunService runService,
                               TenantPolicyService tenantPolicyService,
                               SensitiveDataSanitizer sanitizer,
                               WorkspaceToolSupport workspace,
                               WorkspaceDirectoryService workspaceDirectoryService) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.contextRepository = contextRepository;
        this.attachmentRepository = attachmentRepository;
        this.runRepository = runRepository;
        this.runService = runService;
        this.tenantPolicyService = tenantPolicyService;
        this.sanitizer = sanitizer;
        this.workspace = workspace;
        this.workspaceDirectoryService = workspaceDirectoryService;
    }

    @Transactional
    public ConversationDetail create(String tenantId, String userId, CreateConversationRequest request) {
        String title = request == null ? null : request.title();
        String workspaceId = request == null ? null : normalizeWorkspaceId(request.workspaceId());
        if (workspaceId != null) {
            workspaceDirectoryService.requireRoot(workspaceId, tenantId, userId);
        }
        Conversation conversation = new Conversation(tenantId, userId,
                sanitizer.sanitize(title == null || title.isBlank() ? Conversation.DEFAULT_TITLE : title.trim()), workspaceId);
        conversationRepository.save(conversation);
        return detail(conversation);
    }

    @Transactional(readOnly = true)
    public List<ConversationSummary> list(String tenantId, String userId) {
        return conversationRepository.findTop50ByTenantIdAndUserIdOrderByUpdatedAtDesc(tenantId, userId)
                .stream().map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public ConversationDetail detail(String conversationId, String tenantId, String userId) {
        return detail(load(conversationId, tenantId, userId));
    }

    /** 重命名不会改动会话消息、上下文快照、工作区绑定或关联 Run。 */
    @Transactional
    public ConversationDetail rename(String conversationId, String tenantId, String userId, String title) {
        Conversation conversation = loadForMessage(conversationId, tenantId, userId);
        String normalized = sanitizer.sanitize(title == null ? "" : title.trim());
        if (normalized.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "CONVERSATION_TITLE_REQUIRED", "会话标题不能为空");
        }
        conversation.rename(normalized);
        conversationRepository.save(conversation);
        return detail(conversation);
    }

    /**
     * 将浏览器拖入的 UTF-8 文本文件导入到受控工作区。
     *
     * <p>浏览器不会也不应暴露用户硬盘绝对路径；接口返回的 {@code workspacePath} 才是 Agent
     * 可使用的稳定相对路径。文件尚未绑定到消息，只有发送成功后才成为聊天历史的一部分。</p>
     */
    @Transactional
    public List<ConversationAttachmentView> upload(String conversationId, String tenantId, String userId,
                                                    List<MultipartFile> files) {
        return upload(conversationId, tenantId, userId, files, List.of());
    }

    /**
     * 导入文件或文件夹。{@code relativePaths} 与文件顺序一一对应，文件夹会保留其相对层级，
     * 并以一个目录附件根路径暴露给 Agent。
     */
    @Transactional
    public List<ConversationAttachmentView> upload(String conversationId, String tenantId, String userId,
                                                    List<MultipartFile> files, List<String> relativePaths) {
        Conversation conversation = loadForMessage(conversationId, tenantId, userId);
        if (files == null || files.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ATTACHMENT_REQUIRED", "至少需要上传一个文件");
        }
        if (files.size() > MAX_FILES_PER_UPLOAD) {
            throw new BusinessException(HttpStatus.PAYLOAD_TOO_LARGE, "ATTACHMENT_LIMIT_EXCEEDED",
                    "一次最多导入 " + MAX_FILES_PER_UPLOAD + " 个文本文件");
        }
        workspace.requireEnabled();

        List<PreparedAttachment> prepared = prepareAttachments(files, relativePaths);
        Map<String, AttachmentGroup> groups = groupAttachments(prepared);
        if (groups.size() > MAX_ATTACHMENTS_PER_MESSAGE) {
            throw new BusinessException(HttpStatus.PAYLOAD_TOO_LARGE, "ATTACHMENT_LIMIT_EXCEEDED",
                    "每条消息最多可携带 " + MAX_ATTACHMENTS_PER_MESSAGE + " 个文件或文件夹");
        }

        Path workspaceRoot = workspaceDirectoryService.requireRoot(conversation.getWorkspaceId(), tenantId, userId);
        return workspace.withRoot(workspaceRoot, () -> savePreparedAttachments(conversation, tenantId, userId, groups));
    }

    /** 附件存储沿用会话绑定的工作区，避免切换项目后把旧会话文件写入新的目录。 */
    private List<ConversationAttachmentView> savePreparedAttachments(Conversation conversation,
                                                                       String tenantId, String userId,
                                                                       Map<String, AttachmentGroup> groups) {
        List<Path> cleanupRoots = new ArrayList<>();
        List<ConversationAttachment> attachments = new ArrayList<>();
        try {
            for (AttachmentGroup group : groups.values()) {
                String workspacePath = attachmentWorkspacePath(conversation, storageFileName(group.rootName));
                cleanupRoots.add(workspace.resolve(workspacePath, true));
                for (PreparedAttachment item : group.files) {
                    String targetPath = group.workspacePathFor(workspacePath, item);
                    Path target = workspace.resolve(targetPath, true);
                    workspace.writeText(target, targetPath, item.content(), null);
                }
                attachments.add(new ConversationAttachment(conversation.getId(), tenantId, userId,
                        group.rootName, workspacePath, group.mediaType(), group.totalBytes(),
                        group.isDirectory(), group.files.size()));
            }
            attachmentRepository.saveAll(attachments);
            return attachments.stream().map(this::toAttachmentView).toList();
        } catch (RuntimeException exception) {
            // 数据库存储失败时尽力删除刚导入的文件或目录，避免工作区遗留不可引用的附件。
            for (Path path : cleanupRoots) {
                try {
                    deleteImportedPath(path);
                } catch (IOException ignored) {
                    // 原始异常更能说明请求失败原因，清理失败不覆盖它。
                }
            }
            throw exception;
        }
    }

    /** 发送失败或用户撤回时删除尚未绑定消息的附件，避免受控工作区积累孤儿文件。 */
    @Transactional
    public void discardPendingAttachment(String conversationId, String attachmentId,
                                         String tenantId, String userId) {
        Conversation conversation = loadForMessage(conversationId, tenantId, userId);
        ConversationAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "ATTACHMENT_NOT_FOUND",
                        "聊天附件不存在或已被删除"));
        if (!conversation.getId().equals(attachment.getConversationId())
                || !tenantId.equals(attachment.getTenantId())
                || !userId.equals(attachment.getUserId())) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "ATTACHMENT_NOT_FOUND", "聊天附件不存在或无权访问");
        }
        if (attachment.getMessageId() != null) {
            throw new BusinessException(HttpStatus.CONFLICT, "ATTACHMENT_ALREADY_USED", "已发送的聊天附件不能撤回");
        }
        // 文件可能已被运维清理；此时仍删除元数据，避免留下无法再次绑定的孤儿附件记录。
        Path workspaceRoot = workspaceDirectoryService.requireRoot(conversation.getWorkspaceId(), tenantId, userId);
        workspace.withRoot(workspaceRoot, () -> {
            Path storedFile = workspace.resolve(attachment.getWorkspacePath(), true);
            try {
                deleteImportedPath(storedFile);
            } catch (IOException exception) {
                throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "ATTACHMENT_DELETE_FAILED",
                        "删除工作区附件失败");
            }
            return null;
        });
        attachmentRepository.delete(attachment);
    }

    /** 创建一轮 USER + ASSISTANT 消息，并立即启动关联 Run。 */
    @Transactional
    public ConversationDetail send(String conversationId, String tenantId, String userId,
                                   SendConversationMessageRequest request, String idempotencyKey,
                                   String permissions) {
        Conversation conversation = loadForMessage(conversationId, tenantId, userId);
        String content = sanitizer.sanitize(request.content().trim());
        conversation.autoTitleFromFirstMessage(content);
        List<String> attachmentIds = request.effectiveAttachmentIds();
        String effectiveIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);
        if (sanitizer.containsSensitiveData(effectiveIdempotencyKey)) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "SENSITIVE_IDEMPOTENCY_KEY_REJECTED",
                    "幂等键不能包含疑似密钥或凭证");
        }

        Optional<Run> existing = effectiveIdempotencyKey == null
                ? Optional.empty()
                : runRepository.findByTenantIdAndIdempotencyKey(tenantId, effectiveIdempotencyKey);
        if (existing.isPresent()) {
            return replayExistingMessage(conversation, existing.get(), content, request, attachmentIds, permissions);
        }

        List<ConversationAttachment> attachments = loadPendingAttachments(
                conversation, tenantId, userId, attachmentIds);
        String runInput = buildPrompt(conversation.getId(), tenantId, content, attachments);
        CreateRunRequest runRequest = new CreateRunRequest(
                tenantId, userId, conversation.getTitle(), runInput,
                null, sanitizer.sanitize(request.modelName()), "prompt-v1", "policy-v1",
                BigDecimal.ONE, effectiveIdempotencyKey, permissions, true, request.effectiveMaxTurns(),
                conversation.getId(), conversation.getWorkspaceId());
        RunSummary run = runService.create(runRequest);

        ConversationMessage userMessage = messageRepository.findByRunIdAndRole(run.id(), ConversationMessageRole.USER)
                .orElseGet(() -> appendMessage(conversation, run.id(), ConversationMessageRole.USER,
                        ConversationMessageStatus.COMPLETED, content));
        attachToMessage(attachments, userMessage);
        // 幂等重放时不重复插入助手气泡；重试已有 Run 会先恢复该气泡为 PENDING。
        messageRepository.findByRunIdAndRole(run.id(), ConversationMessageRole.ASSISTANT)
                .orElseGet(() -> appendMessage(conversation, run.id(), ConversationMessageRole.ASSISTANT,
                        ConversationMessageStatus.PENDING, ""));
        conversation.touch();
        conversationRepository.save(conversation);

        if (run.status() == RunStatus.QUEUED) {
            runService.start(run.id(), tenantId);
        }
        return detail(conversation);
    }

    /**
     * 网络超时后的重放必须返回原始轮次，而不是把同一条用户消息再次拼进上下文。
     * 会话锁已由 {@link #loadForMessage(String, String, String)} 持有，因此读取和校验结果稳定。
     */
    private ConversationDetail replayExistingMessage(Conversation conversation, Run run, String content,
                                                     SendConversationMessageRequest request,
                                                     List<String> attachmentIds, String permissions) {
        if (!conversation.getId().equals(run.getConversationId())
                || !conversation.getUserId().equals(run.getUserId())) {
            throw idempotencyConflict();
        }
        ConversationMessage userMessage = messageRepository
                .findByRunIdAndRole(run.getId(), ConversationMessageRole.USER)
                .orElseThrow(this::idempotencyConflict);
        if (!content.equals(userMessage.getContent())
                || !run.isAgentMode()
                || run.getMaxTurns() != request.effectiveMaxTurns()
                || !sameRequestedModel(run, request.modelName())
                || !sameAttachmentIds(userMessage.getId(), attachmentIds)
                || !normalizePermissions(permissions).equals(normalizePermissions(run.getPermissionsSnapshot()))) {
            throw idempotencyConflict();
        }
        if (run.getStatus() == RunStatus.QUEUED) {
            runService.start(run.getId(), conversation.getTenantId());
        }
        return detail(conversation);
    }

    private boolean sameRequestedModel(Run run, String requestedModel) {
        return requestedModel == null || requestedModel.isBlank()
                || requestedModel.trim().equals(run.getModelName());
    }

    private String normalizeIdempotencyKey(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeWorkspaceId(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizePermissions(String value) {
        if (value == null || value.isBlank()) return "";
        return java.util.Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .distinct()
                .sorted()
                .collect(java.util.stream.Collectors.joining(","));
    }

    private BusinessException idempotencyConflict() {
        return new BusinessException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                "幂等键已经用于其他对话消息");
    }

    /** 领取尚未绑定的附件，防止跨租户、跨会话或跨消息复用。 */
    private List<ConversationAttachment> loadPendingAttachments(Conversation conversation,
                                                                 String tenantId, String userId,
                                                                 List<String> attachmentIds) {
        if (attachmentIds.isEmpty()) return List.of();
        if (attachmentIds.size() > MAX_ATTACHMENTS_PER_MESSAGE) {
            throw new BusinessException(HttpStatus.PAYLOAD_TOO_LARGE, "ATTACHMENT_LIMIT_EXCEEDED",
                    "每条消息最多可携带 " + MAX_ATTACHMENTS_PER_MESSAGE + " 个附件");
        }
        Map<String, ConversationAttachment> byId = new HashMap<>();
        for (ConversationAttachment attachment : attachmentRepository.findByIdIn(attachmentIds)) {
            byId.put(attachment.getId(), attachment);
        }
        if (byId.size() != attachmentIds.size()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "ATTACHMENT_NOT_FOUND", "聊天附件不存在或已被删除");
        }
        List<ConversationAttachment> result = new ArrayList<>();
        for (String attachmentId : attachmentIds) {
            ConversationAttachment attachment = byId.get(attachmentId);
            if (!conversation.getId().equals(attachment.getConversationId())
                    || !tenantId.equals(attachment.getTenantId())
                    || !userId.equals(attachment.getUserId())) {
                throw new BusinessException(HttpStatus.NOT_FOUND, "ATTACHMENT_NOT_FOUND", "聊天附件不存在或无权访问");
            }
            if (attachment.getMessageId() != null) {
                throw new BusinessException(HttpStatus.CONFLICT, "ATTACHMENT_ALREADY_USED", "聊天附件已经绑定到其他消息");
            }
            result.add(attachment);
        }
        return List.copyOf(result);
    }

    private void attachToMessage(List<ConversationAttachment> attachments, ConversationMessage message) {
        for (ConversationAttachment attachment : attachments) {
            attachment.attachToMessage(message.getId());
        }
        if (!attachments.isEmpty()) attachmentRepository.saveAll(attachments);
    }

    private boolean sameAttachmentIds(String messageId, List<String> requestedIds) {
        List<String> existingIds = attachmentRepository.findByMessageIdInOrderByCreatedAtAsc(List.of(messageId))
                .stream().map(ConversationAttachment::getId).toList();
        return existingIds.equals(requestedIds);
    }

    private ConversationMessage appendMessage(Conversation conversation, String runId,
                                              ConversationMessageRole role,
                                              ConversationMessageStatus status, String content) {
        int sequence = messageRepository.findTopByConversationIdOrderBySequenceDesc(conversation.getId())
                .map(message -> message.getSequence() + 1)
                .orElse(1);
        return messageRepository.save(new ConversationMessage(
                conversation.getId(), runId, conversation.getTenantId(), conversation.getUserId(),
                role, status, sequence, content));
    }

    private String buildPrompt(String conversationId, String tenantId, String currentContent,
                               List<ConversationAttachment> currentAttachments) {
        List<ConversationMessage> messages = messageRepository.findByConversationIdOrderBySequenceAsc(conversationId);
        Map<String, List<ConversationAttachment>> attachmentsByMessage = attachmentsByMessage(messages);
        ConversationContext context = contextRepository.findById(conversationId).orElse(null);
        int maxInputLength = tenantPolicyService.limitsFor(tenantId).maxInputLength();
        List<ConversationMessage> completed = messages.stream()
                .filter(message -> message.getStatus() == ConversationMessageStatus.COMPLETED)
                .filter(message -> message.getContent() != null && !message.getContent().isBlank())
                .toList();
        int compactedThrough = context == null ? 0 : context.getCompactedThroughSequence();
        String existingSummary = context == null ? "" : context.getSummary();
        List<ConversationMessage> uncompressed = completed.stream()
                .filter(message -> message.getSequence() > compactedThrough)
                .toList();
        String workspaceReference = directWorkspaceReference();
        String currentOnly = composePrompt(workspaceReference, "", List.of(), currentContent,
                currentAttachments, attachmentsByMessage);
        if (currentOnly.length() > maxInputLength) {
            throw inputTooLarge();
        }

        String prompt = composePrompt(workspaceReference, existingSummary, uncompressed,
                currentContent, currentAttachments, attachmentsByMessage);
        if (prompt.length() <= maxInputLength) {
            return prompt;
        }

        int summaryBudget = Math.max(128, maxInputLength / SUMMARY_BUDGET_DIVISOR);
        String summary = existingSummary;
        int cutoff = compactedThrough;
        for (ConversationMessage message : uncompressed) {
            summary = summarize(summary, message, summaryBudget);
            cutoff = message.getSequence();
            int currentCutoff = cutoff;
            List<ConversationMessage> remaining = uncompressed.stream()
                    .filter(candidate -> candidate.getSequence() > currentCutoff)
                    .toList();
            prompt = composePrompt(workspaceReference, summary, remaining, currentContent,
                    currentAttachments, attachmentsByMessage);
            if (prompt.length() <= maxInputLength) {
                saveContext(context, conversationId, summary, cutoff);
                return prompt;
            }
        }

        // 摘要本身也必须服从预算；旧摘要过大时继续压缩其两端，避免历史上下文再次撑爆请求。
        int availableForSummary = maxInputLength - currentOnly.length();
        if (availableForSummary >= 0) {
            summary = boundSummary(summary, availableForSummary);
            prompt = composePrompt(workspaceReference, summary, List.of(), currentContent,
                    currentAttachments, attachmentsByMessage);
            if (prompt.length() <= maxInputLength) {
                saveContext(context, conversationId, summary, cutoff);
                return prompt;
            }
        }
        throw inputTooLarge();
    }

    private BusinessException inputTooLarge() {
        return new BusinessException(HttpStatus.PAYLOAD_TOO_LARGE, "INPUT_TOO_LARGE",
                "任务输入超过允许的最大长度");
    }

    private void saveContext(ConversationContext context, String conversationId,
                             String summary, int compactedThrough) {
        ConversationContext target = context == null
                ? new ConversationContext(conversationId) : context;
        target.update(summary, compactedThrough);
        contextRepository.save(target);
    }

    private String summarize(String existingSummary, ConversationMessage message, int budget) {
        StringBuilder value = new StringBuilder(existingSummary == null ? "" : existingSummary);
        if (value.length() > 0) value.append('\n');
        value.append(message.getRole() == ConversationMessageRole.USER ? "用户" : "助手")
                .append('#').append(message.getSequence()).append(':')
                .append(compactMessage(message.getContent()));
        return boundSummary(value.toString(), budget);
    }

    private String compactMessage(String value) {
        String normalized = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= MESSAGE_SUMMARY_CHARS) return normalized;
        int head = MESSAGE_SUMMARY_CHARS / 2;
        int tail = MESSAGE_SUMMARY_CHARS - head - 1;
        return normalized.substring(0, head) + "…" + normalized.substring(normalized.length() - tail);
    }

    private String boundSummary(String value, int maximum) {
        if (maximum <= 0 || value == null || value.isBlank()) return "";
        if (value.length() <= maximum) return value;
        if (maximum < 8) return value.substring(value.length() - maximum);
        int head = Math.max(1, maximum / 2 - 1);
        int tail = maximum - head - 1;
        return value.substring(0, head) + "…" + value.substring(value.length() - tail);
    }

    private String composePrompt(String workspaceReference, String summary,
                                 List<ConversationMessage> history, String currentContent,
                                 List<ConversationAttachment> currentAttachments,
                                 Map<String, List<ConversationAttachment>> attachmentsByMessage) {
        StringBuilder prompt = new StringBuilder(workspaceReference == null ? "" : workspaceReference);
        if (summary != null && !summary.isBlank()) {
            prompt.append("对话历史摘要（较早消息已压缩，完整记录仍保存在会话历史中）：\n")
                    .append(summary).append("\n\n");
        }
        for (ConversationMessage message : history) {
            prompt.append(message.getRole() == ConversationMessageRole.USER ? "用户: " : "助手: ")
                    .append(message.getContent()).append("\n\n");
            appendAttachmentReferences(prompt, attachmentsByMessage.getOrDefault(message.getId(), List.of()));
        }
        prompt.append("用户: ").append(currentContent).append("\n");
        appendAttachmentReferences(prompt, currentAttachments);
        prompt.append("\n助手:");
        return sanitizer.sanitize(prompt.toString());
    }

    /**
     * 明确告诉模型：本轮可以直接操作本机后端已授权的项目目录，而不是只能处理上传附件。
     * 根目录的绝对路径不会写入 Run、聊天记录或模型输入。
     */
    private String directWorkspaceReference() {
        if (!workspace.properties().enabled()) return "";
        return "当前会话已连接到本地代码工作区。所有 workspace.* 工具的 path 都相对于该工作区根目录，"
                + "读取、编辑和受审批命令会直接作用于用户已授权的本地项目。"
                + "首次处理代码任务时调用 workspace.list（path 为 .）了解项目结构；目录结果已经提供后不要重复调用，"
                + "也不要请求或输出工作区的绝对路径。\n\n";
    }

    private Map<String, List<ConversationAttachment>> attachmentsByMessage(Collection<ConversationMessage> messages) {
        List<String> messageIds = messages.stream().map(ConversationMessage::getId).toList();
        if (messageIds.isEmpty()) return Map.of();
        Map<String, List<ConversationAttachment>> result = new HashMap<>();
        for (ConversationAttachment attachment : attachmentRepository.findByMessageIdInOrderByCreatedAtAsc(messageIds)) {
            result.computeIfAbsent(attachment.getMessageId(), ignored -> new ArrayList<>()).add(attachment);
        }
        return result;
    }

    private void appendAttachmentReferences(StringBuilder prompt, List<ConversationAttachment> attachments) {
        if (attachments.isEmpty()) return;
        prompt.append("附件已导入工作区：\n");
        for (ConversationAttachment attachment : attachments) {
            if (attachment.isDirectory()) {
                prompt.append("- 文件夹 ").append(attachment.getWorkspacePath())
                        .append("（包含 ").append(attachment.getFileCount())
                        .append(" 个文本文件）；首次需要了解它时调用 workspace.list，再按需 workspace.read。\n");
            } else {
                prompt.append("- 文件 ").append(attachment.getWorkspacePath())
                        .append("；需要阅读时请调用 workspace.read。\n");
            }
        }
        prompt.append("\n");
    }

    private Conversation load(String conversationId, String tenantId, String userId) {
        return conversationRepository.findByIdAndTenantIdAndUserId(conversationId, tenantId, userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "CONVERSATION_NOT_FOUND",
                        "聊天会话不存在: " + conversationId));
    }

    private Conversation loadForMessage(String conversationId, String tenantId, String userId) {
        return conversationRepository.findByIdAndTenantIdAndUserIdForUpdate(conversationId, tenantId, userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "CONVERSATION_NOT_FOUND",
                        "聊天会话不存在: " + conversationId));
    }

    private ConversationDetail detail(Conversation conversation) {
        List<ConversationMessage> messages = messageRepository
                .findByConversationIdOrderBySequenceAsc(conversation.getId());
        Map<String, List<ConversationAttachment>> attachmentsByMessage = attachmentsByMessage(messages);
        return new ConversationDetail(summary(conversation), messages.stream().map(message ->
                new ConversationMessageView(message.getId(), message.getRunId(), message.getRole(),
                        message.getStatus(), message.getSequence(), message.getContent(),
                        attachmentsByMessage.getOrDefault(message.getId(), List.of()).stream()
                                .map(this::toAttachmentView).toList(),
                        message.getCreatedAt(), message.getUpdatedAt())).toList());
    }

    private ConversationAttachmentView toAttachmentView(ConversationAttachment attachment) {
        return new ConversationAttachmentView(attachment.getId(), attachment.getOriginalName(),
                attachment.getWorkspacePath(), attachment.getMediaType(), attachment.isDirectory(),
                attachment.getSizeBytes(), attachment.getFileCount(), attachment.getCreatedAt());
    }

    private List<PreparedAttachment> prepareAttachments(List<MultipartFile> files, List<String> relativePaths) {
        if (relativePaths != null && !relativePaths.isEmpty() && relativePaths.size() != files.size()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ATTACHMENT_PATHS_INVALID",
                    "文件与相对路径数量不一致");
        }
        long totalBytes = 0;
        Set<String> uniquePaths = new LinkedHashSet<>();
        List<PreparedAttachment> result = new ArrayList<>();
        for (int index = 0; index < files.size(); index++) {
            String providedPath = relativePaths == null || relativePaths.isEmpty()
                    ? null : relativePaths.get(index);
            PreparedAttachment attachment = prepareAttachment(files.get(index), providedPath);
            if (!uniquePaths.add(attachment.relativePath())) {
                throw new BusinessException(HttpStatus.CONFLICT, "ATTACHMENT_PATH_DUPLICATED",
                        "文件夹中存在重复的相对路径: " + attachment.relativePath());
            }
            totalBytes += attachment.sizeBytes();
            if (totalBytes > MAX_UPLOAD_BYTES) {
                throw new BusinessException(HttpStatus.PAYLOAD_TOO_LARGE, "ATTACHMENT_BATCH_TOO_LARGE",
                        "一次导入的文件总大小不能超过 20 MB");
            }
            result.add(attachment);
        }
        return List.copyOf(result);
    }

    private Map<String, AttachmentGroup> groupAttachments(List<PreparedAttachment> prepared) {
        Map<String, AttachmentGroup> result = new LinkedHashMap<>();
        for (PreparedAttachment item : prepared) {
            String rootName = item.relativePath().contains("/")
                    ? item.relativePath().substring(0, item.relativePath().indexOf('/'))
                    : item.relativePath();
            result.computeIfAbsent(rootName, AttachmentGroup::new).add(item);
        }
        for (AttachmentGroup group : result.values()) {
            if (group.hasFileAndChildWithSameRoot()) {
                throw new BusinessException(HttpStatus.CONFLICT, "ATTACHMENT_PATH_CONFLICT",
                        "文件和文件夹不能使用相同路径: " + group.rootName);
            }
        }
        return result;
    }

    private PreparedAttachment prepareAttachment(MultipartFile file, String relativePath) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ATTACHMENT_EMPTY", "不能上传空文件");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ATTACHMENT_READ_FAILED", "读取上传文件失败");
        }
        if (bytes.length > workspace.properties().maxWriteBytes()) {
            throw new BusinessException(HttpStatus.PAYLOAD_TOO_LARGE, "ATTACHMENT_TOO_LARGE",
                    "附件超过工作区允许的最大写入大小");
        }
        if (containsNulByte(bytes)) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "ATTACHMENT_NOT_TEXT",
                    "当前只支持 UTF-8 文本文件，请勿上传二进制文件");
        }
        String content;
        try {
            content = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "ATTACHMENT_NOT_TEXT",
                    "当前只支持 UTF-8 文本文件，请先转换文件编码");
        }
        String originalName = displayFileName(file.getOriginalFilename());
        return new PreparedAttachment(originalName, normalizeRelativePath(relativePath, originalName), content,
                file.getContentType(), bytes.length);
    }

    private String normalizeRelativePath(String rawPath, String fallbackName) {
        String value = rawPath == null || rawPath.isBlank() ? fallbackName : rawPath.replace('\\', '/');
        if (value.length() > 512 || value.startsWith("/") || value.indexOf('\0') >= 0) {
            throw invalidAttachmentPath();
        }
        String[] segments = value.split("/", -1);
        List<String> cleaned = new ArrayList<>();
        for (String segment : segments) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)
                    || !segment.equals(segment.trim()) || containsControlCharacter(segment)) {
                throw invalidAttachmentPath();
            }
            cleaned.add(segment);
        }
        return String.join("/", cleaned);
    }

    private BusinessException invalidAttachmentPath() {
        return new BusinessException(HttpStatus.BAD_REQUEST, "ATTACHMENT_PATH_INVALID",
                "文件夹相对路径不合法");
    }

    private boolean containsControlCharacter(String value) {
        return value.codePoints().anyMatch(Character::isISOControl);
    }

    private String attachmentWorkspacePath(Conversation conversation, String storageName) {
        return "attachments/" + conversation.getId() + "/" + UUID.randomUUID() + "-" + storageName;
    }

    private String displayFileName(String rawName) {
        String value = rawName == null ? "" : rawName.replace('\\', '/');
        int separator = value.lastIndexOf('/');
        if (separator >= 0) value = value.substring(separator + 1);
        value = value.replaceAll("[\\p{Cntrl}]", "_").trim();
        if (value.isBlank()) value = "attachment.txt";
        return value.substring(0, Math.min(value.length(), 255));
    }

    private String storageFileName(String originalName) {
        String value = originalName.replaceAll("[^A-Za-z0-9._-]", "_");
        while (value.startsWith(".")) value = value.substring(1);
        if (value.isBlank()) value = "attachment.txt";
        return value.substring(0, Math.min(value.length(), 120));
    }

    private boolean containsNulByte(byte[] value) {
        for (byte item : value) {
            if (item == 0) return true;
        }
        return false;
    }

    private void deleteImportedPath(Path path) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return;
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException exception) throws IOException {
                if (exception != null) throw exception;
                Files.deleteIfExists(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private record PreparedAttachment(String originalName, String relativePath, String content,
                                      String mediaType, long sizeBytes) {
    }

    /** 单个顶层文件或文件夹；目录内文件保留用户提供的安全相对路径。 */
    private static final class AttachmentGroup {
        private final String rootName;
        private final List<PreparedAttachment> files = new ArrayList<>();

        private AttachmentGroup(String rootName) {
            this.rootName = rootName;
        }

        private void add(PreparedAttachment item) {
            files.add(item);
        }

        private boolean isDirectory() {
            return files.size() > 1 || files.stream().anyMatch(item -> item.relativePath().contains("/"));
        }

        private boolean hasFileAndChildWithSameRoot() {
            boolean rootFile = files.stream().anyMatch(item -> item.relativePath().equals(rootName));
            return rootFile && files.size() > 1;
        }

        private String workspacePathFor(String workspaceRoot, PreparedAttachment item) {
            if (!isDirectory()) return workspaceRoot;
            return workspaceRoot + item.relativePath().substring(rootName.length());
        }

        private long totalBytes() {
            return files.stream().mapToLong(PreparedAttachment::sizeBytes).sum();
        }

        private String mediaType() {
            return isDirectory() ? "inode/directory" : files.get(0).mediaType();
        }
    }

    private ConversationSummary summary(Conversation conversation) {
        List<ConversationMessage> messages = messageRepository
                .findByConversationIdOrderBySequenceAsc(conversation.getId());
        ConversationMessage last = messages.isEmpty() ? null : messages.get(messages.size() - 1);
        String activeRunId = messages.stream()
                .filter(message -> message.getRole() == ConversationMessageRole.ASSISTANT
                        && message.getStatus() == ConversationMessageStatus.PENDING)
                .map(ConversationMessage::getRunId)
                .filter(value -> value != null && !value.isBlank())
                .reduce((first, second) -> second).orElse(null);
        String preview = last == null ? "" : last.getContent();
        if (preview != null && preview.length() > 80) preview = preview.substring(0, 80) + "…";
        return new ConversationSummary(conversation.getId(), conversation.getTenantId(), conversation.getUserId(),
                conversation.getTitle(), conversation.getWorkspaceId(), conversation.getCreatedAt(), conversation.getUpdatedAt(),
                messages.size(), preview, activeRunId);
    }
}
