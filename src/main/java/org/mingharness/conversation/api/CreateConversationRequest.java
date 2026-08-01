package org.mingharness.conversation.api;

import jakarta.validation.constraints.Size;

public record CreateConversationRequest(
        @Size(max = 255, message = "会话标题长度不能超过 255 个字符") String title
) {
}
