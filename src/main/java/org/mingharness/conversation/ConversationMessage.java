package org.mingharness.conversation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/** 对话中的一条用户消息或助手消息，助手消息与一个 Run 一一对应。 */
@Entity
@Table(name = "harness_conversation_messages", uniqueConstraints = {
        @UniqueConstraint(name = "uk_harness_message_run_role", columnNames = {"run_id", "role"}),
        @UniqueConstraint(name = "uk_harness_message_conversation_sequence",
                columnNames = {"conversation_id", "message_sequence"})
})
public class ConversationMessage {

    @Id
    private String id;
    @Column(name = "conversation_id", nullable = false, length = 128)
    private String conversationId;
    @Column(name = "run_id", length = 128)
    private String runId;
    @Column(name = "tenant_id", nullable = false, length = 128)
    private String tenantId;
    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ConversationMessageRole role;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ConversationMessageStatus status;
    @Column(name = "message_sequence", nullable = false)
    private int sequence;
    @Column(nullable = false, columnDefinition = "text")
    private String content;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ConversationMessage() {
    }

    public ConversationMessage(String conversationId, String runId, String tenantId, String userId,
                                ConversationMessageRole role, ConversationMessageStatus status,
                                int sequence, String content) {
        this.id = UUID.randomUUID().toString();
        this.conversationId = conversationId;
        this.runId = runId;
        this.tenantId = tenantId;
        this.userId = userId;
        this.role = role;
        this.status = status;
        this.sequence = sequence;
        this.content = content == null ? "" : content;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void markPending() {
        this.status = ConversationMessageStatus.PENDING;
        this.content = "";
        this.updatedAt = Instant.now();
    }

    public void complete(String content) {
        this.status = ConversationMessageStatus.COMPLETED;
        this.content = content == null ? "" : content;
        this.updatedAt = Instant.now();
    }

    public void fail(String content) {
        this.status = ConversationMessageStatus.FAILED;
        this.content = content == null ? "执行失败" : content;
        this.updatedAt = Instant.now();
    }

    public void cancel(String content) {
        this.status = ConversationMessageStatus.CANCELLED;
        this.content = content == null ? "任务已取消" : content;
        this.updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getConversationId() { return conversationId; }
    public String getRunId() { return runId; }
    public String getTenantId() { return tenantId; }
    public String getUserId() { return userId; }
    public ConversationMessageRole getRole() { return role; }
    public ConversationMessageStatus getStatus() { return status; }
    public int getSequence() { return sequence; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
