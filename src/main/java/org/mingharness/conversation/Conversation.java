package org.mingharness.conversation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** 持久化一组连续的用户与 Agent 对话。 */
@Entity
@Table(name = "harness_conversations")
public class Conversation {

    public static final String DEFAULT_TITLE = "新的对话";

    @Id
    private String id;
    @Column(name = "tenant_id", nullable = false, length = 128)
    private String tenantId;
    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;
    @Column(nullable = false, length = 255)
    private String title;
    /** 会话固定绑定一个本地项目，运行中切换其他项目不会影响已创建的 Run。 */
    @Column(name = "workspace_id", length = 128)
    private String workspaceId;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Conversation() {
    }

    public Conversation(String tenantId, String userId, String title) {
        this(tenantId, userId, title, null);
    }

    public Conversation(String tenantId, String userId, String title, String workspaceId) {
        this.id = UUID.randomUUID().toString();
        this.tenantId = tenantId;
        this.userId = userId;
        this.title = title;
        this.workspaceId = workspaceId;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }

    public void rename(String title) {
        if (title != null && !title.isBlank()) {
            this.title = title;
            touch();
        }
    }

    /** 首条消息只为默认标题生成摘要；用户已经重命名过时绝不覆盖。 */
    public void autoTitleFromFirstMessage(String message) {
        if (!DEFAULT_TITLE.equals(title) || message == null || message.isBlank()) return;
        String candidate = message.replaceAll("\\s+", " ").trim();
        if (candidate.length() > 48) candidate = candidate.substring(0, 47).trim() + "…";
        rename(candidate);
    }

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getUserId() { return userId; }
    public String getTitle() { return title; }
    public String getWorkspaceId() { return workspaceId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
