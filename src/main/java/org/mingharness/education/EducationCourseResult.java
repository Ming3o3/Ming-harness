package org.mingharness.education;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/** 课程结课时的不可变汇总事实；后续保持度复习不会改写本结果。 */
@Entity
@Table(name = "harness_education_course_results", uniqueConstraints = @UniqueConstraint(
        name = "uk_harness_education_course_result_course", columnNames = {"tenant_id", "course_id"}))
public class EducationCourseResult {

    @Id
    private String id;
    @Column(nullable = false, length = 255)
    private String tenantId;
    @Column(nullable = false, length = 255)
    private String courseId;
    @Column(nullable = false)
    private long activeLearnerTotal;
    @Column(nullable = false)
    private long learnersWithAssignments;
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
    @Column(nullable = false)
    private Instant completedAt;
    @Column(nullable = false, length = 255)
    private String completedByUserId;
    @Column(nullable = false)
    private Instant createdAt;

    protected EducationCourseResult() {
    }

    public EducationCourseResult(String tenantId, String courseId, long activeLearnerTotal,
                                 long learnersWithAssignments, long effectiveAssignmentTotal,
                                 long assignmentCompleted, long assignmentVerified,
                                 long submissionCovered, double averageMasteryProgress,
                                 double averageMasteryGain, Instant completedAt,
                                 String completedByUserId) {
        this.id = UUID.randomUUID().toString();
        this.tenantId = required(tenantId, "tenantId");
        this.courseId = required(courseId, "courseId");
        this.activeLearnerTotal = nonNegative(activeLearnerTotal, "activeLearnerTotal");
        this.learnersWithAssignments = nonNegative(learnersWithAssignments, "learnersWithAssignments");
        this.effectiveAssignmentTotal = nonNegative(effectiveAssignmentTotal, "effectiveAssignmentTotal");
        this.assignmentCompleted = nonNegative(assignmentCompleted, "assignmentCompleted");
        this.assignmentVerified = nonNegative(assignmentVerified, "assignmentVerified");
        this.submissionCovered = nonNegative(submissionCovered, "submissionCovered");
        this.averageMasteryProgress = bounded(averageMasteryProgress);
        this.averageMasteryGain = Double.isFinite(averageMasteryGain) ? averageMasteryGain : 0.0;
        this.completedAt = completedAt == null ? Instant.now() : completedAt;
        this.completedByUserId = required(completedByUserId, "completedByUserId");
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
    public String getCourseId() { return courseId; }
    public long getActiveLearnerTotal() { return activeLearnerTotal; }
    public long getLearnersWithAssignments() { return learnersWithAssignments; }
    public long getEffectiveAssignmentTotal() { return effectiveAssignmentTotal; }
    public long getAssignmentCompleted() { return assignmentCompleted; }
    public long getAssignmentVerified() { return assignmentVerified; }
    public long getSubmissionCovered() { return submissionCovered; }
    public double getAverageMasteryProgress() { return averageMasteryProgress; }
    public double getAverageMasteryGain() { return averageMasteryGain; }
    public Instant getCompletedAt() { return completedAt; }
    public String getCompletedByUserId() { return completedByUserId; }
    public Instant getCreatedAt() { return createdAt; }
}
