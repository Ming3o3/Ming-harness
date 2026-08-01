package org.mingharness.conversation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/**
 * 用户拖入聊天框的文本文件元数据。
 *
 * <p>文件正文只保存到受控工作区，数据库仅记录工作区相对路径，避免把大型代码正文复制进业务表。</p>
 */
@Entity
@Table(name = "harness_conversation_attachments", uniqueConstraints = {
        @UniqueConstraint(name = "uk_harness_attachment_workspace_path", columnNames = "workspace_path")
})
public class ConversationAttachment {

    @Id
    private String id;
    @Column(name = "conversation_id", nullable = false, length = 128)
    private String conversationId;
    @Column(name = "message_id", length = 128)
    private String messageId;
    @Column(name = "tenant_id", nullable = false, length = 128)
    private String tenantId;
    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;
    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;
    @Column(name = "workspace_path", nullable = false, length = 1024)
    private String workspacePath;
    @Column(name = "media_type", length = 255)
    private String mediaType;
    @Column(name = "directory", nullable = false)
    private boolean directory;
    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;
    @Column(name = "file_count", nullable = false)
    private int fileCount;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ConversationAttachment() {
    }

    public ConversationAttachment(String conversationId, String tenantId, String userId,
                                  String originalName, String workspacePath,
                                  String mediaType, long sizeBytes) {
        this(conversationId, tenantId, userId, originalName, workspacePath, mediaType,
                sizeBytes, false, 1);
    }

    public ConversationAttachment(String conversationId, String tenantId, String userId,
                                  String originalName, String workspacePath,
                                  String mediaType, long sizeBytes,
                                  boolean directory, int fileCount) {
        this.id = UUID.randomUUID().toString();
        this.conversationId = conversationId;
        this.tenantId = tenantId;
        this.userId = userId;
        this.originalName = originalName;
        this.workspacePath = workspacePath;
        this.mediaType = mediaType;
        this.directory = directory;
        this.sizeBytes = Math.max(0, sizeBytes);
        this.fileCount = Math.max(1, fileCount);
        this.createdAt = Instant.now();
    }

    /** 附件只能绑定到所属会话中的一条用户消息。 */
    public void attachToMessage(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("消息 ID 不能为空");
        }
        if (messageId != null && !messageId.equals(value)) {
            throw new IllegalStateException("附件已经绑定到其他聊天消息");
        }
        this.messageId = value;
    }

    public String getId() { return id; }
    public String getConversationId() { return conversationId; }
    public String getMessageId() { return messageId; }
    public String getTenantId() { return tenantId; }
    public String getUserId() { return userId; }
    public String getOriginalName() { return originalName; }
    public String getWorkspacePath() { return workspacePath; }
    public String getMediaType() { return mediaType; }
    public boolean isDirectory() { return directory; }
    public long getSizeBytes() { return sizeBytes; }
    public int getFileCount() { return fileCount; }
    public Instant getCreatedAt() { return createdAt; }
}
