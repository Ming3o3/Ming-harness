package org.mingharness.education;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * 教师或独立评价者对单条教育检索证据的结构化判断。
 *
 * <p>判断只引用 Run 已冻结的证据快照，不允许客户端提交一个未被 Runtime
 * 授权的文档作为训练样本。记录保留量规版本和创建时间，后续可以按版本校准
 * 排序权重，而不会把历史评价重解释成新标签。</p>
 */
@Entity
@Table(name = "harness_education_retrieval_judgments")
public class EducationRetrievalJudgment {

    public static final String CURRENT_RUBRIC_VERSION = "retrieval-v1";

    @Id
    private String id;
    @Column(nullable = false, length = 255)
    private String tenantId;
    @Column(nullable = false, length = 255)
    private String runId;
    @Column(length = 255)
    private String stepId;
    @Column(nullable = false, length = 255)
    private String evaluatorUserId;
    @Column(length = 255)
    private String documentId;
    @Column(nullable = false, length = 1500)
    private String evidenceCitation;
    @Column(nullable = false)
    private int targetGroundingScore;
    @Column(nullable = false)
    private int prerequisiteUtilityScore;
    @Column(nullable = false)
    private int difficultyFitScore;
    @Column(nullable = false)
    private int overallUtilityScore;
    @Column(nullable = false, length = 64)
    private String rubricVersion;
    @Column(columnDefinition = "text")
    private String note;
    @Column(nullable = false)
    private Instant createdAt;

    protected EducationRetrievalJudgment() {
    }

    public EducationRetrievalJudgment(String tenantId, String runId, String stepId,
                                      String evaluatorUserId, String documentId,
                                      String evidenceCitation, int targetGroundingScore,
                                      int prerequisiteUtilityScore, int difficultyFitScore,
                                      int overallUtilityScore, String note, Instant createdAt) {
        this.id = UUID.randomUUID().toString();
        this.tenantId = required(tenantId, "tenantId");
        this.runId = required(runId, "runId");
        this.stepId = optional(stepId);
        this.evaluatorUserId = required(evaluatorUserId, "evaluatorUserId");
        this.documentId = optional(documentId);
        this.evidenceCitation = required(evidenceCitation, "evidenceCitation");
        this.targetGroundingScore = score(targetGroundingScore, "targetGroundingScore");
        this.prerequisiteUtilityScore = score(prerequisiteUtilityScore, "prerequisiteUtilityScore");
        this.difficultyFitScore = score(difficultyFitScore, "difficultyFitScore");
        this.overallUtilityScore = score(overallUtilityScore, "overallUtilityScore");
        this.rubricVersion = CURRENT_RUBRIC_VERSION;
        this.note = optional(note);
        this.createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    private static int score(int value, String name) {
        if (value < 1 || value > 5) throw new IllegalArgumentException(name + " 必须在 1 到 5 之间");
        return value;
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
    public String getRunId() { return runId; }
    public String getStepId() { return stepId; }
    public String getEvaluatorUserId() { return evaluatorUserId; }
    public String getDocumentId() { return documentId; }
    public String getEvidenceCitation() { return evidenceCitation; }
    public int getTargetGroundingScore() { return targetGroundingScore; }
    public int getPrerequisiteUtilityScore() { return prerequisiteUtilityScore; }
    public int getDifficultyFitScore() { return difficultyFitScore; }
    public int getOverallUtilityScore() { return overallUtilityScore; }
    public String getRubricVersion() { return rubricVersion; }
    public String getNote() { return note; }
    public Instant getCreatedAt() { return createdAt; }
}
