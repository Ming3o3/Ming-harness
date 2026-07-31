package org.mingharness.messaging;

import java.time.Instant;

/** RabbitMQ 中传递的 Run 执行命令，正文只包含定位和审计所需字段。 */
public record RunExecutionMessage(
        String eventId,
        String runId,
        String tenantId,
        String traceId,
        String command,
        Instant requestedAt
) {
}
