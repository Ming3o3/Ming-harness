package org.mingharness.evaluation;

import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.evaluation.api.EvaluationCaseRequest;
import org.mingharness.evaluation.api.EvaluationReportView;
import org.mingharness.evaluation.api.EvaluationRequest;
import org.mingharness.runtime.api.CreateRunRequest;
import org.mingharness.runtime.api.RunDetail;
import org.mingharness.runtime.api.RunSummary;
import org.mingharness.runtime.application.RunService;
import org.mingharness.runtime.domain.RunStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 用固定用例回放 Run，形成可比较的模型、Prompt 和策略版本报告。 */
@Service
public class EvaluationService {

    private final RunService runService;
    private final EvaluationReportRepository reportRepository;
    private final String defaultModel;
    private final String defaultPromptVersion;
    private final String defaultPolicyVersion;
    private final String executionMode;
    private final long waitTimeoutMs;
    private final long pollIntervalMs;
    private final SensitiveDataSanitizer sanitizer;

    public EvaluationService(RunService runService,
                             EvaluationReportRepository reportRepository,
                             @Value("${harness.model.name:demo-model}") String defaultModel,
                             @Value("${harness.prompt.version:prompt-v1}") String defaultPromptVersion,
                             @Value("${harness.policy.version:policy-v1}") String defaultPolicyVersion,
                             @Value("${harness.execution.mode:sync}") String executionMode,
                             @Value("${harness.evaluation.wait-timeout-ms:120000}") long waitTimeoutMs,
                             @Value("${harness.evaluation.poll-interval-ms:250}") long pollIntervalMs,
                             SensitiveDataSanitizer sanitizer) {
        this.runService = runService;
        this.reportRepository = reportRepository;
        this.defaultModel = defaultModel;
        this.defaultPromptVersion = defaultPromptVersion;
        this.defaultPolicyVersion = defaultPolicyVersion;
        this.executionMode = executionMode;
        if (waitTimeoutMs < 0) {
            throw new IllegalArgumentException("评测等待超时不能小于 0 毫秒");
        }
        if (pollIntervalMs < 1) {
            throw new IllegalArgumentException("评测轮询间隔不能小于 1 毫秒");
        }
        this.waitTimeoutMs = waitTimeoutMs;
        this.pollIntervalMs = pollIntervalMs;
        this.sanitizer = sanitizer;
    }

    /**
     * 评测会等待异步 Worker，显式禁止继承调用方事务；每个 Run 和最终报告各自独立提交。
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public EvaluationReportView run(String tenantId, String userId, EvaluationRequest request) {
        String evaluationKey = UUID.randomUUID().toString();
        int passed = 0;
        List<String> details = new ArrayList<>();
        for (int index = 0; index < request.cases().size(); index++) {
            EvaluationCaseRequest item = request.cases().get(index);
            CreateRunRequest runRequest = new CreateRunRequest(
                    tenantId, userId, item.name(), item.input(), item.toolName(), request.modelName(),
                    request.promptVersion(), request.policyVersion(), item.budget(),
                    "evaluation:" + evaluationKey + ":" + index, null);
            RunSummary created = runService.create(runRequest);
            RunDetail result = awaitCompletion(created.id(), tenantId, runService.start(created.id(), tenantId));
            boolean success = result.run().status() == RunStatus.SUCCEEDED
                    && (item.expectedContains() == null || item.expectedContains().isBlank()
                    || result.run().output().contains(item.expectedContains()));
            if (success) {
                passed++;
            }
            String outcome = success ? "PASSED" : isWaitTimeout(result) ? "TIMEOUT" : "FAILED";
            details.add(sanitizer.sanitize(item.name()) + "=" + outcome
                    + ":run=" + created.id() + ":status=" + result.run().status()
                    + ("TIMEOUT".equals(outcome) ? ":等待超时" : ""));
        }
        int total = request.cases().size();
        BigDecimal successRate = BigDecimal.valueOf(passed)
                .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
        EvaluationReport report = new EvaluationReport(
                tenantId, sanitizer.sanitize(request.name()),
                sanitizer.sanitize(valueOrDefault(request.modelName(), defaultModel)),
                sanitizer.sanitize(valueOrDefault(request.promptVersion(), defaultPromptVersion)),
                sanitizer.sanitize(valueOrDefault(request.policyVersion(), defaultPolicyVersion)),
                total, passed, total - passed, successRate, String.join("\n", details));
        return EvaluationReportView.from(reportRepository.save(report));
    }

    /**
     * Rabbit 模式的启动接口只负责投递命令，评测必须等到 Worker 写回最终状态后再判断结果。
     * 等待审批不自动代审批，避免离线评测绕过生产策略；超时则记录当时状态并继续后续用例。
     */
    private RunDetail awaitCompletion(String runId, String tenantId, RunDetail initial) {
        if (!isRabbitMode() || isSettled(initial)) {
            return initial;
        }
        long timeout = Math.max(0L, waitTimeoutMs);
        long deadline = System.nanoTime() + timeout * 1_000_000L;
        RunDetail current = initial;
        while (!isSettled(current)) {
            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0) {
                return current;
            }
            if (!sleepBeforePoll(Math.min(Math.max(0L, pollIntervalMs),
                    Math.max(1L, remainingNanos / 1_000_000L)))) {
                return current;
            }
            current = runService.getDetail(runId, tenantId);
        }
        return current;
    }

    private boolean sleepBeforePoll(long delayMs) {
        if (delayMs <= 0) {
            return true;
        }
        try {
            Thread.sleep(delayMs);
            return true;
        } catch (InterruptedException exception) {
            // 评测请求被取消时保留中断标记，并返回最近一次已知状态，避免吞掉容器停止信号。
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private boolean isRabbitMode() {
        return "rabbit".equalsIgnoreCase(executionMode);
    }

    private boolean isSettled(RunDetail detail) {
        return detail == null || detail.run() == null || switch (detail.run().status()) {
            case QUEUED, RUNNING -> false;
            case WAITING_APPROVAL, SUCCEEDED, FAILED, CANCELLED, TIMED_OUT -> true;
        };
    }

    private boolean isWaitTimeout(RunDetail detail) {
        return isRabbitMode() && detail != null && detail.run() != null
                && (detail.run().status() == RunStatus.QUEUED || detail.run().status() == RunStatus.RUNNING);
    }

    @Transactional(readOnly = true)
    public List<EvaluationReportView> list(String tenantId) {
        return reportRepository.findTop50ByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(EvaluationReportView::from).toList();
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
