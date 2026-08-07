package org.mingharness.context;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * 可独立向量化的父文档子块。
 *
 * <p>正文和父对象仍然分开保存：子块用于精准召回，父文档用于恢复完整上下文。
 * embedding 列由 PostgreSQL pgvector 迁移创建，后续通过 JDBC 向量适配层写入，避免
 * 让 Hibernate 依赖具体的 pgvector 类型实现。</p>
 */
@Entity
@Table(name = "harness_context_chunks", indexes = {
        @Index(name = "idx_harness_context_chunks_parent", columnList = "parent_type,parent_id,chunk_index"),
        @Index(name = "idx_harness_context_chunks_tenant_active", columnList = "tenant_id,deleted_at")
})
public class ContextChunk {

    @Id
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 255)
    private String tenantId;

    @Column(name = "parent_type", nullable = false, length = 32)
    private String parentType;

    @Column(name = "parent_id", nullable = false, length = 255)
    private String parentId;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

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

    @Column(name = "embedding_model", length = 128)
    private String embeddingModel;

    @Column(name = "embedded_at")
    private Instant embeddedAt;

    private Instant deletedAt;

    protected ContextChunk() {
    }

    public ContextChunk(String tenantId, String parentType, String parentId, int chunkIndex,
                        String content, String contentHash) {
        this.id = UUID.randomUUID().toString();
        this.tenantId = tenantId;
        this.parentType = parentType;
        this.parentId = parentId;
        this.chunkIndex = chunkIndex;
        this.content = content == null ? "" : content;
        this.contentHash = contentHash;
        this.characterCount = this.content.length();
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void markDeleted() {
        if (deletedAt == null) {
            deletedAt = Instant.now();
            updatedAt = deletedAt;
        }
    }

    public void markEmbedded(String model) {
        this.embeddingModel = model;
        this.embeddedAt = Instant.now();
        this.updatedAt = this.embeddedAt;
    }

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getParentType() { return parentType; }
    public String getParentId() { return parentId; }
    public int getChunkIndex() { return chunkIndex; }
    public String getContent() { return content; }
    public String getContentHash() { return contentHash; }
    public int getCharacterCount() { return characterCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getEmbeddingModel() { return embeddingModel; }
    public Instant getEmbeddedAt() { return embeddedAt; }
    public Instant getDeletedAt() { return deletedAt; }
}
