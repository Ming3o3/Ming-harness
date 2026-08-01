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
import org.mingharness.runtime.domain.Run;
import org.mingharness.runtime.domain.RunStatus;
import org.mingharness.runtime.repository.RunRepository;
import org.mingharness.tool.WorkspaceToolSupport;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** 聊天会话应用服务：每条用户消息创建一个可审计、可恢复的 Agent Run。 */
@Service
public class ConversationService {

    private static final int MAX_CONTEXT_CHARS = 12000;
    private static final int MAX_ATTACHMENTS_PER_MESSAGE = 8;

    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository messageRepository;
    private final ConversationAttachmentRepository attachmentRepository;
    private final RunRepository runRepository;
    private final RunService runService;
    private final SensitiveDataSanitizer sanitizer;
    private final WorkspaceToolSupport workspace;

    public ConversationService(ConversationRepository conversationRepository,
                               ConversationMessageRepository messageRepository,
                               ConversationAttachmentRepository attachmentRepository,
                               RunRepository runRepository,
                               RunService runService,
                               SensitiveDataSanitizer sanitizer,
                               WorkspaceToolSupport workspace) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.attachmentRepository = attachmentRepository;
        this.runRepository = runRepository;
        this.runService = runService;
        this.sanitizer = sanitizer;
        this.workspace = workspace;
    }

    @Transactional
    public ConversationDetail create(String tenantId, String userId, CreateConversationRequest request) {
        String title = request == null ? null : request.title();
        Conversation conversation = new Conversation(tenantId, userId,
                sanitizer.sanitize(title == null || title.isBlank() ? "新的对话" : title.trim()));
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

    /**
     * 将浏览器拖入的 UTF-8 文本文件导入到受控工作区。
     *
     * <p>浏览器不会也不应暴露用户硬盘绝对路径；接口返回的 {@code workspacePath} 才是 Agent
     * 可使用的稳定相对路径。文件尚未绑定到消息，只有发送成功后才成为聊天历史的一部分。</p>
     */
    @Transactional
    public List<ConversationAttachmentView> upload(String conversationId, String tenantId, String userId,
                                                    List<MultipartFile> files) {
        Conversation conversation = loadForMessage(conversationId, tenantId, userId);
        if (files == null || files.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ATTACHMENT_REQUIRED", "至少需要上传一个文件");
        }
        if (files.size() > MAX_ATTACHMENTS_PER_MESSAGE) {
            throw new BusinessException(HttpStatus.PAYLOAD_TOO_LARGE, "ATTACHMENT_LIMIT_EXCEEDED",
                    "一次最多上传 " + MAX_ATTACHMENTS_PER_MESSAGE + " 个文件");
        }
        workspace.requireEnabled();

        List<PreparedAttachment> prepared = files.stream()
                .map(this::prepareAttachment)
                .toList();
        List<Path> writtenPaths = new ArrayList<>();
        List<ConversationAttachment> attachments = new ArrayList<>();
        try {
            for (PreparedAttachment item : prepared) {
                String workspacePath = attachmentWorkspacePath(conversation, item.storageName());
                Path target = workspace.resolve(workspacePath, true);
                workspace.writeText(target, workspacePath, item.content(), null);
                writtenPaths.add(target);
                attachments.add(new ConversationAttachment(conversation.getId(), tenantId, userId,
                        item.originalName(), workspacePath, item.mediaType(), item.sizeBytes()));
            }
            attachmentRepository.saveAll(attachments);
            return attachments.stream().map(this::toAttachmentView).toList();
        } catch (RuntimeException exception) {
            // 数据库存储失败时尽力删除刚导入的文件，避免工作区遗留不可引用的附件。
            for (Path path : writtenPaths) {
                try {
                    Files.deleteIfExists(path);
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
        Path storedFile = workspace.resolve(attachment.getWorkspacePath(), true);
        try {
            Files.deleteIfExists(storedFile);
        } catch (IOException exception) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "ATTACHMENT_DELETE_FAILED",
                    "删除工作区附件失败");
        }
        attachmentRepository.delete(attachment);
    }

    /** 创建一轮 USER + ASSISTANT 消息，并立即启动关联 Run。 */
    @Transactional
    public ConversationDetail send(String conversationId, String tenantId, String userId,
                                   SendConversationMessageRequest request, String idempotencyKey,
                                   String permissions) {
        Conversation conversation = loadForMessage(conversationId, tenantId, userId);
        String content = sanitizer.sanitize(request.content().trim());
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
        String runInput = buildPrompt(conversation.getId(), content, attachments);
        CreateRunRequest runRequest = new CreateRunRequest(
                tenantId, userId, conversation.getTitle(), runInput,
                null, sanitizer.sanitize(request.modelName()), "prompt-v1", "policy-v1",
                BigDecimal.ONE, effectiveIdempotencyKey, permissions, true, request.effectiveMaxTurns(),
                conversation.getId());
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

    private String buildPrompt(String conversationId, String currentContent,
                               List<ConversationAttachment> currentAttachments) {
        List<ConversationMessage> messages = messageRepository.findByConversationIdOrderBySequenceAsc(conversationId);
        Map<String, List<ConversationAttachment>> attachmentsByMessage = attachmentsByMessage(messages);
        StringBuilder prompt = new StringBuilder();
        for (ConversationMessage message : messages) {
            if (message.getStatus() != ConversationMessageStatus.COMPLETED
                    || message.getContent() == null || message.getContent().isBlank()) {
                continue;
            }
            prompt.append(message.getRole() == ConversationMessageRole.USER ? "用户: " : "助手: ")
                    .append(message.getContent()).append("\n\n");
            appendAttachmentReferences(prompt, attachmentsByMessage.getOrDefault(message.getId(), List.of()));
        }
        prompt.append("用户: ").append(currentContent).append("\n");
        appendAttachmentReferences(prompt, currentAttachments);
        prompt.append("\n助手:");
        String value = sanitizer.sanitize(prompt.toString());
        return value.length() <= MAX_CONTEXT_CHARS
                ? value : value.substring(value.length() - MAX_CONTEXT_CHARS);
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
        prompt.append("附件已导入工作区；需要阅读时请调用 workspace.read：\n");
        for (ConversationAttachment attachment : attachments) {
            prompt.append("- ").append(attachment.getWorkspacePath()).append("\n");
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
                attachment.getWorkspacePath(), attachment.getMediaType(), attachment.getSizeBytes(),
                attachment.getCreatedAt());
    }

    private PreparedAttachment prepareAttachment(MultipartFile file) {
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
        return new PreparedAttachment(originalName, storageFileName(originalName), content,
                file.getContentType(), bytes.length);
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

    private record PreparedAttachment(String originalName, String storageName, String content,
                                      String mediaType, long sizeBytes) {
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
                conversation.getTitle(), conversation.getCreatedAt(), conversation.getUpdatedAt(),
                messages.size(), preview, activeRunId);
    }
}
