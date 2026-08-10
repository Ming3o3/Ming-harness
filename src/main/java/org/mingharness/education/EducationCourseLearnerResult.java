package org.mingharness.education;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/** 课程结课时的单个学习者结果事实，和课程汇总一起冻结。 */
@Entity
@Table(name = "harness_education_course_learner_results", uniqueConstraints = @UniqueConstraint(
        name = "uk_harness_education_course_learner_result_member",
        columnNames = {"tenant_id", "course_result_id", "learner_user_id"}))
public class EducationCourseLearnerResult {

    @Id
    private String id;
    @Column(nullable = false, length = 255)
    private String tenantId;
    @Column(nullable = false, length = 255)
    private String courseResultId;
    @Column(nullable = false, length = 255)
    private String courseId;
    @Column(nullable = false, length = 255)
    private String learnerUserId;
    @Column(nullable = false)
    private long effectiveAssignmentTotal;
    @Column(nullable = false)
    private long assignmentCompleted;
    @Column(nullable = false)
    private long assignmentVerified;
    @Column(nullable = false)
    private long submissionCovered;
    @Column(nullable = false)
    private double averageMasteryProgress;
    @Column(nullable = false)
    private double averageMasteryGain;
    private Instant lastActivityAt;
    @Column(nullable = false)
    private Instant createdAt;

    protected EducationCourseLearnerResult() {
    }

    public EducationCourseLearnerResult(String tenantId, String courseResultId, String courseId,
                                        String learnerUserId, long effectiveAssignmentTotal,
                                        long assignmentCompleted, long assignmentVerified,
                                        long submissionCovered, double averageMasteryProgress,
                                        double averageMasteryGain, Instant lastActivityAt) {
        this.id = UUID.randomUUID().toString();
        this.tenantId = required(tenantId, "tenantId");
        this.courseResultId = required(courseResultId, "courseResultId");
        this.courseId = required(courseId, "courseId");
        this.learnerUserId = required(learnerUserId, "learnerUserId");
        this.effectiveAssignmentTotal = nonNegative(effectiveAssignmentTotal, "effectiveAssignmentTotal");
        this.assignmentCompleted = nonNegative(assignmentCompleted, "assignmentCompleted");
        this.assignmentVerified = nonNegative(assignmentVerified, "assignmentVerified");
        this.submissionCovered = nonNegative(submissionCovered, "submissionCovered");
        this.averageMasteryProgress = bounded(averageMasteryProgress);
        this.averageMasteryGain = Double.isFinite(averageMasteryGain) ? averageMasteryGain : 0.0;
        this.lastActivityAt = lastActivityAt;
        this.createdAt = Instant.now();
    }

    private static long nonNegative(long value, String name) {
        if (value < 0) throw new IllegalArgumentException(name + " 不能为负数");
        return value;
    }

    private static double bounded(double value) {
        if (!Double.isFinite(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static String required(String value, String name) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(name + " 不能为空");
        return normalized;
    }

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getCourseResultId() { return courseResultId; }
    public String getCourseId() { return courseId; }
    public String getLearnerUserId() { return learnerUserId; }
    public long getEffectiveAssignmentTotal() { return effectiveAssignmentTotal; }
    public long getAssignmentCompleted() { return assignmentCompleted; }
    public long getAssignmentVerified() { return assignmentVerified; }
    public long getSubmissionCovered() { return submissionCovered; }
    public double getAverageMasteryProgress() { return averageMasteryProgress; }
    public double getAverageMasteryGain() { return averageMasteryGain; }
    public Instant getLastActivityAt() { return lastActivityAt; }
    public Instant getCreatedAt() { return createdAt; }
}
