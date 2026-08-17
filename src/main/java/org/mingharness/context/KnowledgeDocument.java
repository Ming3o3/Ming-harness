package org.mingharness.context;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
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
    @Column(nullable = false, columnDefinition = "text")
    private String content;
    @Column(nullable = false)
    private String sensitivity;
    @Column(length = 2000)
    private String allowedUsers;
    @Enumerated(EnumType.STRING)
    @Column(name = "import_status", nullable = false, length = 32)
    private DocumentImportStatus importStatus;
    @Column(name = "import_error", length = 2000)
    private String importError;
    @Column(name = "content_char_count", nullable = false)
    private int contentCharCount;
    @Column(name = "page_count", nullable = false)
    private int pageCount;
    @Column(name = "import_source_path", length = 2000)
    private String importSourcePath;
    @Column(name = "import_source_name", length = 512)
    private String importSourceName;
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
        this.importStatus = DocumentImportStatus.READY;
        this.contentCharCount = this.content.length();
        this.pageCount = 0;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public KnowledgeDocument(String tenantId, String ownerUserId, String title,
                             String sensitivity, String allowedUsers, String importSourcePath,
                             String importSourceName, DocumentImportStatus importStatus) {
        this.id = UUID.randomUUID().toString();
        this.tenantId = tenantId;
        this.ownerUserId = ownerUserId;
        this.title = title;
        this.content = "";
        this.sensitivity = sensitivity == null || sensitivity.isBlank() ? "INTERNAL" : sensitivity;
        this.allowedUsers = normalizeUsers(allowedUsers);
        this.importStatus = importStatus == null ? DocumentImportStatus.PROCESSING : importStatus;
        this.contentCharCount = 0;
        this.pageCount = 0;
        this.importSourcePath = importSourcePath;
        this.importSourceName = importSourceName;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public boolean isVisibleTo(String userId) {
        // 资料所有者必须始终能看到自己上传的正文；否则教师上传时限定学生可见，
        // 下一次刷新就会把自己的课程资料从工作台中隐藏，无法继续维护或删除。
        if (ownerUserId != null && ownerUserId.equals(userId)) {
            return true;
        }
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

    public void markImportReady(int contentCharCount, int pageCount) {
        this.importStatus = DocumentImportStatus.READY;
        this.importError = null;
        this.contentCharCount = Math.max(0, contentCharCount);
        this.pageCount = Math.max(0, pageCount);
        this.importSourcePath = null;
        this.updatedAt = Instant.now();
    }

    public void markImportFailed(String error) {
        this.importStatus = DocumentImportStatus.FAILED;
        this.importError = error == null || error.isBlank() ? "文档解析失败" : error;
        this.importSourcePath = null;
        this.updatedAt = Instant.now();
    }

    public boolean isReady() {
        return importStatus == null || importStatus == DocumentImportStatus.READY;
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
    public DocumentImportStatus getImportStatus() {
        return importStatus == null ? DocumentImportStatus.READY : importStatus;
    }
    public String getImportError() { return importError; }
    public int getContentCharCount() { return contentCharCount; }
    public int getPageCount() { return pageCount; }
    public String getImportSourcePath() { return importSourcePath; }
    public String getImportSourceName() { return importSourceName; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }
}
