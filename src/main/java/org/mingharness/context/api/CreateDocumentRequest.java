package org.mingharness.context.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateDocumentRequest(
        @NotBlank(message = "文档标题不能为空") @Size(max = 200, message = "文档标题不能超过 200 个字符") String title,
        @NotBlank(message = "文档内容不能为空") @Size(max = 100_000, message = "文档内容不能超过 100000 个字符") String content,
        @Size(max = 32, message = "敏感级别长度不能超过 32 个字符") String sensitivity,
        @Size(max = 2000, message = "文档授权用户列表过长") String allowedUsers
) {
}
