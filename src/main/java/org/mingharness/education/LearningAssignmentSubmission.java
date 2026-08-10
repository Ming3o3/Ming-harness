package org.mingharness.education;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/** 学习者提交物事实；每个教育 Run 最多产生一份不可变文本提交。 */
@Entity
@Table(name = "harness_learning_assignment_submissions", uniqueConstraints = @UniqueConstraint(
        name = "uk_harness_learning_assignment_submission_run",
        columnNames = {"tenant_id", "learning_assignment_id", "run_id"}))
public class LearningAssignmentSubmission {

    @Id
    private String id;
    @Column(nullable = false, length = 255)
    private String tenantId;
    @Column(nullable = false, length = 255)
    private String learningAssignmentId;
    @Column(nullable = false, length = 255)
    private String learnerUserId;
    @Column(nullable = false, length = 255)
    private String runId;
    @Column(nullable = false, columnDefinition = "text")
    private String content;
    @Column(nullable = false)
    private Instant submittedAt;

    protected LearningAssignmentSubmission() {
    }

    public LearningAssignmentSubmission(String tenantId, String learningAssignmentId,
                                        String learnerUserId, String runId, String content,
                                        Instant submittedAt) {
        this.id = UUID.randomUUID().toString();
        this.tenantId = required(tenantId, "tenantId");
        this.learningAssignmentId = required(learningAssignmentId, "learningAssignmentId");
        this.learnerUserId = required(learnerUserId, "learnerUserId");
        this.runId = required(runId, "runId");
        this.content = required(content, "content");
        this.submittedAt = submittedAt == null ? Instant.now() : submittedAt;
    }

    private static String required(String value, String name) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(name + " 不能为空");
        return normalized;
    }

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getLearningAssignmentId() { return learningAssignmentId; }
    public String getLearnerUserId() { return learnerUserId; }
    public String getRunId() { return runId; }
    public String getContent() { return content; }
    public Instant getSubmittedAt() { return submittedAt; }
}
