package org.mingharness.context.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateMemoryRequest(
        @NotBlank(message = "记忆类型不能为空") @Size(max = 64, message = "记忆类型不能超过 64 个字符") String memoryType,
        @NotBlank(message = "记忆内容不能为空") @Size(max = 20_000, message = "记忆内容不能超过 20000 个字符") String content,
        @Size(max = 64, message = "来源 Run ID 不能超过 64 个字符") String sourceRunId,
        Instant expiresAt
) {
}
