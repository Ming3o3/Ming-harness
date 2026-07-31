package org.mingharness.model;

/** 模型供应商调用失败，retryable 用于区分临时基础设施故障和业务/契约错误。 */
public class ModelGatewayException extends RuntimeException {

    private final String provider;
    private final boolean retryable;

    public ModelGatewayException(String provider, boolean retryable, String message) {
        super(message);
        this.provider = provider;
        this.retryable = retryable;
    }

    public ModelGatewayException(String provider, boolean retryable, String message, Throwable cause) {
        super(message, cause);
        this.provider = provider;
        this.retryable = retryable;
    }

    public String provider() {
        return provider;
    }

    public boolean retryable() {
        return retryable;
    }
}
