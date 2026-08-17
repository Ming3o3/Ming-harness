package org.mingharness.education;

import org.mingharness.education.api.MasteryUpdateRequest;
import org.mingharness.runtime.domain.Run;
import org.mingharness.runtime.domain.Step;
import org.mingharness.runtime.repository.RunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        CodeDiagnosticCategory diagnosticCategory = CodeDiagnosticClassifier.classify(evaluation);
        String evidenceText = evaluation.hasBehaviorEvidence()
                ? "代码行为测试：" + evaluation.passedTestCaseCount() + "/"
                + evaluation.testCaseCount() + "（通过率 "
                + String.format(java.util.Locale.ROOT, "%.0f%%", evaluation.testPassRate() * 100.0)
                + "）；" + evaluation.diagnostics()
                : "代码语法/编译检查：" + evaluation.status().name()
                + "；" + (evaluation.diagnostics() == null ? "" : evaluation.diagnostics());
        List<KnowledgePointAssessment> knowledgePoints = knowledgePoints(run, evaluation, conceptKey);
        Map<String, Double> masteryBefore = new LinkedHashMap<>();
        Map<String, LearnerMastery> updatedByConcept = new LinkedHashMap<>();
        for (KnowledgePointAssessment point : knowledgePoints) {
            String pointConcept = point.conceptKey();
            double before = masteryRepository
                    .findByTenantIdAndLearnerProfileIdAndConceptKey(
                            tenantId, run.getEducationLearnerProfileId(), pointConcept)
                    .map(LearnerMastery::getMasteryScore).orElse(0.0);
            masteryBefore.put(pointConcept, before);
            LearnerStateTransitionContext context = LearnerStateTransitionContext.code(
                    run.getId(), evidenceText + "；知识点=" + pointConcept,
                    diagnosticCategory.name(), evaluation.testPassRate());
            LearnerMastery updated = learnerService.recordObservedMasteryWithoutGoalCompletion(
                    tenantId, learnerUserId, run.getEducationLearnerProfileId(),
                    new MasteryUpdateRequest(pointConcept, point.score(), point.correct(), null, null,
                            2, 0.5, false, false), context);
            // 兼容只 mock 旧接口的组件测试和旧扩展实现。
            if (updated == null) {
                updated = learnerService.recordObservedMasteryWithoutGoalCompletion(
                        tenantId, learnerUserId, run.getEducationLearnerProfileId(),
                        new MasteryUpdateRequest(pointConcept, point.score(), point.correct(), null, null,
                                2, 0.5, false, false));
            }
            if (updated != null) updatedByConcept.put(pointConcept, updated);
        }
        LearnerMastery updated = updatedByConcept.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(conceptKey))
                .map(Map.Entry::getValue)
                .findFirst().orElse(null);
        if (updated == null) return null;
        KnowledgePointAssessment targetPoint = knowledgePoints.stream()
                .filter(point -> point.conceptKey().equalsIgnoreCase(conceptKey))
                .findFirst().orElse(knowledgePoints.get(0));
        double before = masteryBefore.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(targetPoint.conceptKey()))
                .map(Map.Entry::getValue)
                .findFirst().orElse(0.0);
        AssessmentAttempt attempt = new AssessmentAttempt(tenantId, learnerUserId, run.getId(),
                step.getId(), run.getEducationLearningGoalId(), run.getEducationLearnerProfileId(),
                conceptKey, targetPoint.correct(), targetPoint.score(), before, updated.getMasteryScore(),
                AssessmentAttemptType.FORMATIVE, null, "CODE_EVALUATION", evidenceText,
                evaluation.diagnostics(), assignment.getId(), EducationRetrievalEvidence.snapshot(run), null);
        attempt.setStructuredEvidence(2, encodeKnowledgePoints(knowledgePoints), false, false,
                "CODE_" + diagnosticCategory.name());
        return attemptRepository.save(attempt);
    }

    /** 将冻结测试用例上的知识点标注投影为状态更新；未标注用例回退到作业目标。 */
    private List<KnowledgePointAssessment> knowledgePoints(Run run,
                                                            EducationCodeEvaluationResult evaluation,
                                                            String targetConcept) {
        if (!evaluation.hasBehaviorEvidence()) {
            double observed = evaluation.status() == CodeEvaluationStatus.PASSED ? 0.8 : 0.2;
            return List.of(new KnowledgePointAssessment(targetConcept,
                    evaluation.status() == CodeEvaluationStatus.PASSED, observed, 1.0,
                    "代码语法/编译检查：" + evaluation.status().name()));
        }
        Map<String, EducationProgrammingTestCase> specifications = new LinkedHashMap<>();
        if (run.educationConfiguration() != null) {
            run.educationConfiguration().programmingTestCases().cases().forEach(item ->
                    specifications.put(item.caseKey(), item));
        }
        Map<String, Double> totalWeights = new LinkedHashMap<>();
        Map<String, Double> passedWeights = new LinkedHashMap<>();
        Map<String, List<String>> caseKeys = new LinkedHashMap<>();
        for (EducationCodeTestCaseResult result : evaluation.testCaseResults()) {
            EducationProgrammingTestCase specification = specifications.get(result.caseKey());
            String concept = specification == null || specification.conceptKey() == null
                    || specification.conceptKey().isBlank() ? targetConcept : specification.conceptKey();
            double weight = specification == null ? 1.0 : specification.weight();
            totalWeights.merge(concept, weight, Double::sum);
            if (result.passed()) passedWeights.merge(concept, weight, Double::sum);
            caseKeys.computeIfAbsent(concept, ignored -> new ArrayList<>()).add(result.caseKey());
        }
        List<KnowledgePointAssessment> points = new ArrayList<>();
        totalWeights.forEach((concept, total) -> {
            double passRate = total <= 0.0 ? 0.0 : passedWeights.getOrDefault(concept, 0.0) / total;
            points.add(new KnowledgePointAssessment(concept, passRate >= 0.999999,
                    0.2 + 0.6 * passRate, 1.0,
                    "行为测试用例：" + String.join(", ", caseKeys.getOrDefault(concept, List.of()))
                            + "；知识点通过率 "
                            + String.format(java.util.Locale.ROOT, "%.0f%%", passRate * 100.0)));
        });
        if (points.stream().noneMatch(point -> point.conceptKey().equalsIgnoreCase(targetConcept))) {
            double passRate = evaluation.testPassRate();
            points.add(new KnowledgePointAssessment(targetConcept, passRate >= 0.999999,
                    0.2 + 0.6 * passRate, 1.0, "作业目标综合行为测试"));
        }
        return points;
    }

    private String encodeKnowledgePoints(List<KnowledgePointAssessment> points) {
        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        for (KnowledgePointAssessment point : points) {
            if (!first) json.append(',');
            first = false;
            json.append("{\"conceptKey\":\"").append(jsonEscape(point.conceptKey()))
                    .append("\",\"correct\":").append(point.correct())
                    .append(",\"score\":").append(point.score())
                    .append(",\"weight\":").append(point.weight());
            if (point.evidenceText() != null) {
                json.append(",\"evidenceText\":\"").append(jsonEscape(point.evidenceText())).append('"');
            }
            json.append('}');
        }
        return json.append(']').toString();
    }

    private String jsonEscape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\")
                .replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
