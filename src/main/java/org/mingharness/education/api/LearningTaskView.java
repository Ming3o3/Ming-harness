package org.mingharness.education.api;

import org.mingharness.education.LearningTask;

import java.time.Instant;

/** 学习任务的安全投影，不暴露内部数据库实现细节。 */
public record LearningTaskView(
        String id,
        String taskType,
        String status,
        String learningGoalId,
        String reviewPlanId,
        int reviewSequence,
        String title,
        String prompt,
        Instant scheduledAt,
        String conversationId,
        String runId,
        Instant startedAt,
        Instant completedAt,
        Boolean outcomeCorrect,
        int deferCount,
        Instant lastDeferredAt,
        Instant createdAt,
        Instant updatedAt
) {

    public static LearningTaskView from(LearningTask task) {
        return new LearningTaskView(task.getId(), task.getTaskType().name(), task.getStatus().name(),
                task.getLearningGoalId(), task.getReviewPlanId(), task.getReviewSequence(), task.getTitle(),
                task.getPrompt(), task.getScheduledAt(), task.getConversationId(), task.getRunId(),
                task.getStartedAt(), task.getCompletedAt(), task.getOutcomeCorrect(), task.getDeferCount(),
                task.getLastDeferredAt(), task.getCreatedAt(), task.getUpdatedAt());
    }
}
