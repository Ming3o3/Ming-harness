package org.mingharness.tool;

/**
 * 工具声明外部依赖出现了可安全重试的瞬态错误。
 * 只有只读工具抛出此异常时，Runtime 才会依据 maxAttempts 自动重试。
 */
public class RetryableToolException extends RuntimeException {

    public RetryableToolException(String message) {
        super(message);
    }

    public RetryableToolException(String message, Throwable cause) {
        super(message, cause);
    }
}
