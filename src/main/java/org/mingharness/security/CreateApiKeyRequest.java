package org.mingharness.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Set;

/** 创建数据库 API Key 的请求；密钥明文由服务端生成，调用方不能自定义。 */
public record CreateApiKeyRequest(
        @NotBlank(message = "组织不能为空") @Size(max = 128, message = "组织长度不能超过 128") String tenantId,
        @NotBlank(message = "用户不能为空") @Size(max = 128, message = "用户长度不能超过 128") String userId,
        Set<String> permissions,
        Instant expiresAt
) {
}
