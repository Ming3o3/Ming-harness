package org.mingharness.education.api;

/** 教师量规与第二评分者量规的可审计共识判定。 */
public record LearningAssignmentEvaluationConsensusView(
        String learningAssignmentId,
        String status,
        String teacherEvaluationId,
        String independentEvaluationId,
        double contentScoreDifference,
        double evidenceScoreDifference,
        double transferScoreDifference,
        String rule
) {
}
