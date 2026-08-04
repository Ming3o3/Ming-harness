package org.mingharness.model;

/** 模型供应商调用失败，保留稳定错误语义供 Runtime、审计和前端使用。 */
public class ModelGatewayException extends RuntimeException {

    private final String provider;
    private final ModelErrorCode code;
    private final boolean retryable;
    private final Integer httpStatus;
    private final Long retryAfterMs;
    private final String requestId;

    public ModelGatewayException(String provider, boolean retryable, String message) {
        this(provider, ModelErrorCode.UNKNOWN, retryable, message, null, null, null, null);
    }

    public ModelGatewayException(String provider, boolean retryable, String message, Throwable cause) {
        this(provider, ModelErrorCode.UNKNOWN, retryable, message, null, null, null, cause);
    }

    public ModelGatewayException(String provider, ModelErrorCode code, boolean retryable, String message) {
        this(provider, code, retryable, message, null, null, null, null);
    }

    public ModelGatewayException(String provider, ModelErrorCode code, boolean retryable,
                                 String message, Throwable cause) {
        this(provider, code, retryable, message, null, null, null, cause);
    }

    public ModelGatewayException(String provider, ModelErrorCode code, boolean retryable,
                                 String message, Integer httpStatus, Long retryAfterMs,
                                 String requestId, Throwable cause) {
        super(message, cause);
        this.provider = provider;
        this.code = code == null ? ModelErrorCode.UNKNOWN : code;
        this.retryable = retryable;
        this.httpStatus = httpStatus;
        this.retryAfterMs = retryAfterMs;
        this.requestId = requestId;
    }

    public String provider() {
        return provider;
    }

    public ModelErrorCode code() {
        return code;
    }

    public boolean retryable() {
        return retryable;
    }

    public Integer httpStatus() {
        return httpStatus;
    }

    public Long retryAfterMs() {
        return retryAfterMs;
    }

    public String requestId() {
        return requestId;
    }
}
