package org.mingharness.education;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 教育知识库中与通用文档绑定的课程元数据。
 *
 * <p>正文仍由 context 模块保存；本实体只保存课程约束和教学检索所需的结构化标签，
 * 使同一份文档可以继续被通用 Agent 使用，同时支持教育 Agent 的受约束召回。</p>
 */
@Entity
@Table(name = "harness_education_sources", uniqueConstraints = @UniqueConstraint(
        name = "uk_harness_education_source_document",
        columnNames = {"tenant_id", "document_id"}
))
public class EducationKnowledgeSource {

    @Id
    private String id;
    @Column(nullable = false)
    private String tenantId;
    @Column(nullable = false, length = 255)
    private String documentId;
    @Column(nullable = false, length = 128)
    private String subject;
    @Column(nullable = false, length = 128)
    private String gradeLevel;
    @Column(nullable = false, length = 128)
    private String curriculumVersion;
    @Column(length = 255)
    private String chapter;
    @Column(columnDefinition = "text")
    private String learningObjectives;
    @Column(columnDefinition = "text")
    private String conceptTags;
    @Column(columnDefinition = "text")
    private String prerequisiteConcepts;
    @Column(nullable = false)
    private int difficultyLevel;
    @Column(nullable = false, length = 64)
    private String sourceType;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;
    private Instant deletedAt;

    protected EducationKnowledgeSource() {
    }

    public EducationKnowledgeSource(String tenantId, String documentId, String subject,
                                    String gradeLevel, String curriculumVersion,
                                    String chapter, String learningObjectives,
                                    String conceptTags, String prerequisiteConcepts,
                                    int difficultyLevel, String sourceType) {
        this.id = UUID.randomUUID().toString();
        this.tenantId = required(tenantId, "tenantId");
        this.documentId = required(documentId, "documentId");
        this.createdAt = Instant.now();
        update(subject, gradeLevel, curriculumVersion, chapter, learningObjectives,
                conceptTags, prerequisiteConcepts, difficultyLevel, sourceType);
    }

    public void update(String subject, String gradeLevel, String curriculumVersion,
                       String chapter, String learningObjectives, String conceptTags,
                       String prerequisiteConcepts, int difficultyLevel, String sourceType) {
        this.subject = required(subject, "subject");
        this.gradeLevel = required(gradeLevel, "gradeLevel");
        this.curriculumVersion = required(curriculumVersion, "curriculumVersion");
        this.chapter = normalize(chapter);
        this.learningObjectives = normalizeText(learningObjectives);
        this.conceptTags = normalizeList(conceptTags);
        this.prerequisiteConcepts = normalizeList(prerequisiteConcepts);
        this.difficultyLevel = Math.max(1, Math.min(5, difficultyLevel));
        this.sourceType = normalize(sourceType).isBlank() ? "TEXTBOOK" : normalize(sourceType);
        this.deletedAt = null;
        this.updatedAt = Instant.now();
    }

    public void markDeleted() {
        this.deletedAt = Instant.now();
        this.updatedAt = this.deletedAt;
    }

    public boolean isActive() {
        return deletedAt == null;
    }

    private static String required(String value, String name) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
        return normalized;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeList(String value) {
        if (value == null || value.isBlank()) return "";
        return Arrays.stream(value.split("[,，;；\\n]"))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .distinct()
                .sorted()
                .collect(Collectors.joining(","));
    }

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getDocumentId() { return documentId; }
    public String getSubject() { return subject; }
    public String getGradeLevel() { return gradeLevel; }
    public String getCurriculumVersion() { return curriculumVersion; }
    public String getChapter() { return chapter; }
    public String getLearningObjectives() { return learningObjectives; }
    public String getConceptTags() { return conceptTags; }
    public String getPrerequisiteConcepts() { return prerequisiteConcepts; }
    public int getDifficultyLevel() { return difficultyLevel; }
    public String getSourceType() { return sourceType; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }
}
