package org.mingharness.education;

/** 保持度复习计划的生命周期。计划在目标完成后持续存在，直到用户主动归档。 */
public enum LearningReviewPlanStatus {
    ACTIVE,
    PAUSED,
    ARCHIVED
}
