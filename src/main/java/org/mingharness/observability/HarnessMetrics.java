package org.mingharness.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/** Harness 运行指标，避免把组织 ID 等高基数字段作为标签。 */
@Component
public class HarnessMetrics {

    private final Counter runsCreated;
    private final Counter runsSucceeded;
    private final Counter runsFailed;
    private final Counter runsTimedOut;
    private final Counter toolFailures;
    private final Counter workerClaims;
    private final Counter workerFailures;
    private final Counter workerInfrastructureFailures;
    private final Counter outboxPublished;
    private final Counter outboxFailures;
    private final Counter rabbitRetries;
    private final Counter rabbitDeadLetters;
    private final Counter rabbitBackpressure;
    private final Counter rabbitQueuePollFailures;
    private final Counter retentionDeleted;
    private final Counter modelRetries;
    private final Counter modelFallbacks;
    private final Counter modelFailures;
    private final Timer workerDuration;
    private final AtomicInteger pendingOutbox = new AtomicInteger();
    private final AtomicInteger rabbitQueueDepth = new AtomicInteger(-1);
    private final AtomicInteger rabbitQueueCapacity = new AtomicInteger(-1);
    private final AtomicInteger activeWorkers = new AtomicInteger();
    private final AtomicInteger workerConcurrency = new AtomicInteger();

    public HarnessMetrics(MeterRegistry registry) {
        runsCreated = Counter.builder("harness.runs.created").description("创建的 Run 数量").register(registry);
        runsSucceeded = Counter.builder("harness.runs.succeeded").description("成功的 Run 数量").register(registry);
        runsFailed = Counter.builder("harness.runs.failed").description("失败的 Run 数量").register(registry);
        runsTimedOut = Counter.builder("harness.runs.timed_out").description("超时的 Run 数量").register(registry);
        toolFailures = Counter.builder("harness.tools.failed").description("工具失败数量").register(registry);
        workerClaims = Counter.builder("harness.worker.claims").description("Worker 获取租约数量").register(registry);
        workerFailures = Counter.builder("harness.worker.failed").description("Worker 消费失败数量").register(registry);
        workerInfrastructureFailures = Counter.builder("harness.worker.infrastructure_failed")
                .description("Worker 临时基础设施故障数量").register(registry);
        outboxPublished = Counter.builder("harness.outbox.published").description("发布成功的 Outbox 数量").register(registry);
        outboxFailures = Counter.builder("harness.outbox.failed").description("发布失败的 Outbox 数量").register(registry);
        rabbitRetries = Counter.builder("harness.rabbit.retries").description("RabbitMQ 消费重试次数").register(registry);
        rabbitDeadLetters = Counter.builder("harness.rabbit.dead_letters").description("RabbitMQ 死信数量").register(registry);
        rabbitBackpressure = Counter.builder("harness.rabbit.backpressure")
                .description("因执行队列达到上限而暂停发布的次数").register(registry);
        rabbitQueuePollFailures = Counter.builder("harness.rabbit.queue.poll_failures")
                .description("读取 RabbitMQ 队列深度失败次数").register(registry);
        retentionDeleted = Counter.builder("harness.retention.deleted").description("数据保留任务删除的记录数量").register(registry);
        modelRetries = Counter.builder("harness.models.retries").description("模型供应商重试次数").register(registry);
        modelFallbacks = Counter.builder("harness.models.fallbacks").description("模型备用供应商切换次数").register(registry);
        modelFailures = Counter.builder("harness.models.failed").description("模型供应商最终失败次数").register(registry);
        workerDuration = Timer.builder("harness.worker.duration").description("Worker 执行耗时").register(registry);
        registry.gauge("harness.outbox.pending", pendingOutbox);
        registry.gauge("harness.rabbit.queue.depth", rabbitQueueDepth);
        registry.gauge("harness.rabbit.queue.capacity", rabbitQueueCapacity);
        registry.gauge("harness.worker.active", activeWorkers);
        registry.gauge("harness.worker.concurrency", workerConcurrency);
    }

    public void runCreated() { runsCreated.increment(); }
    public void runSucceeded() { runsSucceeded.increment(); }
    public void runFailed() { runsFailed.increment(); }
    public void runTimedOut() { runsTimedOut.increment(); }
    public void toolFailed() { toolFailures.increment(); }
    public void workerClaimed() { workerClaims.increment(); }
    public void workerFailed() { workerFailures.increment(); }
    public void workerInfrastructureFailed() { workerInfrastructureFailures.increment(); }
    public void outboxPublished() { outboxPublished.increment(); }
    public void outboxFailed() { outboxFailures.increment(); }
    public void rabbitRetry() { rabbitRetries.increment(); }
    public void rabbitDeadLetter() { rabbitDeadLetters.increment(); }
    public void rabbitBackpressure() { rabbitBackpressure.increment(); }
    public void rabbitQueuePollFailed() { rabbitQueuePollFailures.increment(); }
    public void rabbitQueueDepth(long depth) {
        rabbitQueueDepth.set(depth < 0 ? -1 : (int) Math.min(Integer.MAX_VALUE, depth));
    }
    public void rabbitQueueCapacity(long capacity) {
        rabbitQueueCapacity.set(capacity < 0 ? -1 : (int) Math.min(Integer.MAX_VALUE, capacity));
    }
    public void workerStarted() { activeWorkers.incrementAndGet(); }
    public void workerFinished() {
        activeWorkers.updateAndGet(value -> Math.max(0, value - 1));
    }
    public void workerConcurrency(int concurrency) {
        workerConcurrency.set(Math.max(0, concurrency));
    }
    public void retentionDeleted(int count) { retentionDeleted.increment(Math.max(0, count)); }
    public void modelRetry() { modelRetries.increment(); }
    public void modelFallback() { modelFallbacks.increment(); }
    public void modelFailed() { modelFailures.increment(); }
    public void pendingOutbox(int count) { pendingOutbox.set(Math.max(0, count)); }

    /**
     * 返回低基数的运行态摘要，供受 ops.read 保护的健康接口使用。
     * 队列指标在同步模式或 Rabbit 状态未知时返回 null，避免把 -1 这样的内部哨兵值暴露给客户端。
     */
    public OperationalSnapshot operationalSnapshot() {
        return new OperationalSnapshot(
                activeWorkers.get(),
                workerConcurrency.get(),
                monitoredValue(rabbitQueueDepth),
                monitoredValue(rabbitQueueCapacity),
                pendingOutbox.get(),
                Math.round(rabbitRetries.count()),
                Math.round(rabbitDeadLetters.count()),
                Math.round(runsTimedOut.count())
        );
    }

    private Integer monitoredValue(AtomicInteger value) {
        int current = value.get();
        return current < 0 ? null : current;
    }

    /** 健康摘要中使用的 Worker、队列和消息治理指标。 */
    public record OperationalSnapshot(
            int activeWorkers,
            int workerConcurrencyLimit,
            Integer queueDepth,
            Integer queueCapacity,
            int pendingOutbox,
            long rabbitRetryCount,
            long rabbitDeadLetterCount,
            long timedOutRunCount
    ) {
    }

    public <T> T recordWorkerDuration(Supplier<T> action) {
        return workerDuration.record(action);
    }

    public void recordWorkerDuration(Runnable action) {
        workerDuration.record(action);
    }
}
