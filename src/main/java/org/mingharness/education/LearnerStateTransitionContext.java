package org.mingharness.education;

/**
 * 一次学习者状态更新携带的证据上下文。
 *
 * <p>上下文只保存审计所需的结构化摘要，不保存完整代码或模型提示词。Run、测评和
 * 代码行为证据通过同一个状态更新入口写入，保证状态转移可以回到业务事实。</p>
 */
public record LearnerStateTransitionContext(
        String runId,
        String evidenceSource,
        String assessmentType,
        String evidenceText,
        String diagnosticCategory,
        Double behaviorTestPassRate,
        int difficultyLevel,
        double evidenceWeight,
        boolean hintUsed,
        boolean independentEvidence
) {

    public LearnerStateTransitionContext {
        runId = optional(runId);
        evidenceSource = optional(evidenceSource);
        assessmentType = optional(assessmentType);
        evidenceText = optional(evidenceText);
        diagnosticCategory = optional(diagnosticCategory);
        behaviorTestPassRate = behaviorTestPassRate == null || !Double.isFinite(behaviorTestPassRate)
                ? null : bounded(behaviorTestPassRate);
        difficultyLevel = Math.max(1, Math.min(5, difficultyLevel));
        evidenceWeight = bounded(evidenceWeight);
    }

    public static LearnerStateTransitionContext manualCalibration() {
        return new LearnerStateTransitionContext(null, "MANUAL_CALIBRATION", null,
                "教师或治理角色手动校准掌握度", null, null, 3, 1.0, false, true);
    }

    public static LearnerStateTransitionContext observation(String runId, String evidenceSource,
                                                              String assessmentType,
                                                              String evidenceText,
                                                              AssessmentObservation observation) {
        AssessmentObservation value = observation == null
                ? AssessmentObservation.legacy(false, 0.0) : observation;
        return new LearnerStateTransitionContext(runId, evidenceSource, assessmentType, evidenceText,
                null, null, value.difficultyLevel(), value.effectiveEvidenceWeight(),
                value.hintUsed(), value.independent());
    }

    public static LearnerStateTransitionContext code(String runId, String evidenceText,
                                                       String diagnosticCategory,
                                                       double passRate) {
        return new LearnerStateTransitionContext(runId, "CODE_EVALUATION", "FORMATIVE",
                evidenceText, diagnosticCategory, passRate, 2, 0.5, false, false);
    }

    public String effectiveEvidenceSource() {
        return evidenceSource == null ? "FORMATIVE_ASSESSMENT" : evidenceSource;
    }

    public String effectiveAssessmentType() {
        return assessmentType == null ? "FORMATIVE" : assessmentType;
    }

    private static String optional(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private static double bounded(double value) {
        if (!Double.isFinite(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
    }
}
