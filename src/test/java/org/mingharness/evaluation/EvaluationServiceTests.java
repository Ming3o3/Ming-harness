package org.mingharness.evaluation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mingharness.evaluation.api.EvaluationCaseRequest;
import org.mingharness.evaluation.api.EvaluationRequest;
import org.mingharness.evaluation.api.EvaluationReportView;
import org.mingharness.runtime.repository.RunRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
class EvaluationServiceTests {

    @Autowired
    private EvaluationService evaluationService;
    @Autowired
    private EvaluationReportRepository reportRepository;
    @Autowired
    private RunRepository runRepository;

    @BeforeEach
    void cleanDatabase() {
        reportRepository.deleteAll();
        runRepository.deleteAll();
    }

    @Test
    void shouldPersistRegressionReportWithVersionMetadata() {
        EvaluationReportView report = evaluationService.run("tenant-eval", "evaluator",
                new EvaluationRequest("基础回归", List.of(
                        new EvaluationCaseRequest("成功用例", "订单状态", "demo.echo", "订单状态", null),
                        new EvaluationCaseRequest("失败断言", "订单状态", "demo.echo", "不存在的结果", null)
                ), "demo-model", "prompt-v2", "policy-v3"));

        assertEquals(2, report.totalCases());
        assertEquals(1, report.passedCases());
        assertEquals(1, report.failedCases());
        assertEquals("prompt-v2", report.promptVersion());
        assertEquals(1, evaluationService.list("tenant-eval").size());
    }

    @Test
    void shouldFailQualityGateWhenRegressionDropsBelowBaselineOrThreshold() {
        EvaluationReport baseline = reportRepository.save(new EvaluationReport(
                "tenant-eval", "线上基线", "demo-model", "prompt-v1", "policy-v1",
                1, 1, 0, BigDecimal.ONE, "baseline"));

        EvaluationReportView report = evaluationService.run("tenant-eval", "evaluator",
                new EvaluationRequest("回归检查", List.of(
                        new EvaluationCaseRequest("失败用例", "订单状态", "demo.echo", "不存在", null)
                ), "demo-model", "prompt-v2", "policy-v2", baseline.getId(), new BigDecimal("0.80")));

        assertEquals(0, BigDecimal.ONE.compareTo(report.baselineSuccessRate()));
        assertEquals(new BigDecimal("-1.0000"), report.successRateDelta());
        assertFalse(report.gatePassed());
    }
}
