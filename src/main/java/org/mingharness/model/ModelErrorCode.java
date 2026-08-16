package org.mingharness.model;

/**
 * 模型后端的稳定错误语义。供应商的 HTTP 状态和错误文本可能不同，Runtime 不应依赖它们做分支判断。
 */
public enum ModelErrorCode {
    CONFIGURATION_INVALID,
    INVALID_REQUEST,
    AUTHENTICATION_FAILED,
    PERMISSION_DENIED,
    MODEL_NOT_FOUND,
    RATE_LIMITED,
    TIMEOUT,
    PROVIDER_UNAVAILABLE,
    CIRCUIT_OPEN,
    BAD_RESPONSE,
    INVALID_TOOL_CALL,
    RESPONSE_TOO_LARGE,
    PARTIAL_RESPONSE,
    FALLBACK_FAILED,
    RETRY_INTERRUPTED,
    UNKNOWN
}
