package org.mingharness.messaging;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.mingharness.runtime.domain.Run;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/** 在当前数据库事务中创建 RabbitMQ 执行事件。 */
@Service
public class OutboxService {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public OutboxEvent enqueue(Run run, String command) {
        String eventId = UUID.randomUUID().toString();
        try {
            String payload = objectMapper.writeValueAsString(new RunExecutionMessage(
                    eventId, run.getId(), run.getTenantId(), run.getTraceId(), command, Instant.now()));
            return repository.save(new OutboxEvent(
                    run.getId(), run.getTenantId(), run.getTraceId(), command, payload));
        } catch (JacksonException exception) {
            throw new IllegalStateException("无法序列化 Run 执行消息", exception);
        }
    }
}
