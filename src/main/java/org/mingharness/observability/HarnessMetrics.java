package org.mingharness.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/** Harness 运行指标，避免把租户 ID 等高基数字段作为标签。 */
@Component
public class HarnessMetrics {

    private final Counter runsCreated;
    private final Counter runsSucceeded;
    private final Counter runsFailed;
    private final Counter runsTimedOut;
    private final Counter toolFailures;
    private final Counter workerClaims;
    private final Counter workerFailures;
    private final Counter outboxPublished;
    private final Counter outboxFailures;
    private final Timer workerDuration;
    private final AtomicInteger pendingOutbox = new AtomicInteger();

    public HarnessMetrics(MeterRegistry registry) {
        runsCreated = Counter.builder("harness.runs.created").description("创建的 Run 数量").register(registry);
        runsSucceeded = Counter.builder("harness.runs.succeeded").description("成功的 Run 数量").register(registry);
        runsFailed = Counter.builder("harness.runs.failed").description("失败的 Run 数量").register(registry);
        runsTimedOut = Counter.builder("harness.runs.timed_out").description("超时的 Run 数量").register(registry);
        toolFailures = Counter.builder("harness.tools.failed").description("工具失败数量").register(registry);
        workerClaims = Counter.builder("harness.worker.claims").description("Worker 获取租约数量").register(registry);
        workerFailures = Counter.builder("harness.worker.failed").description("Worker 消费失败数量").register(registry);
        outboxPublished = Counter.builder("harness.outbox.published").description("发布成功的 Outbox 数量").register(registry);
        outboxFailures = Counter.builder("harness.outbox.failed").description("发布失败的 Outbox 数量").register(registry);
        workerDuration = Timer.builder("harness.worker.duration").description("Worker 执行耗时").register(registry);
        registry.gauge("harness.outbox.pending", pendingOutbox);
    }

    public void runCreated() { runsCreated.increment(); }
    public void runSucceeded() { runsSucceeded.increment(); }
    public void runFailed() { runsFailed.increment(); }
    public void runTimedOut() { runsTimedOut.increment(); }
    public void toolFailed() { toolFailures.increment(); }
    public void workerClaimed() { workerClaims.increment(); }
    public void workerFailed() { workerFailures.increment(); }
    public void outboxPublished() { outboxPublished.increment(); }
    public void outboxFailed() { outboxFailures.increment(); }
    public void pendingOutbox(int count) { pendingOutbox.set(Math.max(0, count)); }

    public <T> T recordWorkerDuration(Supplier<T> action) {
        return workerDuration.record(action);
    }

    public void recordWorkerDuration(Runnable action) {
        workerDuration.record(action);
    }
}
