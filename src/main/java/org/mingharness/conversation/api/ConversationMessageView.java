package org.mingharness.conversation.api;

import org.mingharness.conversation.ConversationMessageRole;
import org.mingharness.conversation.ConversationMessageStatus;

import java.time.Instant;

public record ConversationMessageView(
        String id,
        String runId,
        ConversationMessageRole role,
        ConversationMessageStatus status,
        int sequence,
        String content,
        Instant createdAt,
        Instant updatedAt
) {
}
