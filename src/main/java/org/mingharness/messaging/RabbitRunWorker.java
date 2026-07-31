package org.mingharness.messaging;

import org.mingharness.runtime.application.RunService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** RabbitMQ Run 执行消费者，业务状态和幂等判断交给 RunService。 */
@Component
@ConditionalOnProperty(prefix = "harness.messaging", name = "enabled", havingValue = "true")
public class RabbitRunWorker {

    private final RunService runService;

    public RabbitRunWorker(RunService runService) {
        this.runService = runService;
    }

    @RabbitListener(queues = "${harness.messaging.queue}", containerFactory = "rabbitListenerContainerFactory")
    public void consume(RunExecutionMessage message) {
        runService.executeFromWorker(message);
    }
}
