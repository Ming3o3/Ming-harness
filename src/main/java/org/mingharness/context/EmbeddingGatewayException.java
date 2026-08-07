package org.mingharness.context;

/** embedding 供应商失败，调用方可依据 retryable 决定是否延迟重试或降级到关键词检索。 */
public class EmbeddingGatewayException extends RuntimeException {

    private final boolean retryable;
    private final Integer httpStatus;

    public EmbeddingGatewayException(boolean retryable, String message) {
        this(retryable, message, null, null);
    }

    public EmbeddingGatewayException(boolean retryable, String message, Throwable cause) {
        this(retryable, message, null, cause);
    }

    public EmbeddingGatewayException(boolean retryable, String message, Integer httpStatus, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
        this.httpStatus = httpStatus;
    }

    public boolean retryable() { return retryable; }
    public Integer httpStatus() { return httpStatus; }
}
