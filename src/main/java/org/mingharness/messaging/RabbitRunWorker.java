package org.mingharness.messaging;

import org.mingharness.runtime.application.RunService;
import org.mingharness.observability.HarnessMetrics;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** RabbitMQ Run 执行消费者，业务状态和幂等判断交给 RunService。 */
@Component
@ConditionalOnProperty(prefix = "harness.messaging", name = "enabled", havingValue = "true")
public class RabbitRunWorker {

    private final RunService runService;
    private final HarnessMetrics metrics;

    public RabbitRunWorker(RunService runService, HarnessMetrics metrics) {
        this.runService = runService;
        this.metrics = metrics;
    }

    @RabbitListener(queues = "${harness.messaging.queue}", containerFactory = "rabbitListenerContainerFactory")
    public void consume(RunExecutionMessage message) {
        metrics.workerStarted();
        try {
            runService.executeFromWorker(message);
        } finally {
            metrics.workerFinished();
        }
    }
}
