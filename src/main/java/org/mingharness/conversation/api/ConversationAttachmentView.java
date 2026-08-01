package org.mingharness.conversation.api;

import java.time.Instant;

/** 聊天记录中可展示的附件元数据；路径始终是受控工作区内的相对路径。 */
public record ConversationAttachmentView(
        String id,
        String originalName,
        String workspacePath,
        String mediaType,
        long sizeBytes,
        Instant createdAt
) {
}
