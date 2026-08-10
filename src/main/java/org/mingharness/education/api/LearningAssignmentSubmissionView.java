package org.mingharness.education.api;

import org.mingharness.education.LearningAssignmentSubmission;

import java.time.Instant;

/** 学习者提交物安全投影。 */
public record LearningAssignmentSubmissionView(
        String id,
        String learningAssignmentId,
        String learnerUserId,
        String runId,
        String content,
        Instant submittedAt
) {
    public static LearningAssignmentSubmissionView from(LearningAssignmentSubmission submission) {
        return new LearningAssignmentSubmissionView(submission.getId(),
                submission.getLearningAssignmentId(), submission.getLearnerUserId(),
                submission.getRunId(), submission.getContent(), submission.getSubmittedAt());
    }
}
