package org.mingharness.messaging;

import org.mingharness.observability.HarnessMetrics;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;

/** 记录死信指标后继续执行 Rabbit 默认的拒绝且不重新入队语义。 */
final class RabbitDeadLetterRecoverer implements MessageRecoverer {

    private final HarnessMetrics metrics;
    private final MessageRecoverer delegate;

    RabbitDeadLetterRecoverer(HarnessMetrics metrics) {
        this(metrics, new RejectAndDontRequeueRecoverer());
    }

    RabbitDeadLetterRecoverer(HarnessMetrics metrics, MessageRecoverer delegate) {
        this.metrics = metrics;
        this.delegate = delegate;
    }

    @Override
    public void recover(Message message, Throwable cause) {
        metrics.rabbitDeadLetter();
        delegate.recover(message, cause);
    }
}
