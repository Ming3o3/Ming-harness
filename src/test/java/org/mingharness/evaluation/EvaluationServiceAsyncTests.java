package org.mingharness.evaluation;

import org.junit.jupiter.api.Test;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.evaluation.api.EvaluationCaseRequest;
import org.mingharness.evaluation.api.EvaluationReportView;
import org.mingharness.evaluation.api.EvaluationRequest;
import org.mingharness.runtime.api.CreateRunRequest;
import org.mingharness.runtime.api.RunDetail;
import org.mingharness.runtime.api.RunSummary;
import org.mingharness.runtime.application.RunService;
import org.mingharness.runtime.domain.RunStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证 Rabbit 异步执行场景不会把刚投递的 RUNNING Run 误判为失败。 */
class EvaluationServiceAsyncTests {

    @Test
    void shouldWaitForRabbitRunToReachTerminalState() {
        RunService runService = mock(RunService.class);
        EvaluationReportRepository reportRepository = mock(EvaluationReportRepository.class);
        EvaluationService service = new EvaluationService(runService, reportRepository,
                "demo-model", "prompt-v1", "policy-v1", "rabbit", 1_000, 1,
                new SensitiveDataSanitizer());
        RunSummary created = run("run-async", RunStatus.QUEUED, null);
        RunDetail running = new RunDetail(run("run-async", RunStatus.RUNNING, null), List.of());
        RunDetail succeeded = new RunDetail(run("run-async", RunStatus.SUCCEEDED, "订单状态已完成"), List.of());

        when(runService.create(any(CreateRunRequest.class))).thenReturn(created);
        when(runService.start("run-async", "tenant-eval")).thenReturn(running);
        when(runService.getDetail("run-async", "tenant-eval")).thenReturn(running, succeeded);
        when(reportRepository.save(any(EvaluationReport.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EvaluationReportView report = service.run("tenant-eval", "evaluator", new EvaluationRequest(
                "异步回归", List.of(new EvaluationCaseRequest(
                "异步用例", "订单状态", "demo.echo", "已完成", BigDecimal.ONE
        )), null, null, null));

        assertEquals(1, report.passedCases());
        assertEquals(0, report.failedCases());
        assertTrue(report.details().contains("异步用例=PASSED:run=run-async:status=SUCCEEDED"));
        verify(runService, times(2)).getDetail("run-async", "tenant-eval");
    }

    @Test
    void shouldRecordCurrentStatusWhenRabbitEvaluationWaitsTooLong() {
        RunService runService = mock(RunService.class);
        EvaluationReportRepository reportRepository = mock(EvaluationReportRepository.class);
        EvaluationService service = new EvaluationService(runService, reportRepository,
                "demo-model", "prompt-v1", "policy-v1", "rabbit", 5, 1,
                new SensitiveDataSanitizer());
        RunSummary created = run("run-timeout", RunStatus.QUEUED, null);
        RunDetail running = new RunDetail(run("run-timeout", RunStatus.RUNNING, null), List.of());

        when(runService.create(any(CreateRunRequest.class))).thenReturn(created);
        when(runService.start("run-timeout", "tenant-eval")).thenReturn(running);
        when(runService.getDetail("run-timeout", "tenant-eval")).thenReturn(running);
        when(reportRepository.save(any(EvaluationReport.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EvaluationReportView report = service.run("tenant-eval", "evaluator", new EvaluationRequest(
                "超时回归", List.of(new EvaluationCaseRequest(
                "缓慢用例", "订单状态", "demo.echo", "已完成", BigDecimal.ONE
        )), null, null, null));

        assertEquals(0, report.passedCases());
        assertEquals(1, report.failedCases());
        assertTrue(report.details().contains("缓慢用例=TIMEOUT:run=run-timeout:status=RUNNING:等待超时"));
    }

    @Test
    void shouldNotApproveWaitingRunAutomatically() {
        RunService runService = mock(RunService.class);
        EvaluationReportRepository reportRepository = mock(EvaluationReportRepository.class);
        EvaluationService service = new EvaluationService(runService, reportRepository,
                "demo-model", "prompt-v1", "policy-v1", "rabbit", 1_000, 1,
                new SensitiveDataSanitizer());
        RunSummary created = run("run-approval", RunStatus.QUEUED, null);
        RunDetail waiting = new RunDetail(run("run-approval", RunStatus.WAITING_APPROVAL, null), List.of());

        when(runService.create(any(CreateRunRequest.class))).thenReturn(created);
        when(runService.start("run-approval", "tenant-eval")).thenReturn(waiting);
        when(reportRepository.save(any(EvaluationReport.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EvaluationReportView report = service.run("tenant-eval", "evaluator", new EvaluationRequest(
                "审批回归", List.of(new EvaluationCaseRequest(
                "需要审批", "订单状态", "demo.echo", "已完成", BigDecimal.ONE
        )), null, null, null));

        assertEquals(0, report.passedCases());
        assertTrue(report.details().contains("需要审批=FAILED:run=run-approval:status=WAITING_APPROVAL"));
        verify(runService, never()).getDetail("run-approval", "tenant-eval");
        verify(runService, never()).approve("run-approval", "tenant-eval");
    }

    private RunSummary run(String id, RunStatus status, String output) {
        Instant timestamp = Instant.parse("2026-07-31T00:00:00Z");
        return new RunSummary(id, "tenant-eval", "evaluator", "评测用例", "demo-model",
                "prompt-v1", "policy-v1", "订单状态", output, null, status, BigDecimal.ONE,
                timestamp, timestamp, 2, "evaluation-key", "trace-id", 0, BigDecimal.ZERO);
    }
}
