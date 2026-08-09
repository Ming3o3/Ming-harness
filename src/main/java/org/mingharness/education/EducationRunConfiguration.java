package org.mingharness.education;

/**
 * 在 Run 创建时冻结的教育执行配置。
 *
 * <p>配置快照和学习者掌握度摘要随 Run 一起执行，避免运行过程中用户切换课程或画像
 * 导致同一个实验样本使用不同上下文。</p>
 */
public record EducationRunConfiguration(
        boolean enabled,
        String learnerProfileId,
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
        return new EducationRunConfiguration(false, null, null, null, null, null,
                null, null, "AUTO", "");
    }

    public EducationRetrievalFilter retrievalFilter() {
        return enabled ? new EducationRetrievalFilter(subject, gradeLevel, curriculumVersion,
                conceptKey, minDifficulty, maxDifficulty) : null;
    }

    public String promptSummary() {
        if (!enabled) return "";
        StringBuilder summary = new StringBuilder();
        summary.append("学科=").append(subject)
                .append("；年级=").append(gradeLevel)
                .append("；课程版本=").append(curriculumVersion)
                .append("；教学策略=").append(pedagogicalMode);
        if (conceptKey != null && !conceptKey.isBlank()) {
            summary.append("；目标知识点=").append(conceptKey);
        }
        if (learnerStateSummary != null && !learnerStateSummary.isBlank()) {
            summary.append("；学习者状态=").append(learnerStateSummary);
        }
        return summary.toString();
    }
}
