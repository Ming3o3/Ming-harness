package org.mingharness.conversation.api;

import java.time.Instant;

public record ConversationSummary(
        String id,
        String tenantId,
        String userId,
        String title,
        String workspaceId,
        Instant createdAt,
        Instant updatedAt,
        int messageCount,
        String lastMessagePreview,
        String activeRunId,
        boolean educationMode,
        String educationCourseCode,
        String educationCourseTitle,
        String educationSubject,
        String educationGradeLevel,
        String educationCurriculumVersion,
        String educationLearningGoalTitle,
        String educationConceptKey,
        String educationLearningAssignmentTitle,
        String educationLearningAssignmentId
) {

    /** 保持服务层测试和旧扩展调用方可用；教育上下文在未关联教育 Run 时为空。 */
    public ConversationSummary(String id, String tenantId, String userId, String title,
                               String workspaceId, Instant createdAt, Instant updatedAt,
                               int messageCount, String lastMessagePreview, String activeRunId) {
        this(id, tenantId, userId, title, workspaceId, createdAt, updatedAt, messageCount,
                lastMessagePreview, activeRunId, false, null, null, null, null, null,
                null, null, null, null);
    }
}
