package org.mingharness.conversation.api;

import jakarta.validation.constraints.Size;

public record CreateConversationRequest(
        @Size(max = 255, message = "会话标题长度不能超过 255 个字符") String title,
        @Size(max = 128, message = "工作区 ID 长度不能超过 128 个字符") String workspaceId
) {

    /** 兼容未选择桌面工作区的旧客户端。 */
    public CreateConversationRequest(String title) {
        this(title, null);
    }
}
