package org.mingharness.messaging;

import org.mingharness.config.MessagingProperties;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.mingharness.observability.HarnessMetrics;
import org.springframework.core.retry.RetryPolicy;

import java.time.Duration;

/** RabbitMQ 执行交换机、主队列和死信队列声明。 */
@Configuration
@ConditionalOnProperty(prefix = "harness.messaging", name = "enabled", havingValue = "true")
public class RabbitMessagingConfig {

    @Bean
    public DirectExchange harnessRuntimeExchange(MessagingProperties properties) {
        return new DirectExchange(properties.exchange(), true, false);
    }

    @Bean
    public DirectExchange harnessRuntimeDeadLetterExchange(MessagingProperties properties) {
        return new DirectExchange(properties.deadLetterExchange(), true, false);
    }

    @Bean
    public Queue harnessRunExecuteQueue(MessagingProperties properties) {
        return QueueBuilder.durable(properties.queue())
                .deadLetterExchange(properties.deadLetterExchange())
                .deadLetterRoutingKey(properties.deadLetterQueue())
                .build();
    }

    @Bean
    public Queue harnessRunExecuteDeadLetterQueue(MessagingProperties properties) {
        return QueueBuilder.durable(properties.deadLetterQueue()).build();
    }

    @Bean
    public Binding harnessRunExecuteBinding(Queue harnessRunExecuteQueue,
                                            DirectExchange harnessRuntimeExchange,
                                            MessagingProperties properties) {
        return BindingBuilder.bind(harnessRunExecuteQueue)
                .to(harnessRuntimeExchange)
                .with(properties.routingKey());
    }

    @Bean
    public Binding harnessRunExecuteDeadLetterBinding(Queue harnessRunExecuteDeadLetterQueue,
                                                      DirectExchange harnessRuntimeDeadLetterExchange,
                                                      MessagingProperties properties) {
        return BindingBuilder.bind(harnessRunExecuteDeadLetterQueue)
                .to(harnessRuntimeDeadLetterExchange)
                .with(properties.deadLetterQueue());
    }

    @Bean
    public MessageConverter harnessMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter,
            MessagingProperties properties,
            HarnessMetrics metrics) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(messageConverter);
        RetryPolicy retryPolicy = RetryPolicy.builder()
                .maxRetries(Math.max(0, properties.maxAttempts() - 1L))
                .delay(Duration.ofMillis(500))
                .multiplier(2.0)
                .maxDelay(Duration.ofSeconds(5))
                .build();
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .retryPolicy(new RabbitRetryMetricsPolicy(retryPolicy, metrics))
                .recoverer(new RabbitDeadLetterRecoverer(metrics))
                .build());
        return factory;
    }
}
