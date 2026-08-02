package org.mingharness.conversation.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 用户主动维护会话标题；标题不参与模型输入或工作区路径解析。 */
public record UpdateConversationRequest(
        @NotBlank(message = "会话标题不能为空")
        @Size(max = 255, message = "会话标题长度不能超过 255 个字符") String title
) {
}
