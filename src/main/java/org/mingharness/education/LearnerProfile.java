package org.mingharness.education;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/** 按组织和用户隔离的学习者课程画像。 */
@Entity
@Table(name = "harness_learner_profiles", uniqueConstraints = @UniqueConstraint(
        name = "uk_harness_learner_profile_scope",
        columnNames = {"tenant_id", "user_id", "subject", "grade_level", "curriculum_version"}
))
public class LearnerProfile {

    @Id
    private String id;
    @Column(nullable = false)
    private String tenantId;
    @Column(nullable = false, length = 255)
    private String userId;
    @Column(nullable = false, length = 128)
    private String subject;
    @Column(nullable = false, length = 128)
    private String gradeLevel;
    @Column(nullable = false, length = 128)
    private String curriculumVersion;
    @Column(length = 512)
    private String learningGoal;
    @Column(nullable = false, length = 32)
    private String language;
    @Column(nullable = false)
    private boolean active;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;

    protected LearnerProfile() {
    }

    public LearnerProfile(String tenantId, String userId, String subject, String gradeLevel,
                          String curriculumVersion, String learningGoal, String language) {
        this.id = UUID.randomUUID().toString();
        this.tenantId = required(tenantId, "tenantId");
        this.userId = required(userId, "userId");
        this.createdAt = Instant.now();
        this.active = true;
        update(subject, gradeLevel, curriculumVersion, learningGoal, language);
    }

    public void update(String subject, String gradeLevel, String curriculumVersion,
                       String learningGoal, String language) {
        this.subject = required(subject, "subject");
        this.gradeLevel = required(gradeLevel, "gradeLevel");
        this.curriculumVersion = required(curriculumVersion, "curriculumVersion");
        this.learningGoal = normalize(learningGoal);
        this.language = normalize(language).isBlank() ? "zh-CN" : normalize(language);
        this.updatedAt = Instant.now();
    }

    public void activate() {
        this.active = true;
        this.updatedAt = Instant.now();
    }

    public void deactivate() {
        this.active = false;
        this.updatedAt = Instant.now();
    }

    private static String required(String value, String name) {
        String normalized = normalize(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(name + " 不能为空");
        return normalized;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getUserId() { return userId; }
    public String getSubject() { return subject; }
    public String getGradeLevel() { return gradeLevel; }
    public String getCurriculumVersion() { return curriculumVersion; }
    public String getLearningGoal() { return learningGoal; }
    public String getLanguage() { return language; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
