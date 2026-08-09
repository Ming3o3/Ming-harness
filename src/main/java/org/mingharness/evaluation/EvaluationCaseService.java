package org.mingharness.evaluation;

import org.mingharness.common.BusinessException;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.evaluation.api.EvaluationCaseView;
import org.mingharness.evaluation.api.SaveEvaluationCaseRequest;
import org.mingharness.runtime.domain.Run;
import org.mingharness.runtime.domain.RunScenario;
import org.mingharness.runtime.domain.Step;
import org.mingharness.runtime.domain.StepType;
import org.mingharness.runtime.repository.RunRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class EvaluationCaseService {

    private final EvaluationCaseRepository caseRepository;
    private final RunRepository runRepository;
    private final SensitiveDataSanitizer sanitizer;

    public EvaluationCaseService(EvaluationCaseRepository caseRepository, RunRepository runRepository,
                                 SensitiveDataSanitizer sanitizer) {
        this.caseRepository = caseRepository;
        this.runRepository = runRepository;
        this.sanitizer = sanitizer;
    }

    @Transactional
    public EvaluationCaseView saveFromRun(String tenantId, String userId, SaveEvaluationCaseRequest request) {
        Run run = runRepository.findById(request.runId()).orElseThrow(() ->
                new BusinessException(HttpStatus.NOT_FOUND, "RUN_NOT_FOUND", "执行任务不存在: " + request.runId()));
        if (!tenantId.equals(run.getTenantId()) || !userId.equals(run.getUserId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "EVALUATION_CASE_ACCESS_DENIED", "只能保存自己组织和用户的 Run");
        }
        String name = request.name() == null || request.name().isBlank()
                ? run.getTitle() : request.name().trim();
        String toolName = run.getSteps().stream()
                .filter(step -> step.getType() == StepType.TOOL)
                .map(Step::getName).findFirst().orElse(null);
        String expected = request.expectedContains() == null ? "" : request.expectedContains().trim();
        EvaluationCase item = new EvaluationCase(tenantId, userId, run.getId(), sanitizer.sanitize(name),
                sanitizer.sanitize(run.getInput()), sanitizer.sanitize(toolName), sanitizer.sanitize(expected),
                run.getBudget() == null ? BigDecimal.ONE : run.getBudget(), run.getScenario());
        return EvaluationCaseView.from(caseRepository.save(item));
    }

    @Transactional(readOnly = true)
    public List<EvaluationCaseView> list(String tenantId) {
        return caseRepository.findTop200ByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(EvaluationCaseView::from).toList();
    }

    @Transactional
    public void delete(String tenantId, String userId, String caseId) {
        EvaluationCase item = caseRepository.findById(caseId).orElseThrow(() ->
                new BusinessException(HttpStatus.NOT_FOUND, "EVALUATION_CASE_NOT_FOUND", "评测用例不存在: " + caseId));
        if (!tenantId.equals(item.getTenantId()) || !userId.equals(item.getOwnerUserId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "EVALUATION_CASE_ACCESS_DENIED", "只能删除自己的评测用例");
        }
        caseRepository.delete(item);
    }
}
