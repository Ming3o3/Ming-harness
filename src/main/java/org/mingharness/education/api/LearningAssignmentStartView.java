package org.mingharness.education.api;

import org.mingharness.conversation.api.ConversationDetail;

/** 接受并启动课程作业后返回作业状态和绑定的学习会话。 */
public record LearningAssignmentStartView(
        LearningAssignmentView assignment,
        String learnerProfileId,
        String learningGoalId,
        ConversationDetail conversation
) {
}
