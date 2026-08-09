package org.mingharness.conversation.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;
public record SendConversationMessageRequest(
        @NotBlank(message = "消息内容不能为空")
        @Size(max = 10000, message = "消息内容不能超过 10000 个字符") String content,
        String modelName,
        @Min(value = 1, message = "Agent 最大轮数必须至少为 1")
        @Max(value = 1000, message = "Agent 最大轮数不能超过 1000") Integer maxTurns,
        @Size(max = 8, message = "每条消息最多可携带 8 个附件")
        List<@Size(max = 128, message = "附件 ID 长度不能超过 128 个字符") String> attachmentIds
) {

    /** 兼容尚未上传聊天附件的调用方。 */
    public SendConversationMessageRequest(String content, String modelName, Integer maxTurns) {
        this(content, modelName, maxTurns, List.of());
    }

    public int effectiveMaxTurns() {
        return maxTurns == null ? 1_000 : Math.max(1, Math.min(1_000, maxTurns));
    }

    /** 附件 ID 去重并保留调用方顺序，最多允许一轮携带 8 个文本文件。 */
    public List<String> effectiveAttachmentIds() {
        if (attachmentIds == null || attachmentIds.isEmpty()) return List.of();
        List<String> result = new ArrayList<>();
        for (String value : attachmentIds) {
            if (value == null || value.isBlank()) continue;
            String normalized = value.trim();
            if (!result.contains(normalized)) result.add(normalized);
        }
        return List.copyOf(result);
    }
}
