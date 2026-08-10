package org.mingharness.education;

/** 作业业务状态变化对应的站内触达事件。 */
public enum LearningAssignmentNotificationType {
    ASSIGNED,
    ACCEPTED,
    EVIDENCE_REQUIRED,
    RETRY_REQUIRED,
    OVERDUE,
    COMPLETED,
    REVIEW_REQUIRED,
    REVIEW_VERIFIED,
    REVISION_REQUIRED,
    CANCELLED,
    SUBMISSION_RECEIVED,
    FEEDBACK,
    FEEDBACK_ACKNOWLEDGED
}
