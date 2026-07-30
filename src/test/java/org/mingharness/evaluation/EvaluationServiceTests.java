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

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
