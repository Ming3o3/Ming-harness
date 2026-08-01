package org.mingharness.conversation.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendConversationMessageRequest(
        @NotBlank(message = "消息内容不能为空")
        @Size(max = 10000, message = "消息内容不能超过 10000 个字符") String content,
        String modelName,
        @Min(value = 1, message = "Agent 最大轮数必须至少为 1")
        @Max(value = 20, message = "Agent 最大轮数不能超过 20") Integer maxTurns
) {

    public int effectiveMaxTurns() {
        return maxTurns == null ? 8 : Math.max(1, Math.min(20, maxTurns));
    }
}
