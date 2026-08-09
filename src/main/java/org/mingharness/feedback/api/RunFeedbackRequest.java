package org.mingharness.feedback.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RunFeedbackRequest(
        @NotBlank(message = "反馈类型不能为空") @Size(max = 16, message = "反馈类型过长") String rating,
        @Size(max = 64, message = "反馈原因过长") String reasonCode,
        @Size(max = 2000, message = "反馈备注不能超过 2000 个字符") String note,
        @Size(max = 128, message = "消息 ID 长度不能超过 128 个字符") String messageId
) {
}
