package org.mingharness.runtime.application;

import org.junit.jupiter.api.Test;
import org.mingharness.runtime.api.RunDetail;
import org.mingharness.runtime.api.RunSummary;
import org.mingharness.runtime.domain.RunStatus;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextClosedEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 验证应用关闭时长连接会主动完成，避免优雅停机无限等待。 */
class RunEventStreamServiceShutdownTests {

    @Test
    void shouldCompleteAllSubscriptionsWhenContextCloses() {
        RunService runService = mock(RunService.class);
        RunEventStreamService service = new RunEventStreamService(runService, 15_000, 2);
        when(runService.getDetail("run-1", "tenant-1")).thenReturn(runningDetail());

        service.subscribe("run-1", "tenant-1");

        assertEquals(1, service.activeSubscriberCount());
        service.onContextClosed(new ContextClosedEvent(mock(ApplicationContext.class)));

        assertEquals(0, service.activeSubscriberCount());
    }

    private RunDetail runningDetail() {
        Instant now = Instant.now();
        RunSummary summary = new RunSummary(
                "run-1", "tenant-1", "user-1", "测试任务", "demo-model", "prompt-v1", "policy-v1",
                "输入", null, null, RunStatus.RUNNING, BigDecimal.ONE, now, now, 0, "key-1");
        return new RunDetail(summary, List.of());
    }
}
