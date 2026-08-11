package org.mingharness.education.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 创建教育 Agent Run 时的课程约束和学习者画像选择。
 * <p>为空或 enabled=false 时，Run 继续走通用 Agent 路径。</p>
 */
public record EducationRunOptions(
        Boolean enabled,
        @Size(max = 128, message = "学习者画像 ID 不能超过 128 个字符") String learnerProfileId,
        @Size(max = 128, message = "学习目标 ID 不能超过 128 个字符") String learningGoalId,
        @Size(max = 128, message = "课程作业 ID 不能超过 128 个字符") String learningAssignmentId,
        @Size(max = 255, message = "复习计划 ID 不能超过 255 个字符") String reviewPlanId,
        @Size(max = 128, message = "学科长度不能超过 128 个字符") String subject,
        @Size(max = 128, message = "年级长度不能超过 128 个字符") String gradeLevel,
        @Size(max = 128, message = "课程版本长度不能超过 128 个字符") String curriculumVersion,
        @Size(max = 255, message = "目标知识点长度不能超过 255 个字符") String conceptKey,
        @Min(value = 1, message = "最低难度必须在 1 到 5 之间")
        @Max(value = 5, message = "最低难度必须在 1 到 5 之间") Integer minDifficulty,
        @Min(value = 1, message = "最高难度必须在 1 到 5 之间")
        @Max(value = 5, message = "最高难度必须在 1 到 5 之间") Integer maxDifficulty,
        @Size(max = 64, message = "教学策略长度不能超过 64 个字符") String pedagogicalMode,
        @Size(max = 128, message = "课程实例 ID 不能超过 128 个字符") String courseId
) {

    /** 兼容增加课程实例绑定前的完整教育 Run 请求。 */
    public EducationRunOptions(Boolean enabled, String learnerProfileId, String learningGoalId,
                               String learningAssignmentId, String reviewPlanId, String subject,
                               String gradeLevel, String curriculumVersion, String conceptKey,
                               Integer minDifficulty, Integer maxDifficulty, String pedagogicalMode) {
        this(enabled, learnerProfileId, learningGoalId, learningAssignmentId, reviewPlanId,
                subject, gradeLevel, curriculumVersion, conceptKey, minDifficulty,
                maxDifficulty, pedagogicalMode, null);
    }

    /** 兼容既有调用方；未绑定结构化学习目标。 */
    public EducationRunOptions(Boolean enabled, String learnerProfileId, String subject,
                               String gradeLevel, String curriculumVersion, String conceptKey,
                               Integer minDifficulty, Integer maxDifficulty, String pedagogicalMode) {
        this(enabled, learnerProfileId, null, null, null, subject, gradeLevel, curriculumVersion, conceptKey,
                minDifficulty, maxDifficulty, pedagogicalMode, null);
    }

    /** 兼容已绑定学习目标但不使用保持度复习的旧调用方。 */
    public EducationRunOptions(Boolean enabled, String learnerProfileId, String learningGoalId,
                               String subject, String gradeLevel, String curriculumVersion,
                               String conceptKey, Integer minDifficulty, Integer maxDifficulty,
                               String pedagogicalMode) {
        this(enabled, learnerProfileId, learningGoalId, null, null, subject, gradeLevel,
                curriculumVersion, conceptKey, minDifficulty, maxDifficulty, pedagogicalMode, null);
    }

    /** 兼容已绑定学习目标和保持度复习计划的旧调用方。 */
    public EducationRunOptions(Boolean enabled, String learnerProfileId, String learningGoalId,
                               String reviewPlanId, String subject, String gradeLevel,
                               String curriculumVersion, String conceptKey, Integer minDifficulty,
                               Integer maxDifficulty, String pedagogicalMode) {
        this(enabled, learnerProfileId, learningGoalId, null, reviewPlanId, subject, gradeLevel,
                curriculumVersion, conceptKey, minDifficulty, maxDifficulty, pedagogicalMode, null);
    }

    public boolean isEnabled() {
        return Boolean.TRUE.equals(enabled);
    }

    public String effectivePedagogicalMode() {
        return pedagogicalMode == null || pedagogicalMode.isBlank()
                ? "AUTO" : pedagogicalMode.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
