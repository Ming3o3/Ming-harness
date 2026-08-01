package org.mingharness.conversation.api;

import org.mingharness.conversation.ConversationMessageRole;
import org.mingharness.conversation.ConversationMessageStatus;

import java.time.Instant;
import java.util.List;

public record ConversationMessageView(
        String id,
        String runId,
        ConversationMessageRole role,
        ConversationMessageStatus status,
        int sequence,
        String content,
        List<ConversationAttachmentView> attachments,
        Instant createdAt,
        Instant updatedAt
) {
}
