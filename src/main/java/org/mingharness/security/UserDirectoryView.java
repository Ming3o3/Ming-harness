package org.mingharness.security;

/** 管理员用户选择器使用的安全用户摘要，不包含凭证或业务数据。 */
public record UserDirectoryView(
        String tenantId,
        String userId
) {
}
