package org.mingharness.runtime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mingharness.audit.AuditEventRepository;
import org.mingharness.runtime.api.CreateRunRequest;
import org.mingharness.runtime.api.RunDetail;
import org.mingharness.runtime.api.RunSummary;
import org.mingharness.runtime.application.RunCancellationSignal;
import org.mingharness.runtime.application.RunService;
import org.mingharness.runtime.domain.RunStatus;
import org.mingharness.runtime.repository.RunRepository;
import org.mingharness.tool.HarnessTool;
import org.mingharness.tool.ToolDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证取消请求在 Worker 长步骤期间也能通过短期信号协作停止。 */
@SpringBootTest
@Import(RunCancellationTests.CancellationToolConfiguration.class)
class RunCancellationTests {

    @Autowired
    private RunService runService;
    @Autowired
    private RunRepository runRepository;
    @Autowired
    private AuditEventRepository auditEventRepository;
    @Autowired
    private BlockingToolState blockingToolState;
    @Autowired
    private RunCancellationSignal cancellationSignal;

    @BeforeEach
    void cleanDatabase() {
        auditEventRepository.deleteAll();
        runRepository.deleteAll();
        blockingToolState.reset();
    }

    @Test
    void shouldStopBeforeNextStepWhenCancellationArrivesDuringToolExecution() throws Exception {
        RunSummary created = runService.create(new CreateRunRequest(
                "tenant-cancel", "cancel-user", "协作式取消", "等待取消",
                "test.cancel-blocking", null, "prompt-v1", "policy-v1", BigDecimal.ONE));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var execution = executor.submit(() -> runService.start(created.id(), "tenant-cancel"));
            assertTrue(blockingToolState.entered.await(5, TimeUnit.SECONDS));

            var cancellation = executor.submit(() -> runService.cancel(created.id(), "tenant-cancel"));
            // 确认取消线程先写入跨实例信号，再释放阻塞工具，避免测试依赖固定睡眠时间。
            long signalDeadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            while (!cancellationSignal.isRequested(created.id(), "tenant-cancel")
                    && System.nanoTime() < signalDeadline) {
                Thread.sleep(10);
            }
            assertTrue(cancellationSignal.isRequested(created.id(), "tenant-cancel"));
            blockingToolState.release.countDown();

            cancellation.get(5, TimeUnit.SECONDS);
            RunDetail workerResult = execution.get(5, TimeUnit.SECONDS);
            RunStatus finalStatus = runRepository.findById(created.id()).orElseThrow().getStatus();

            assertEquals(RunStatus.CANCELLED, finalStatus);
            assertFalse(auditEventRepository.findTop100ByRunIdOrderByCreatedAtDesc(created.id()).stream()
                    .anyMatch(event -> "RUN_SUCCEEDED".equals(event.getEventType())));
            assertEquals(RunStatus.RUNNING, workerResult.run().status());
        } finally {
            blockingToolState.release.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void shouldScopeCancellationSignalByTenant() {
        cancellationSignal.request("run-cancel-scope", "tenant-a", Duration.ofSeconds(1));

        assertTrue(cancellationSignal.isRequested("run-cancel-scope", "tenant-a"));
        assertFalse(cancellationSignal.isRequested("run-cancel-scope", "tenant-b"));
    }

    static class BlockingToolState {
        private volatile CountDownLatch entered = new CountDownLatch(1);
        private volatile CountDownLatch release = new CountDownLatch(1);

        void reset() {
            entered = new CountDownLatch(1);
            release = new CountDownLatch(1);
        }
    }

    @TestConfiguration
    static class CancellationToolConfiguration {

        @Bean
        BlockingToolState blockingToolState() {
            return new BlockingToolState();
        }

        @Bean
        HarnessTool blockingTool(BlockingToolState state) {
            return new HarnessTool() {
                @Override
                public ToolDefinition definition() {
                    return new ToolDefinition("test.cancel-blocking", "取消竞态测试工具",
                            true, "LOW", false, Map.of());
                }

                @Override
                public String execute(String input) {
                    state.entered.countDown();
                    try {
                        state.release.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                    return input;
                }
            };
        }
    }
}
