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
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LearningAssignmentReviewStatus reviewStatus;
    private String learnerProfileId;
    private String learningGoalId;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;
    private Instant acceptedAt;
    private Instant completedAt;
    private Instant teacherReviewedAt;
    private String teacherReviewerUserId;
    @Column(columnDefinition = "text")
    private String teacherReviewNote;

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
        this.reviewStatus = LearningAssignmentReviewStatus.NOT_REQUIRED;
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
        if (status != LearningAssignmentStatus.ACCEPTED
                && status != LearningAssignmentStatus.AWAITING_EVIDENCE
                && status != LearningAssignmentStatus.OVERDUE) return;
        this.status = LearningAssignmentStatus.COMPLETED;
        this.reviewStatus = LearningAssignmentReviewStatus.PENDING;
        this.completedAt = completedAt == null ? Instant.now() : completedAt;
        this.teacherReviewedAt = null;
        this.teacherReviewerUserId = null;
        this.teacherReviewNote = null;
        this.updatedAt = Instant.now();
    }

    public void verifyByTeacher(String reviewerUserId, String reviewNote, Instant reviewedAt) {
        if (status != LearningAssignmentStatus.COMPLETED) {
            throw new IllegalStateException("只有已完成的作业可以提交教师确认");
        }
        if (reviewStatus != LearningAssignmentReviewStatus.PENDING) {
            throw new IllegalStateException("当前作业不在待教师确认状态");
        }
        this.teacherReviewerUserId = required(reviewerUserId, "teacherReviewerUserId");
        this.teacherReviewNote = reviewNote == null || reviewNote.isBlank() ? null : reviewNote.trim();
        this.teacherReviewedAt = reviewedAt == null ? Instant.now() : reviewedAt;
        this.reviewStatus = LearningAssignmentReviewStatus.VERIFIED;
        this.updatedAt = Instant.now();
    }

    public void returnForRevision(String reviewerUserId, String reviewNote, Instant reviewedAt) {
        if (status != LearningAssignmentStatus.COMPLETED) {
            throw new IllegalStateException("只有已完成的作业可以退回返工");
        }
        if (reviewStatus != LearningAssignmentReviewStatus.PENDING) {
            throw new IllegalStateException("当前作业不在待教师确认状态");
        }
        this.teacherReviewerUserId = required(reviewerUserId, "teacherReviewerUserId");
        this.teacherReviewNote = required(reviewNote, "teacherReviewNote");
        this.teacherReviewedAt = reviewedAt == null ? Instant.now() : reviewedAt;
        this.reviewStatus = LearningAssignmentReviewStatus.REVISION_REQUIRED;
        this.status = LearningAssignmentStatus.RETRY_REQUIRED;
        this.updatedAt = Instant.now();
    }

    public boolean isOverdue(Instant reference) {
        return dueAt != null
                && !dueAt.isAfter(reference == null ? Instant.now() : reference)
                && (status == LearningAssignmentStatus.ASSIGNED
                || status == LearningAssignmentStatus.ACCEPTED);
    }

    public void markOverdue(Instant observedAt) {
        if (status != LearningAssignmentStatus.ASSIGNED
                && status != LearningAssignmentStatus.ACCEPTED) return;
        status = LearningAssignmentStatus.OVERDUE;
        updatedAt = observedAt == null ? Instant.now() : observedAt;
    }

    public void reschedule(Instant nextDueAt) {
        if (nextDueAt == null || !nextDueAt.isAfter(Instant.now())) {
            throw new IllegalArgumentException("新的截止时间必须晚于当前时间");
        }
        if (status == LearningAssignmentStatus.COMPLETED
                || status == LearningAssignmentStatus.CANCELLED) {
            throw new IllegalStateException("已完成或已取消的作业不能重新安排截止时间");
        }
        dueAt = nextDueAt;
        if (status == LearningAssignmentStatus.OVERDUE
                || status == LearningAssignmentStatus.AWAITING_EVIDENCE
                || status == LearningAssignmentStatus.RETRY_REQUIRED) {
            status = learningGoalId == null
                    ? LearningAssignmentStatus.ASSIGNED : LearningAssignmentStatus.ACCEPTED;
        }
        updatedAt = Instant.now();
    }

    /** 教育 Run 成功但尚未有测评证据时，作业进入待补证据；证据写入后恢复执行中。 */
    public void awaitEvidence(Instant observedAt) {
        if (status != LearningAssignmentStatus.ACCEPTED
                && status != LearningAssignmentStatus.OVERDUE
                && status != LearningAssignmentStatus.RETRY_REQUIRED) return;
        status = LearningAssignmentStatus.AWAITING_EVIDENCE;
        updatedAt = observedAt == null ? Instant.now() : observedAt;
    }

    /** 教育 Run 失败、超时或取消后，保留作业上下文并等待学习者从作业入口重试。 */
    public void requireRetry(Instant observedAt) {
        if (status != LearningAssignmentStatus.ACCEPTED
                && status != LearningAssignmentStatus.OVERDUE
                && status != LearningAssignmentStatus.RETRY_REQUIRED) return;
        status = LearningAssignmentStatus.RETRY_REQUIRED;
        updatedAt = observedAt == null ? Instant.now() : observedAt;
    }

    /** 学习者已从作业入口发起新一轮 Run，结束上一轮失败状态。 */
    public void resumeForRetry(Instant startedAt) {
        if (status != LearningAssignmentStatus.RETRY_REQUIRED) return;
        status = LearningAssignmentStatus.ACCEPTED;
        updatedAt = startedAt == null ? Instant.now() : startedAt;
    }

    public void resumeAfterEvidence(Instant resumedAt) {
        if (status != LearningAssignmentStatus.AWAITING_EVIDENCE
                && status != LearningAssignmentStatus.RETRY_REQUIRED) return;
        status = LearningAssignmentStatus.ACCEPTED;
        updatedAt = resumedAt == null ? Instant.now() : resumedAt;
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
    public LearningAssignmentReviewStatus getReviewStatus() { return reviewStatus; }
    public String getLearnerProfileId() { return learnerProfileId; }
    public String getLearningGoalId() { return learningGoalId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getTeacherReviewedAt() { return teacherReviewedAt; }
    public String getTeacherReviewerUserId() { return teacherReviewerUserId; }
    public String getTeacherReviewNote() { return teacherReviewNote; }
}
