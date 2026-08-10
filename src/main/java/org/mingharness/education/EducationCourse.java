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

/**
 * 面向教师运营的课程实例。课程实例把课程上下文和学习者名单固定下来，
 * 作业只允许引用同一课程实例的元数据，避免批量布置时出现课程约束漂移。
 */
@Entity
@Table(name = "harness_education_courses", uniqueConstraints = @UniqueConstraint(
        name = "uk_harness_education_course_code", columnNames = {"tenant_id", "code"}))
public class EducationCourse {

    @Id
    private String id;
    @Column(nullable = false, length = 255)
    private String tenantId;
    @Column(nullable = false, length = 255)
    private String ownerUserId;
    @Column(nullable = false, length = 128)
    private String code;
    @Column(nullable = false, length = 255)
    private String title;
    @Column(nullable = false, length = 128)
    private String subject;
    @Column(nullable = false, length = 128)
    private String gradeLevel;
    @Column(nullable = false, length = 128)
    private String curriculumVersion;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private EducationCourseStatus status;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;

    protected EducationCourse() {
    }

    public EducationCourse(String tenantId, String ownerUserId, String code, String title,
                           String subject, String gradeLevel, String curriculumVersion) {
        this.id = UUID.randomUUID().toString();
        this.tenantId = required(tenantId, "tenantId");
        this.ownerUserId = required(ownerUserId, "ownerUserId");
        this.code = required(code, "code");
        this.title = required(title, "title");
        this.subject = required(subject, "subject");
        this.gradeLevel = required(gradeLevel, "gradeLevel");
        this.curriculumVersion = required(curriculumVersion, "curriculumVersion");
        this.status = EducationCourseStatus.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void archive(Instant archivedAt) {
        this.status = EducationCourseStatus.ARCHIVED;
        this.updatedAt = archivedAt == null ? Instant.now() : archivedAt;
    }

    public boolean isActive() {
        return status == EducationCourseStatus.ACTIVE;
    }

    private static String required(String value, String name) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(name + " 不能为空");
        return normalized;
    }

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getOwnerUserId() { return ownerUserId; }
    public String getCode() { return code; }
    public String getTitle() { return title; }
    public String getSubject() { return subject; }
    public String getGradeLevel() { return gradeLevel; }
    public String getCurriculumVersion() { return curriculumVersion; }
    public EducationCourseStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
