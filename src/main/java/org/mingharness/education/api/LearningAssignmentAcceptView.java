package org.mingharness.education.api;

/** 接受课程作业后返回新绑定的学习画像和学习目标。 */
public record LearningAssignmentAcceptView(
        LearningAssignmentView assignment,
        String learnerProfileId,
        String learningGoalId
) {
}
