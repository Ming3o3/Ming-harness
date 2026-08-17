package org.mingharness.education;

import org.mingharness.education.api.MasteryUpdateRequest;
import org.mingharness.runtime.domain.Run;
import org.mingharness.runtime.domain.Step;
import org.mingharness.runtime.repository.RunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 把代码语法/编译检查转成低权重、不可直接完成学习目标的形成性证据。
 * 语法通过不等于概念掌握；后续仍需模型、教师或测试用例提供更强证据。
 */
@Service
public class EducationCodeEvidenceService {

    private final AssessmentAttemptRepository attemptRepository;
    private final RunRepository runRepository;
    private final LearnerMasteryRepository masteryRepository;
    private final EducationLearnerService learnerService;

    public EducationCodeEvidenceService(AssessmentAttemptRepository attemptRepository,
                                        RunRepository runRepository,
                                        LearnerMasteryRepository masteryRepository,
                                        EducationLearnerService learnerService) {
        this.attemptRepository = attemptRepository;
        this.runRepository = runRepository;
        this.masteryRepository = masteryRepository;
        this.learnerService = learnerService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AssessmentAttempt record(String tenantId, String learnerUserId,
                                    LearningAssignment assignment, Run run,
                                    EducationCodeEvaluationResult evaluation) {
        if (assignment == null || run == null || evaluation == null
                || evaluation.status() == CodeEvaluationStatus.UNAVAILABLE
                || evaluation.status() == CodeEvaluationStatus.REJECTED
                || (evaluation.status() == CodeEvaluationStatus.ERROR
                || (evaluation.status() == CodeEvaluationStatus.TIMEOUT && !evaluation.hasBehaviorEvidence()))) {
            return null;
        }
        if (!run.isEducationMode() || !learnerUserId.equals(run.getUserId())
                || !tenantId.equals(run.getTenantId())
                || run.getEducationLearningGoalId() == null
                || run.getEducationLearnerProfileId() == null) {
            return null;
        }
        Step step = run.getSteps().stream().findFirst().orElse(null);
        if (step == null) return null;
        String conceptKey = run.getEducationConceptKey();
        if (conceptKey == null || conceptKey.isBlank()) return null;
        double observed = evaluation.hasBehaviorEvidence()
                ? 0.2 + 0.6 * evaluation.testPassRate()
                : (evaluation.status() == CodeEvaluationStatus.PASSED ? 0.8 : 0.2);
        boolean correct = evaluation.hasBehaviorEvidence()
                ? evaluation.testPassRate() >= 0.999999
                : evaluation.status() == CodeEvaluationStatus.PASSED;
        double before = masteryRepository
                .findByTenantIdAndLearnerProfileIdAndConceptKey(
                        tenantId, run.getEducationLearnerProfileId(), conceptKey)
                .map(LearnerMastery::getMasteryScore).orElse(0.0);
        LearnerMastery updated = learnerService.recordObservedMasteryWithoutGoalCompletion(
                tenantId, learnerUserId, run.getEducationLearnerProfileId(),
                new MasteryUpdateRequest(conceptKey, observed, correct, null, null,
                        2, 0.5, false, false));
        String evidenceText = evaluation.hasBehaviorEvidence()
                ? "代码行为测试：" + evaluation.passedTestCaseCount() + "/"
                + evaluation.testCaseCount() + "（通过率 "
                + String.format(java.util.Locale.ROOT, "%.0f%%", evaluation.testPassRate() * 100.0)
                + "）；" + evaluation.diagnostics()
                : "代码语法/编译检查：" + evaluation.status().name()
                + "；" + (evaluation.diagnostics() == null ? "" : evaluation.diagnostics());
        CodeDiagnosticCategory diagnosticCategory = CodeDiagnosticClassifier.classify(evaluation);
        AssessmentAttempt attempt = new AssessmentAttempt(tenantId, learnerUserId, run.getId(),
                step.getId(), run.getEducationLearningGoalId(), run.getEducationLearnerProfileId(),
                conceptKey, correct, observed, before, updated.getMasteryScore(),
                AssessmentAttemptType.FORMATIVE, null, "CODE_EVALUATION", evidenceText,
                evaluation.diagnostics(), assignment.getId(), EducationRetrievalEvidence.snapshot(run), null);
        attempt.setStructuredEvidence(2, "[]", false, false,
                "CODE_" + diagnosticCategory.name());
        return attemptRepository.save(attempt);
    }
}
