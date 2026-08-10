package org.mingharness.education;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * 课程作业业务实例，把“谁布置、给谁、属于哪门课、要达到什么知识目标”固定下来。
 * 接受作业后会绑定一个学习者画像和结构化学习目标，后续仍沿用教育 Run、测评和复习闭环。
 */
@Entity
@Table(name = "harness_learning_assignments")
public class LearningAssignment {

    @Id
    private String id;
    @Column(nullable = false, length = 255)
    private String tenantId;
    @Column(nullable = false, length = 255)
    private String teacherUserId;
    @Column(nullable = false, length = 255)
    private String learnerUserId;
    @Column(nullable = false, length = 255)
    private String title;
    @Column(nullable = false, columnDefinition = "text")
    private String instructions;
    @Column(nullable = false, length = 128)
    private String subject;
    @Column(nullable = false, length = 128)
    private String gradeLevel;
    @Column(nullable = false, length = 128)
    private String curriculumVersion;
    @Column(nullable = false, length = 255)
    private String conceptKey;
    @Column(nullable = false)
    private double targetMastery;
    private Instant dueAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LearningAssignmentStatus status;
    private String learnerProfileId;
    private String learningGoalId;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;
    private Instant acceptedAt;
    private Instant completedAt;

    protected LearningAssignment() {
    }

    public LearningAssignment(String tenantId, String teacherUserId, String learnerUserId,
                              String title, String instructions, String subject,
                              String gradeLevel, String curriculumVersion, String conceptKey,
                              double targetMastery, Instant dueAt) {
        this.id = UUID.randomUUID().toString();
        this.tenantId = required(tenantId, "tenantId");
        this.teacherUserId = required(teacherUserId, "teacherUserId");
        this.learnerUserId = required(learnerUserId, "learnerUserId");
        this.title = required(title, "title");
        this.instructions = required(instructions, "instructions");
        this.subject = required(subject, "subject");
        this.gradeLevel = required(gradeLevel, "gradeLevel");
        this.curriculumVersion = required(curriculumVersion, "curriculumVersion");
        this.conceptKey = required(conceptKey, "conceptKey");
        this.targetMastery = clampTarget(targetMastery);
        this.dueAt = dueAt;
        this.status = LearningAssignmentStatus.ASSIGNED;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void accept(String learnerProfileId, String learningGoalId, Instant acceptedAt) {
        if (status != LearningAssignmentStatus.ASSIGNED) {
            throw new IllegalStateException("只有已布置的作业可以接受");
        }
        this.learnerProfileId = required(learnerProfileId, "learnerProfileId");
        this.learningGoalId = required(learningGoalId, "learningGoalId");
        this.acceptedAt = acceptedAt == null ? Instant.now() : acceptedAt;
        this.status = LearningAssignmentStatus.ACCEPTED;
        this.updatedAt = Instant.now();
    }

    public void complete(Instant completedAt) {
        if (status != LearningAssignmentStatus.ACCEPTED) return;
        this.status = LearningAssignmentStatus.COMPLETED;
        this.completedAt = completedAt == null ? Instant.now() : completedAt;
        this.updatedAt = Instant.now();
    }

    public void cancel() {
        if (status == LearningAssignmentStatus.COMPLETED) {
            throw new IllegalStateException("已完成的作业不能取消");
        }
        status = LearningAssignmentStatus.CANCELLED;
        updatedAt = Instant.now();
    }

    private static double clampTarget(double value) {
        if (!Double.isFinite(value)) return 0.8;
        double bounded = Math.max(0.01, Math.min(1.0, value));
        return bounded;
    }

    private static String required(String value, String name) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(name + " 不能为空");
        return normalized;
    }

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getTeacherUserId() { return teacherUserId; }
    public String getLearnerUserId() { return learnerUserId; }
    public String getTitle() { return title; }
    public String getInstructions() { return instructions; }
    public String getSubject() { return subject; }
    public String getGradeLevel() { return gradeLevel; }
    public String getCurriculumVersion() { return curriculumVersion; }
    public String getConceptKey() { return conceptKey; }
    public double getTargetMastery() { return targetMastery; }
    public Instant getDueAt() { return dueAt; }
    public LearningAssignmentStatus getStatus() { return status; }
    public String getLearnerProfileId() { return learnerProfileId; }
    public String getLearningGoalId() { return learningGoalId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public Instant getCompletedAt() { return completedAt; }
}
