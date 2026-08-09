package org.mingharness.education.api;

import org.mingharness.conversation.api.ConversationDetail;

/** 启动学习任务后同时返回任务状态和绑定的学习会话。 */
public record LearningTaskStartView(
        LearningTaskView task,
        ConversationDetail conversation
) {
}
