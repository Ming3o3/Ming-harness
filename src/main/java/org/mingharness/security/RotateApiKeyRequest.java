package org.mingharness.security;

import java.time.Instant;

/** API Key 轮换请求；权限、组织和用户沿用旧凭证，避免轮换时扩大授权范围。 */
public record RotateApiKeyRequest(Instant expiresAt) {
}
