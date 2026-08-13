package org.mingharness.education;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/**
 * 课程知识依赖图中的一条有向边：prerequisiteConcept -> conceptKey。
 *
 * <p>边保留来源文档，便于教师审阅和论文实验追溯。当前图由教育知识源元数据派生，
 * 因此同一条逻辑边可以由多份资料共同支持。</p>
 */
@Entity
@Table(name = "harness_education_concept_dependencies", uniqueConstraints = @UniqueConstraint(
        name = "uk_harness_education_dependency_source_edge",
        columnNames = {"tenant_id", "source_document_id", "concept_key", "prerequisite_concept"}
))
public class EducationConceptDependency {

    @Id
    private String id;
    @Column(nullable = false, length = 255)
    private String tenantId;
    @Column(nullable = false, length = 128)
    private String subject;
    @Column(nullable = false, length = 128)
    private String gradeLevel;
    @Column(nullable = false, length = 128)
    private String curriculumVersion;
    @Column(nullable = false, length = 255)
    private String conceptKey;
    @Column(nullable = false, length = 255)
    private String prerequisiteConcept;
    @Column(nullable = false, length = 255)
    private String sourceDocumentId;
    @Column(nullable = false, length = 64)
    private String relationType;
    @Column(nullable = false)
    private double confidence;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;

    protected EducationConceptDependency() {
    }

    public EducationConceptDependency(String tenantId, String subject, String gradeLevel,
                                      String curriculumVersion, String conceptKey,
                                      String prerequisiteConcept, String sourceDocumentId,
                                      String relationType, double confidence) {
        this.id = UUID.randomUUID().toString();
        this.tenantId = required(tenantId, "tenantId");
        this.subject = required(subject, "subject");
        this.gradeLevel = required(gradeLevel, "gradeLevel");
        this.curriculumVersion = required(curriculumVersion, "curriculumVersion");
        this.conceptKey = required(conceptKey, "conceptKey");
        this.prerequisiteConcept = required(prerequisiteConcept, "prerequisiteConcept");
        this.sourceDocumentId = required(sourceDocumentId, "sourceDocumentId");
        this.relationType = normalize(relationType).isBlank() ? "PREREQUISITE" : normalize(relationType);
        this.confidence = boundedConfidence(confidence);
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    private static String required(String value, String name) {
        String normalized = normalize(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(name + " 不能为空");
        return normalized;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static double boundedConfidence(double value) {
        return Double.isFinite(value) ? Math.max(0.0, Math.min(1.0, value)) : 0.5;
    }

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getSubject() { return subject; }
    public String getGradeLevel() { return gradeLevel; }
    public String getCurriculumVersion() { return curriculumVersion; }
    public String getConceptKey() { return conceptKey; }
    public String getPrerequisiteConcept() { return prerequisiteConcept; }
    public String getSourceDocumentId() { return sourceDocumentId; }
    public String getRelationType() { return relationType; }
    public double getConfidence() { return confidence; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
