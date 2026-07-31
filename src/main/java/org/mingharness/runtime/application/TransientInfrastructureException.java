package org.mingharness.runtime.application;

/** 表示应交给消息队列重试的临时基础设施故障，不会把当前 Run 直接落为 FAILED。 */
public class TransientInfrastructureException extends RuntimeException {

    public TransientInfrastructureException(String message) {
        super(message);
    }

    public TransientInfrastructureException(String message, Throwable cause) {
        super(message, cause);
    }
}
