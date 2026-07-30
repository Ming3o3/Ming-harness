package org.mingharness.context;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

@Entity
@Table(name = "harness_context_documents")
public class KnowledgeDocument {

    @Id
    private String id;
    @Column(nullable = false)
    private String tenantId;
    @Column(nullable = false)
    private String ownerUserId;
    @Column(nullable = false)
    private String title;
    @Lob
    @Column(nullable = false)
    private String content;
    @Column(nullable = false)
    private String sensitivity;
    @Column(length = 2000)
    private String allowedUsers;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;

    protected KnowledgeDocument() {
    }

    public KnowledgeDocument(String tenantId, String ownerUserId, String title, String content,
                             String sensitivity, String allowedUsers) {
        this.id = UUID.randomUUID().toString();
        this.tenantId = tenantId;
        this.ownerUserId = ownerUserId;
        this.title = title;
        this.content = content;
        this.sensitivity = sensitivity == null || sensitivity.isBlank() ? "INTERNAL" : sensitivity;
        this.allowedUsers = normalizeUsers(allowedUsers);
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public boolean isVisibleTo(String userId) {
        if (allowedUsers == null || allowedUsers.isBlank()) {
            return true;
        }
        return Arrays.stream(allowedUsers.split(","))
                .anyMatch(item -> item.equals(userId));
    }

    public void markDeleted() {
        this.deletedAt = Instant.now();
        this.updatedAt = this.deletedAt;
    }

    private String normalizeUsers(String users) {
        if (users == null || users.isBlank()) {
            return "";
        }
        return Arrays.stream(users.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .distinct()
                .sorted()
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getOwnerUserId() { return ownerUserId; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public String getSensitivity() { return sensitivity; }
    public String getAllowedUsers() { return allowedUsers; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }
}
