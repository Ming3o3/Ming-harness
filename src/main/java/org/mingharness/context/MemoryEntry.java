package org.mingharness.context;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "harness_context_memories")
public class MemoryEntry {

    @Id
    private String id;
    @Column(nullable = false)
    private String tenantId;
    @Column(nullable = false)
    private String userId;
    @Column(nullable = false)
    private String memoryType;
    @Lob
    @Column(nullable = false)
    private String content;
    private String sourceRunId;
    private Instant expiresAt;
    private Instant createdAt;
    private Instant deletedAt;

    protected MemoryEntry() {
    }

    public MemoryEntry(String tenantId, String userId, String memoryType, String content,
                       String sourceRunId, Instant expiresAt) {
        this.id = UUID.randomUUID().toString();
        this.tenantId = tenantId;
        this.userId = userId;
        this.memoryType = memoryType;
        this.content = content;
        this.sourceRunId = sourceRunId;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    public boolean isActive(Instant now) {
        return deletedAt == null && (expiresAt == null || expiresAt.isAfter(now));
    }

    public void markDeleted() {
        this.deletedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getUserId() { return userId; }
    public String getMemoryType() { return memoryType; }
    public String getContent() { return content; }
    public String getSourceRunId() { return sourceRunId; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDeletedAt() { return deletedAt; }
}
