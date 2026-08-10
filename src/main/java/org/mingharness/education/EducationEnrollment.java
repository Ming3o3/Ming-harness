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

/** 课程名单事实；移除成员只改变名单状态，不破坏历史学习证据。 */
@Entity
@Table(name = "harness_education_enrollments", uniqueConstraints = @UniqueConstraint(
        name = "uk_harness_education_enrollment_member",
        columnNames = {"tenant_id", "course_id", "learner_user_id"}))
public class EducationEnrollment {

    @Id
    private String id;
    @Column(nullable = false, length = 255)
    private String tenantId;
    @Column(nullable = false, length = 255)
    private String courseId;
    @Column(nullable = false, length = 255)
    private String learnerUserId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private EducationEnrollmentStatus status;
    @Column(nullable = false)
    private Instant enrolledAt;
    private Instant removedAt;
    @Column(nullable = false)
    private Instant updatedAt;

    protected EducationEnrollment() {
    }

    public EducationEnrollment(String tenantId, String courseId, String learnerUserId,
                               Instant enrolledAt) {
        this.id = UUID.randomUUID().toString();
        this.tenantId = required(tenantId, "tenantId");
        this.courseId = required(courseId, "courseId");
        this.learnerUserId = required(learnerUserId, "learnerUserId");
        this.status = EducationEnrollmentStatus.ACTIVE;
        this.enrolledAt = enrolledAt == null ? Instant.now() : enrolledAt;
        this.updatedAt = this.enrolledAt;
    }

    public void reactivate(Instant reactivatedAt) {
        this.status = EducationEnrollmentStatus.ACTIVE;
        this.removedAt = null;
        this.updatedAt = reactivatedAt == null ? Instant.now() : reactivatedAt;
    }

    public void remove(Instant removedAt) {
        this.status = EducationEnrollmentStatus.REMOVED;
        this.removedAt = removedAt == null ? Instant.now() : removedAt;
        this.updatedAt = this.removedAt;
    }

    private static String required(String value, String name) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(name + " 不能为空");
        return normalized;
    }

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getCourseId() { return courseId; }
    public String getLearnerUserId() { return learnerUserId; }
    public EducationEnrollmentStatus getStatus() { return status; }
    public Instant getEnrolledAt() { return enrolledAt; }
    public Instant getRemovedAt() { return removedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
