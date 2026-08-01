package org.mingharness.conversation.api;

import java.util.List;

public record ConversationDetail(
        ConversationSummary conversation,
        List<ConversationMessageView> messages
) {
}
