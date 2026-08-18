package org.mingharness.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

/** 管理员为租户内某个用户设置直接权限；空集合表示清空直接授权。 */
public record AssignUserPermissionsRequest(
        @NotBlank(message = "组织不能为空") @Size(max = 128, message = "组织长度不能超过 128") String tenantId,
        @NotBlank(message = "用户不能为空") @Size(max = 128, message = "用户长度不能超过 128") String userId,
        Set<String> permissions
) {
}
