package org.mingharness.education;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 在 Run 创建时冻结的教育执行配置。
 *
 * <p>配置快照和学习者掌握度摘要随 Run 一起执行，避免运行过程中用户切换课程或画像
 * 导致同一个实验样本使用不同上下文。</p>
 */
public record EducationRunConfiguration(
        boolean enabled,
        String learnerProfileId,
        String learningGoalId,
        String learningAssignmentId,
        String learningAssignmentTitle,
        String learningAssignmentInstructions,
        String learningAssignmentTeacherReviewNote,
        String reviewPlanId,
        String learningGoalTitle,
        double learningGoalBaselineMastery,
        double learningGoalTargetMastery,
        String subject,
        String gradeLevel,
        String curriculumVersion,
        String conceptKey,
        Integer minDifficulty,
        Integer maxDifficulty,
        String pedagogicalMode,
        String learnerStateSummary
) {

    public static EducationRunConfiguration disabled() {
        return new EducationRunConfiguration(false, null, null, null, null, null, null, null, null, 0.0, 0.0,
                null, null, null, null, null, null, "AUTO", "");
    }

    /** 兼容未绑定教师返工说明的既有完整快照构造方式。 */
    public EducationRunConfiguration(boolean enabled, String learnerProfileId, String learningGoalId,
                                     String learningAssignmentId, String learningAssignmentTitle,
                                     String learningAssignmentInstructions, String reviewPlanId,
                                     String learningGoalTitle, double learningGoalBaselineMastery,
                                     double learningGoalTargetMastery, String subject, String gradeLevel,
                                     String curriculumVersion, String conceptKey, Integer minDifficulty,
                                     Integer maxDifficulty, String pedagogicalMode,
                                     String learnerStateSummary) {
        this(enabled, learnerProfileId, learningGoalId, learningAssignmentId, learningAssignmentTitle,
                learningAssignmentInstructions, null, reviewPlanId, learningGoalTitle,
                learningGoalBaselineMastery, learningGoalTargetMastery, subject, gradeLevel,
                curriculumVersion, conceptKey, minDifficulty, maxDifficulty, pedagogicalMode,
                learnerStateSummary);
    }

    /** 兼容未绑定保持度复习计划的既有调用方。 */
    public EducationRunConfiguration(boolean enabled, String learnerProfileId, String learningGoalId,
                                     String learningGoalTitle, double learningGoalBaselineMastery,
                                     double learningGoalTargetMastery, String subject, String gradeLevel,
                                     String curriculumVersion, String conceptKey, Integer minDifficulty,
                                     Integer maxDifficulty, String pedagogicalMode,
                                     String learnerStateSummary) {
        this(enabled, learnerProfileId, learningGoalId, null, null, null, null, learningGoalTitle,
                learningGoalBaselineMastery, learningGoalTargetMastery, subject, gradeLevel,
                curriculumVersion, conceptKey, minDifficulty, maxDifficulty, pedagogicalMode,
                learnerStateSummary);
    }

    /** 兼容旧版携带保持度复习计划的快照构造方式。 */
    public EducationRunConfiguration(boolean enabled, String learnerProfileId, String learningGoalId,
                                     String reviewPlanId, String learningGoalTitle,
                                     double learningGoalBaselineMastery, double learningGoalTargetMastery,
                                     String subject, String gradeLevel, String curriculumVersion,
                                     String conceptKey, Integer minDifficulty, Integer maxDifficulty,
                                     String pedagogicalMode, String learnerStateSummary) {
        this(enabled, learnerProfileId, learningGoalId, null, null, null, reviewPlanId, learningGoalTitle,
                learningGoalBaselineMastery, learningGoalTargetMastery, subject, gradeLevel,
                curriculumVersion, conceptKey, minDifficulty, maxDifficulty, pedagogicalMode,
                learnerStateSummary);
    }

    public EducationRetrievalFilter retrievalFilter() {
        return enabled ? new EducationRetrievalFilter(subject, gradeLevel, curriculumVersion,
                conceptKey, minDifficulty, maxDifficulty, masteryScores()) : null;
    }

    /** 从随 Run 冻结的摘要恢复轻量掌握度快照，保证重启 Worker 后重排结果稳定。 */
    public Map<String, Double> masteryScores() {
        if (learnerStateSummary == null || learnerStateSummary.isBlank()
                || learnerStateSummary.contains("暂无掌握度记录")) {
            return Map.of();
        }
        Map<String, Double> result = new LinkedHashMap<>();
        for (String item : learnerStateSummary.split(",")) {
            int separator = item.lastIndexOf('=');
            if (separator <= 0 || separator >= item.length() - 1) continue;
            String concept = item.substring(0, separator).trim();
            try {
                double score = Double.parseDouble(item.substring(separator + 1).trim());
                if (!concept.isBlank() && Double.isFinite(score)) {
                    result.put(concept, Math.max(0.0, Math.min(1.0, score)));
                }
            } catch (NumberFormatException ignored) {
                // 摘要是诊断信息；单个损坏项不应让整个教育 Run 无法执行。
            }
        }
        return Map.copyOf(result);
    }

    public String promptSummary() {
        if (!enabled) return "";
        StringBuilder summary = new StringBuilder();
        summary.append("学科=").append(subject)
                .append("；年级=").append(gradeLevel)
                .append("；课程版本=").append(curriculumVersion)
                .append("；教学策略=").append(pedagogicalMode);
        if (learningGoalId != null && !learningGoalId.isBlank()) {
            summary.append("；学习目标=").append(learningGoalTitle)
                    .append("；目标掌握度=").append(String.format(java.util.Locale.ROOT, "%.2f",
                            learningGoalTargetMastery));
        }
        if (learningAssignmentId != null && !learningAssignmentId.isBlank()) {
            if (learningAssignmentTitle != null && !learningAssignmentTitle.isBlank()) {
                summary.append("；课程作业=").append(learningAssignmentTitle);
            }
            if (learningAssignmentInstructions != null && !learningAssignmentInstructions.isBlank()) {
                summary.append("；作业要求=").append(learningAssignmentInstructions);
            }
            if (learningAssignmentTeacherReviewNote != null
                    && !learningAssignmentTeacherReviewNote.isBlank()) {
                summary.append("；教师返工说明=").append(learningAssignmentTeacherReviewNote);
            }
        }
        if (reviewPlanId != null && !reviewPlanId.isBlank()) {
            summary.append("；保持度复习=已绑定");
        }
        if (conceptKey != null && !conceptKey.isBlank()) {
            summary.append("；目标知识点=").append(conceptKey);
        }
        if (learnerStateSummary != null && !learnerStateSummary.isBlank()) {
            summary.append("；学习者状态=").append(learnerStateSummary);
        }
        return summary.toString();
    }
}
