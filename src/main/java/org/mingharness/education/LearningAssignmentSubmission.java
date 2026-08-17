package org.mingharness.education;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
    @Enumerated(EnumType.STRING)
    @Column(name = "submission_type", nullable = false, length = 16)
    private LearningAssignmentSubmissionType submissionType;
    @Column(name = "programming_language", length = 64)
    private String programmingLanguage;
    @Enumerated(EnumType.STRING)
    @Column(name = "code_evaluation_status", nullable = false, length = 32)
    private CodeEvaluationStatus codeEvaluationStatus;
    @Column(name = "code_diagnostics", columnDefinition = "text")
    private String codeDiagnostics;
    @Enumerated(EnumType.STRING)
    @Column(name = "code_diagnostic_category", nullable = false, length = 32)
    private CodeDiagnosticCategory codeDiagnosticCategory;
    @Column(name = "code_evaluation_duration_ms", nullable = false)
    private long codeEvaluationDurationMs;
    @Column(nullable = false)
    private Instant submittedAt;

    protected LearningAssignmentSubmission() {
    }

    public LearningAssignmentSubmission(String tenantId, String learningAssignmentId,
                                        String learnerUserId, String runId, String content,
                                        Instant submittedAt) {
        this(tenantId, learningAssignmentId, learnerUserId, runId, content,
                LearningAssignmentSubmissionType.TEXT, null,
                CodeEvaluationStatus.NOT_REQUESTED, null, CodeDiagnosticCategory.NONE, 0, submittedAt);
    }

    public LearningAssignmentSubmission(String tenantId, String learningAssignmentId,
                                        String learnerUserId, String runId, String content,
                                        LearningAssignmentSubmissionType submissionType,
                                        String programmingLanguage,
                                        CodeEvaluationStatus codeEvaluationStatus,
                                        String codeDiagnostics, long codeEvaluationDurationMs,
                                        Instant submittedAt) {
        this(tenantId, learningAssignmentId, learnerUserId, runId, content, submissionType,
                programmingLanguage, codeEvaluationStatus, codeDiagnostics, CodeDiagnosticCategory.NONE,
                codeEvaluationDurationMs, submittedAt);
    }

    public LearningAssignmentSubmission(String tenantId, String learningAssignmentId,
                                        String learnerUserId, String runId, String content,
                                        LearningAssignmentSubmissionType submissionType,
                                        String programmingLanguage,
                                        CodeEvaluationStatus codeEvaluationStatus,
                                        String codeDiagnostics,
                                        CodeDiagnosticCategory codeDiagnosticCategory,
                                        long codeEvaluationDurationMs,
                                        Instant submittedAt) {
        this.id = UUID.randomUUID().toString();
        this.tenantId = required(tenantId, "tenantId");
        this.learningAssignmentId = required(learningAssignmentId, "learningAssignmentId");
        this.learnerUserId = required(learnerUserId, "learnerUserId");
        this.runId = required(runId, "runId");
        this.content = required(content, "content");
        this.submissionType = submissionType == null ? LearningAssignmentSubmissionType.TEXT : submissionType;
        this.programmingLanguage = optional(programmingLanguage);
        this.codeEvaluationStatus = codeEvaluationStatus == null
                ? CodeEvaluationStatus.NOT_REQUESTED : codeEvaluationStatus;
        this.codeDiagnostics = optional(codeDiagnostics);
        this.codeDiagnosticCategory = codeDiagnosticCategory == null
                ? CodeDiagnosticCategory.NONE : codeDiagnosticCategory;
        this.codeEvaluationDurationMs = Math.max(0, codeEvaluationDurationMs);
        this.submittedAt = submittedAt == null ? Instant.now() : submittedAt;
    }

    private static String required(String value, String name) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(name + " 不能为空");
        return normalized;
    }

    private static String optional(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getLearningAssignmentId() { return learningAssignmentId; }
    public String getLearnerUserId() { return learnerUserId; }
    public String getRunId() { return runId; }
    public String getContent() { return content; }
    public LearningAssignmentSubmissionType getSubmissionType() { return submissionType; }
    public String getProgrammingLanguage() { return programmingLanguage; }
    public CodeEvaluationStatus getCodeEvaluationStatus() { return codeEvaluationStatus; }
    public String getCodeDiagnostics() { return codeDiagnostics; }
    public CodeDiagnosticCategory getCodeDiagnosticCategory() { return codeDiagnosticCategory; }
    public long getCodeEvaluationDurationMs() { return codeEvaluationDurationMs; }
    public Instant getSubmittedAt() { return submittedAt; }
}
