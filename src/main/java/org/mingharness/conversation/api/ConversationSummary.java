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
        String activeRunId
) {
}
