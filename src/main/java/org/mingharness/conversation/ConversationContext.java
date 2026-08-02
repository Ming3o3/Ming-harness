package org.mingharness.conversation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 会话的有界上下文快照。
 *
 * <p>原始消息仍然保存在 {@link ConversationMessage} 中；本实体只记录已经被压缩的
 * 历史摘要和摘要覆盖到的消息序号，避免每轮模型调用都把整段历史重新塞入 Prompt。</p>
 */
@Entity
@Table(name = "harness_conversation_contexts")
public class ConversationContext {

    @Id
    @Column(name = "conversation_id", length = 128)
    private String conversationId;

    @Column(nullable = false, columnDefinition = "text")
    private String summary;

    @Column(name = "compacted_through_sequence", nullable = false)
    private int compactedThroughSequence;

    @Column(name = "summary_version", nullable = false)
    private int summaryVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ConversationContext() {
    }

    public ConversationContext(String conversationId) {
        this.conversationId = conversationId;
        this.summary = "";
        this.compactedThroughSequence = 0;
        this.summaryVersion = 1;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void update(String summary, int compactedThroughSequence) {
        this.summary = summary == null ? "" : summary;
        this.compactedThroughSequence = Math.max(this.compactedThroughSequence, compactedThroughSequence);
        this.summaryVersion++;
        this.updatedAt = Instant.now();
    }

    public String getConversationId() { return conversationId; }
    public String getSummary() { return summary; }
    public int getCompactedThroughSequence() { return compactedThroughSequence; }
    public int getSummaryVersion() { return summaryVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
