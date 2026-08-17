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
        String submissionType,
        String programmingLanguage,
        String codeEvaluationStatus,
        String codeDiagnostics,
        String codeDiagnosticCategory,
        String codeBehaviorStatus,
        int codeTestCaseCount,
        int codePassedTestCaseCount,
        double codeTestPassRate,
        long codeEvaluationDurationMs,
        Instant submittedAt
) {
    public static LearningAssignmentSubmissionView from(LearningAssignmentSubmission submission) {
        return new LearningAssignmentSubmissionView(submission.getId(),
                submission.getLearningAssignmentId(), submission.getLearnerUserId(),
                submission.getRunId(), submission.getContent(),
                submission.getSubmissionType().name(), submission.getProgrammingLanguage(),
                submission.getCodeEvaluationStatus().name(), submission.getCodeDiagnostics(),
                submission.getCodeDiagnosticCategory().name(), submission.getCodeBehaviorStatus().name(),
                submission.getCodeTestCaseCount(), submission.getCodePassedTestCaseCount(),
                submission.getCodeTestPassRate(),
                submission.getCodeEvaluationDurationMs(), submission.getSubmittedAt());
    }
}
