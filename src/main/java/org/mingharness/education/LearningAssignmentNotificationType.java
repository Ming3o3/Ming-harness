package org.mingharness.education;

/** 作业业务状态变化对应的站内触达事件。 */
public enum LearningAssignmentNotificationType {
    ASSIGNED,
    ACCEPTED,
    EVIDENCE_REQUIRED,
    OVERDUE,
    COMPLETED,
    REVIEW_REQUIRED,
    REVIEW_VERIFIED,
    CANCELLED,
    FEEDBACK,
    FEEDBACK_ACKNOWLEDGED
}
