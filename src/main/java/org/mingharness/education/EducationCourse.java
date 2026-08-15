package org.mingharness.education;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.security.SecureRandom;
import java.util.UUID;

/**
 * 面向教师运营的课程实例。课程实例把课程上下文和学习者名单固定下来，
 * 作业只允许引用同一课程实例的元数据，避免批量布置时出现课程约束漂移。
 */
@Entity
@Table(name = "harness_education_courses", uniqueConstraints = {
        @UniqueConstraint(name = "uk_harness_education_course_code", columnNames = {"tenant_id", "code"}),
        @UniqueConstraint(name = "uk_harness_education_course_join_code", columnNames = {"tenant_id", "join_code"})
})
public class EducationCourse {

    private static final SecureRandom JOIN_CODE_RANDOM = new SecureRandom();
    private static final char[] JOIN_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    @Id
    private String id;
    @Column(nullable = false, length = 255)
    private String tenantId;
    @Column(nullable = false, length = 255)
    private String ownerUserId;
    @Column(nullable = false, length = 128)
    private String code;
    /** 面向学生分享的短邀请码；不替代课程 ID，只用于低门槛加入课程。 */
    @Column(nullable = false, length = 12)
    private String joinCode;
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
    private Instant completedAt;
    @Column(length = 255)
    private String completedByUserId;
    @Column(columnDefinition = "text")
    private String completionNote;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;

    protected EducationCourse() {
    }

    public EducationCourse(String tenantId, String ownerUserId, String code, String title,
                           String subject, String gradeLevel, String curriculumVersion) {
        this(tenantId, ownerUserId, code, title, subject, gradeLevel, curriculumVersion, newJoinCode());
    }

    public EducationCourse(String tenantId, String ownerUserId, String code, String title,
                           String subject, String gradeLevel, String curriculumVersion,
                           String joinCode) {
        this.id = UUID.randomUUID().toString();
        this.tenantId = required(tenantId, "tenantId");
        this.ownerUserId = required(ownerUserId, "ownerUserId");
        this.code = required(code, "code");
        this.joinCode = normalizeJoinCode(joinCode);
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

    /** 课程只有在全部有效作业完成并经教师确认后才能结课。 */
    public void complete(String completedByUserId, String completionNote, Instant completedAt) {
        if (status == EducationCourseStatus.ARCHIVED) {
            throw new IllegalStateException("已归档课程不能结课");
        }
        if (status == EducationCourseStatus.COMPLETED) return;
        this.status = EducationCourseStatus.COMPLETED;
        this.completedAt = completedAt == null ? Instant.now() : completedAt;
        this.completedByUserId = required(completedByUserId, "completedByUserId");
        this.completionNote = completionNote == null || completionNote.isBlank()
                ? null : completionNote.trim();
        this.updatedAt = this.completedAt;
    }

    public boolean isActive() {
        return status == EducationCourseStatus.ACTIVE;
    }

    private static String required(String value, String name) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(name + " 不能为空");
        return normalized;
    }

    private static String normalizeJoinCode(String value) {
        String normalized = required(value, "joinCode").toUpperCase(java.util.Locale.ROOT);
        if (!normalized.matches("[A-Z0-9]{6,12}")) {
            throw new IllegalArgumentException("joinCode 格式不合法");
        }
        return normalized;
    }

    private static String newJoinCode() {
        char[] code = new char[8];
        for (int index = 0; index < code.length; index++) {
            code[index] = JOIN_CODE_ALPHABET[JOIN_CODE_RANDOM.nextInt(JOIN_CODE_ALPHABET.length)];
        }
        return new String(code);
    }

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getOwnerUserId() { return ownerUserId; }
    public String getCode() { return code; }
    public String getJoinCode() { return joinCode; }
    public String getTitle() { return title; }
    public String getSubject() { return subject; }
    public String getGradeLevel() { return gradeLevel; }
    public String getCurriculumVersion() { return curriculumVersion; }
    public EducationCourseStatus getStatus() { return status; }
    public Instant getCompletedAt() { return completedAt; }
    public String getCompletedByUserId() { return completedByUserId; }
    public String getCompletionNote() { return completionNote; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
