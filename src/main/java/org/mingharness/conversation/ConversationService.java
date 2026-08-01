package org.mingharness.conversation;

import org.mingharness.common.BusinessException;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.conversation.api.ConversationDetail;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** 聊天会话应用服务：每条用户消息创建一个可审计、可恢复的 Agent Run。 */
@Service
public class ConversationService {

    private static final int MAX_CONTEXT_CHARS = 12000;

    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository messageRepository;
    private final RunRepository runRepository;
    private final RunService runService;
    private final SensitiveDataSanitizer sanitizer;

    public ConversationService(ConversationRepository conversationRepository,
                               ConversationMessageRepository messageRepository,
                               RunRepository runRepository,
                               RunService runService,
                               SensitiveDataSanitizer sanitizer) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.runRepository = runRepository;
        this.runService = runService;
        this.sanitizer = sanitizer;
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

    /** 创建一轮 USER + ASSISTANT 消息，并立即启动关联 Run。 */
    @Transactional
    public ConversationDetail send(String conversationId, String tenantId, String userId,
                                   SendConversationMessageRequest request, String idempotencyKey,
                                   String permissions) {
        Conversation conversation = loadForMessage(conversationId, tenantId, userId);
        String content = sanitizer.sanitize(request.content().trim());
        String effectiveIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);
        if (sanitizer.containsSensitiveData(effectiveIdempotencyKey)) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "SENSITIVE_IDEMPOTENCY_KEY_REJECTED",
                    "幂等键不能包含疑似密钥或凭证");
        }

        Optional<Run> existing = effectiveIdempotencyKey == null
                ? Optional.empty()
                : runRepository.findByTenantIdAndIdempotencyKey(tenantId, effectiveIdempotencyKey);
        if (existing.isPresent()) {
            return replayExistingMessage(conversation, existing.get(), content, request, permissions);
        }

        String runInput = buildPrompt(conversation.getId(), content);
        CreateRunRequest runRequest = new CreateRunRequest(
                tenantId, userId, conversation.getTitle(), runInput,
                null, sanitizer.sanitize(request.modelName()), "prompt-v1", "policy-v1",
                BigDecimal.ONE, effectiveIdempotencyKey, permissions, true, request.effectiveMaxTurns(),
                conversation.getId());
        RunSummary run = runService.create(runRequest);

        messageRepository.findByRunIdAndRole(run.id(), ConversationMessageRole.USER)
                .orElseGet(() -> appendMessage(conversation, run.id(), ConversationMessageRole.USER,
                        ConversationMessageStatus.COMPLETED, content));
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
                                                     SendConversationMessageRequest request, String permissions) {
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

    private String buildPrompt(String conversationId, String currentContent) {
        StringBuilder prompt = new StringBuilder();
        for (ConversationMessage message : messageRepository.findByConversationIdOrderBySequenceAsc(conversationId)) {
            if (message.getStatus() != ConversationMessageStatus.COMPLETED
                    || message.getContent() == null || message.getContent().isBlank()) {
                continue;
            }
            prompt.append(message.getRole() == ConversationMessageRole.USER ? "用户: " : "助手: ")
                    .append(message.getContent()).append("\n\n");
        }
        prompt.append("用户: ").append(currentContent).append("\n\n助手:");
        String value = sanitizer.sanitize(prompt.toString());
        return value.length() <= MAX_CONTEXT_CHARS
                ? value : value.substring(value.length() - MAX_CONTEXT_CHARS);
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
        return new ConversationDetail(summary(conversation), messages.stream().map(message ->
                new ConversationMessageView(message.getId(), message.getRunId(), message.getRole(),
                        message.getStatus(), message.getSequence(), message.getContent(),
                        message.getCreatedAt(), message.getUpdatedAt())).toList());
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
