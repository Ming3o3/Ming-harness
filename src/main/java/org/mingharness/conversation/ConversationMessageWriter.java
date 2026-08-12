package org.mingharness.conversation;

import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.runtime.domain.Run;
import org.mingharness.runtime.domain.RunStatus;
import org.mingharness.education.LearningAssignmentReconciliationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 将 Run 的最终状态映射为聊天中的助手气泡，重复调用保持幂等。 */
@Service
public class ConversationMessageWriter {

    private final ConversationMessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final SensitiveDataSanitizer sanitizer;
    private final LearningAssignmentReconciliationService assignmentReconciliationService;

    public ConversationMessageWriter(ConversationMessageRepository messageRepository,
                                     ConversationRepository conversationRepository,
                                     SensitiveDataSanitizer sanitizer,
                                     LearningAssignmentReconciliationService assignmentReconciliationService) {
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.sanitizer = sanitizer;
        this.assignmentReconciliationService = assignmentReconciliationService;
    }

    @Transactional
    public void markPending(Run run) {
        if (run == null || run.getConversationId() == null) {
            return;
        }
        messageRepository.findByRunIdAndRole(run.getId(), ConversationMessageRole.ASSISTANT)
                .ifPresent(message -> {
                    message.markPending();
                    touchConversation(message);
                });
    }

    @Transactional
    public void updateForTerminalRun(Run run) {
        if (run == null || run.getConversationId() == null) {
            if (run != null) assignmentReconciliationService.reconcileRun(run);
            return;
        }
        messageRepository.findByRunIdAndRole(run.getId(), ConversationMessageRole.ASSISTANT)
                .ifPresent(message -> {
                    update(message, run);
                    touchConversation(message);
                });
        assignmentReconciliationService.reconcileRun(run);
    }

    /**
     * 模型流式生成时同步更新聊天气泡。内容仍保存在数据库，因此 SSE 断线或页面刷新后可恢复。
     */
    @Transactional
    public void updatePendingContent(Run run, String content) {
        if (run == null || run.getConversationId() == null || content == null || content.isBlank()) {
            return;
        }
        messageRepository.findByRunIdAndRole(run.getId(), ConversationMessageRole.ASSISTANT)
                .ifPresent(message -> {
                    message.updatePendingContent(sanitizer.sanitize(content));
                    touchConversation(message);
                });
    }

    private void update(ConversationMessage message, Run run) {
        RunStatus status = run.getStatus();
        if (status == RunStatus.SUCCEEDED) {
            message.complete(sanitizer.sanitize(run.getOutput()));
        } else if (status == RunStatus.CANCELLED) {
            message.cancel("任务已取消");
        } else if (status == RunStatus.FAILED || status == RunStatus.TIMED_OUT) {
            message.fail(sanitizer.sanitize(run.getError() == null ? "任务执行失败" : run.getError()));
        }
    }

    private void touchConversation(ConversationMessage message) {
        conversationRepository.findById(message.getConversationId()).ifPresent(Conversation::touch);
    }
}
