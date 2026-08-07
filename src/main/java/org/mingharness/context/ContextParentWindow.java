package org.mingharness.context;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * 供 LLM 推理使用的有界父窗口。
 *
 * <p>子块只负责精准向量召回；父窗口由连续子块物化而成，避免命中一个很小的子块后
 * 丢失标题、前置条件或后续步骤，同时不会把整篇文档无界地交给模型。</p>
 */
@Entity
@Table(name = "harness_context_parent_windows", indexes = {
        @Index(name = "idx_harness_context_parent_windows_parent",
                columnList = "parent_type,parent_id,window_index"),
        @Index(name = "idx_harness_context_parent_windows_tenant_active",
                columnList = "tenant_id,deleted_at")
})
public class ContextParentWindow {

    @Id
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 255)
    private String tenantId;

    @Column(name = "parent_type", nullable = false, length = 32)
    private String parentType;

    @Column(name = "parent_id", nullable = false, length = 255)
    private String parentId;

    @Column(name = "window_index", nullable = false)
    private int windowIndex;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "character_count", nullable = false)
    private int characterCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private Instant deletedAt;

    protected ContextParentWindow() {
    }

    public ContextParentWindow(String tenantId, String parentType, String parentId,
                               int windowIndex, String content, String contentHash) {
        this.id = UUID.randomUUID().toString();
        this.tenantId = tenantId;
        this.parentType = parentType;
        this.parentId = parentId;
        this.windowIndex = windowIndex;
        this.content = content == null ? "" : content;
        this.contentHash = contentHash;
        this.characterCount = this.content.length();
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getParentType() { return parentType; }
    public String getParentId() { return parentId; }
    public int getWindowIndex() { return windowIndex; }
    public String getContent() { return content; }
    public String getContentHash() { return contentHash; }
    public int getCharacterCount() { return characterCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }

}
