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
    private final SensitiveDataSanitizer sanitizer;

    public EvaluationService(RunService runService,
                             EvaluationReportRepository reportRepository,
                             @Value("${harness.model.name:demo-model}") String defaultModel,
                             @Value("${harness.prompt.version:prompt-v1}") String defaultPromptVersion,
                             @Value("${harness.policy.version:policy-v1}") String defaultPolicyVersion,
                             SensitiveDataSanitizer sanitizer) {
        this.runService = runService;
        this.reportRepository = reportRepository;
        this.defaultModel = defaultModel;
        this.defaultPromptVersion = defaultPromptVersion;
        this.defaultPolicyVersion = defaultPolicyVersion;
        this.sanitizer = sanitizer;
    }

    @Transactional
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
            RunDetail result = runService.start(created.id(), tenantId);
            boolean success = result.run().status() == RunStatus.SUCCEEDED
                    && (item.expectedContains() == null || item.expectedContains().isBlank()
                    || result.run().output().contains(item.expectedContains()));
            if (success) {
                passed++;
            }
            details.add(sanitizer.sanitize(item.name()) + "=" + (success ? "PASSED" : "FAILED")
                    + ":run=" + created.id() + ":status=" + result.run().status());
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

    @Transactional(readOnly = true)
    public List<EvaluationReportView> list(String tenantId) {
        return reportRepository.findTop50ByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(EvaluationReportView::from).toList();
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
