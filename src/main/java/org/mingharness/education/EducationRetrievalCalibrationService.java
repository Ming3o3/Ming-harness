package org.mingharness.education;

import org.mingharness.context.api.EducationRankingWeights;
import org.mingharness.runtime.domain.Run;
import org.mingharness.runtime.domain.RunStatus;
import org.mingharness.runtime.repository.RunRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 将教师对真实 Run 证据的评价聚合为下一轮教育检索使用的权重快照。
 *
 * <p>同一评价者对同一 Run 证据的重复提交只保留最新记录，防止 UI 重试把一个样本
 * 放大；权重本身仍向固定先验收缩，低样本量时不会破坏既有排序行为。</p>
 */
@Service
public class EducationRetrievalCalibrationService {

    private final EducationRetrievalJudgmentRepository judgmentRepository;
    private final RunRepository runRepository;
    private final AssessmentAttemptRepository assessmentRepository;

    public EducationRetrievalCalibrationService(EducationRetrievalJudgmentRepository judgmentRepository) {
        this(judgmentRepository, null, null);
    }

    public EducationRetrievalCalibrationService(EducationRetrievalJudgmentRepository judgmentRepository,
                                                RunRepository runRepository) {
        this(judgmentRepository, runRepository, null);
    }

    @Autowired
    public EducationRetrievalCalibrationService(EducationRetrievalJudgmentRepository judgmentRepository,
                                                RunRepository runRepository,
                                                AssessmentAttemptRepository assessmentRepository) {
        this.judgmentRepository = judgmentRepository;
        this.runRepository = runRepository;
        this.assessmentRepository = assessmentRepository;
    }

    @Transactional(readOnly = true)
    public EducationRetrievalCalibrationSnapshot snapshotForTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return EducationRetrievalCalibrationSnapshot.prior();
        }
        Map<String, EducationRetrievalJudgment> latest = new LinkedHashMap<>();
        List<EducationRetrievalJudgment> judgments = judgmentRepository
                .findByTenantIdOrderByCreatedAtAsc(tenantId);
        for (EducationRetrievalJudgment judgment : judgments == null ? List.<EducationRetrievalJudgment>of() : judgments) {
            if (judgment == null
                    || !EducationRetrievalJudgment.CURRENT_RUBRIC_VERSION.equals(judgment.getRubricVersion())) {
                continue;
            }
            String key = String.join("\u0000",
                    safe(judgment.getRunId()), safe(judgment.getStepId()),
                    safe(judgment.getEvidenceCitation()), safe(judgment.getEvaluatorUserId()));
            EducationRetrievalJudgment previous = latest.get(key);
            if (previous == null || after(judgment.getCreatedAt(), previous.getCreatedAt())) {
                latest.put(key, judgment);
            }
        }
        if (latest.isEmpty()) return EducationRetrievalCalibrationSnapshot.prior();

        RubricAggregate overall = aggregate(latest.values());
        Map<String, OutcomeAggregate> outcomes = outcomeSlices(tenantId);
        Map<String, EducationRetrievalCalibrationSlice> slices = stateSlices(latest.values(), outcomes);
        return new EducationRetrievalCalibrationSnapshot(
                EducationRetrievalCalibrationSnapshot.VERSION, overall.sampleCount(),
                overall.targetGroundingMean(), overall.prerequisiteUtilityMean(),
                overall.difficultyFitMean(), overall.overallUtilityMean(), overall.weights(), slices);
    }

    @Transactional(readOnly = true)
    public String encodedSnapshotForTenant(String tenantId) {
        return EducationRetrievalCalibrationSnapshotCodec.encode(snapshotForTenant(tenantId));
    }

    public EducationRankingWeights weightsFromSnapshot(String encodedSnapshot) {
        return EducationRetrievalCalibrationSnapshotCodec.decode(encodedSnapshot).weights();
    }

    /** 根据 Run 创建时冻结的学习者状态选择对应分层权重。 */
    public EducationRankingWeights weightsFromSnapshot(String encodedSnapshot,
                                                       EducationRunConfiguration configuration) {
        EducationRetrievalCalibrationSnapshot snapshot =
                EducationRetrievalCalibrationSnapshotCodec.decode(encodedSnapshot);
        return snapshot.weightsFor(conditioningFor(configuration));
    }

    private Map<String, EducationRetrievalCalibrationSlice> stateSlices(
            java.util.Collection<EducationRetrievalJudgment> judgments,
            Map<String, OutcomeAggregate> outcomes) {
        if (runRepository == null || judgments == null || judgments.isEmpty()) return Map.of();
        Set<String> runIds = judgments.stream().map(EducationRetrievalJudgment::getRunId)
                .filter(value -> value != null && !value.isBlank()).collect(Collectors.toSet());
        if (runIds.isEmpty()) return Map.of();
        Map<String, Run> runs = runRepository.findByTenantIdAndIdIn(
                        judgments.iterator().next().getTenantId(), new ArrayList<>(runIds)).stream()
                .collect(Collectors.toMap(Run::getId, value -> value, (left, right) -> left));
        Map<String, List<EducationRetrievalJudgment>> grouped = new LinkedHashMap<>();
        for (EducationRetrievalJudgment judgment : judgments) {
            String conditioning = conditioningFor(runs.get(judgment.getRunId()));
            if ("UNKNOWN".equals(conditioning)) continue;
            grouped.computeIfAbsent(conditioning, ignored -> new ArrayList<>()).add(judgment);
        }
        Map<String, EducationRetrievalCalibrationSlice> result = new LinkedHashMap<>();
        grouped.forEach((conditioning, values) -> {
            RubricAggregate aggregate = aggregate(values);
            OutcomeAggregate outcome = outcomes == null ? null : outcomes.get(conditioning);
            EducationRankingWeights weights = EducationRankingWeights.calibrated(
                    aggregate.targetGroundingMean(), aggregate.prerequisiteUtilityMean(),
                    aggregate.difficultyFitMean(), aggregate.overallUtilityMean(),
                    aggregate.sampleCount(), outcome == null ? Double.NaN : outcome.score(),
                    outcome == null ? 0L : outcome.assessmentCount()).withConditioning(
                    "CALIBRATED_V2:" + conditioning + ":n=" + aggregate.sampleCount());
            result.put(conditioning, new EducationRetrievalCalibrationSlice(
                    conditioning, aggregate.sampleCount(), aggregate.targetGroundingMean(),
                    aggregate.prerequisiteUtilityMean(), aggregate.difficultyFitMean(),
                    aggregate.overallUtilityMean(), weights,
                    outcome == null ? 0L : outcome.assessmentCount(),
                    outcome == null ? 0.0 : outcome.masteryGainMean(),
                    outcome == null ? 0.0 : outcome.correctRate(),
                    outcome == null ? 0.0 : outcome.targetReachRate(),
                    outcome == null ? 0.0 : outcome.score()));
        });
        return result;
    }

    /** 从成功教育 Run 的形成性测评提取状态分层学习结果信号。 */
    private Map<String, OutcomeAggregate> outcomeSlices(String tenantId) {
        if (runRepository == null || assessmentRepository == null
                || tenantId == null || tenantId.isBlank()) return Map.of();
        List<Run> runs = runRepository.findByTenantIdAndEducationModeTrueOrderByCreatedAtAsc(tenantId);
        List<AssessmentAttempt> attempts = assessmentRepository.findByTenantIdOrderByCreatedAtAsc(tenantId);
        if (runs == null || attempts == null || runs.isEmpty() || attempts.isEmpty()) return Map.of();
        Map<String, List<AssessmentAttempt>> byRun = new LinkedHashMap<>();
        for (AssessmentAttempt attempt : attempts) {
            if (attempt == null || attempt.getAssessmentType() != AssessmentAttemptType.FORMATIVE) continue;
            byRun.computeIfAbsent(attempt.getRunId(), ignored -> new ArrayList<>()).add(attempt);
        }
        Map<String, OutcomeAccumulator> grouped = new LinkedHashMap<>();
        for (Run run : runs) {
            if (run == null || run.getStatus() != RunStatus.SUCCEEDED) continue;
            List<AssessmentAttempt> runAttempts = byRun.getOrDefault(run.getId(), List.of());
            if (runAttempts.isEmpty()) continue;
            String conditioning = conditioningFor(run);
            if ("UNKNOWN".equals(conditioning)) continue;
            double target = run.getEducationLearningGoalTarget() == null
                    ? 1.0 : run.getEducationLearningGoalTarget();
            boolean reached = runAttempts.stream().anyMatch(item -> item.getMasteryAfter() >= target);
            OutcomeAccumulator accumulator = grouped.computeIfAbsent(conditioning,
                    ignored -> new OutcomeAccumulator());
            accumulator.addRun(runAttempts, reached);
        }
        Map<String, OutcomeAggregate> result = new LinkedHashMap<>();
        grouped.forEach((conditioning, accumulator) -> result.put(conditioning, accumulator.aggregate()));
        return result;
    }

    private RubricAggregate aggregate(java.util.Collection<EducationRetrievalJudgment> judgments) {
        List<EducationRetrievalJudgment> values = judgments == null ? List.of()
                : judgments.stream().filter(java.util.Objects::nonNull).toList();
        long sampleCount = values.size();
        return new RubricAggregate(sampleCount,
                values.stream().mapToInt(EducationRetrievalJudgment::getTargetGroundingScore)
                        .average().orElse(3.0),
                values.stream().mapToInt(EducationRetrievalJudgment::getPrerequisiteUtilityScore)
                        .average().orElse(3.0),
                values.stream().mapToInt(EducationRetrievalJudgment::getDifficultyFitScore)
                        .average().orElse(3.0),
                values.stream().mapToInt(EducationRetrievalJudgment::getOverallUtilityScore)
                        .average().orElse(3.0),
                EducationRankingWeights.calibrated(
                        values.stream().mapToInt(EducationRetrievalJudgment::getTargetGroundingScore)
                                .average().orElse(3.0),
                        values.stream().mapToInt(EducationRetrievalJudgment::getPrerequisiteUtilityScore)
                                .average().orElse(3.0),
                        values.stream().mapToInt(EducationRetrievalJudgment::getDifficultyFitScore)
                                .average().orElse(3.0),
                        values.stream().mapToInt(EducationRetrievalJudgment::getOverallUtilityScore)
                                .average().orElse(3.0), sampleCount));
    }

    /** 与 ContextBuilder 使用同一状态命名，确保展示、校准和 Run 执行不会出现两套分桶。 */
    public String conditioningFor(EducationRunConfiguration configuration) {
        if (configuration == null || !configuration.enabled()) return "UNKNOWN";
        double mastery = configuration.masteryScores().getOrDefault(
                configuration.conceptKey(), configuration.learningGoalBaselineMastery());
        EducationDependencyGraph graph = configuration.dependencyGraph();
        double deficit = graph == null ? 0.0 : graph.prerequisites().stream()
                .mapToDouble(EducationDependencyPath::deficit).average().orElse(0.0);
        return EducationRankingWeights.conditioned(mastery, deficit,
                graph != null && !graph.prerequisites().isEmpty()).conditioning();
    }

    /** 从 Run 创建时冻结的学习状态恢复同一状态分桶，供策略推荐复用。 */
    public String conditioningFor(Run run) {
        return run == null ? "UNKNOWN" : conditioningFor(run.educationConfiguration());
    }

    private boolean after(Instant candidate, Instant previous) {
        if (candidate == null) return false;
        return previous == null || candidate.isAfter(previous);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private record RubricAggregate(long sampleCount, double targetGroundingMean,
                                   double prerequisiteUtilityMean, double difficultyFitMean,
                                   double overallUtilityMean, EducationRankingWeights weights) {
    }

    private record OutcomeAggregate(long runCount, long assessmentCount,
                                    double masteryGainMean, double correctRate,
                                    double targetReachRate, double score) {
    }

    private static final class OutcomeAccumulator {
        private long runCount;
        private long assessmentCount;
        private long correctCount;
        private long reachedRunCount;
        private double masteryGainSum;

        private void addRun(List<AssessmentAttempt> attempts, boolean reached) {
            runCount++;
            if (reached) reachedRunCount++;
            for (AssessmentAttempt attempt : attempts) {
                assessmentCount++;
                masteryGainSum += attempt.getMasteryAfter() - attempt.getMasteryBefore();
                if (attempt.isCorrect()) correctCount++;
            }
        }

        private OutcomeAggregate aggregate() {
            double gain = assessmentCount <= 0 ? 0.0 : masteryGainSum / assessmentCount;
            double correct = assessmentCount <= 0 ? 0.0 : correctCount / (double) assessmentCount;
            double reach = runCount <= 0 ? 0.0 : reachedRunCount / (double) runCount;
            double score = 0.5 * clamp(0.5 + gain) + 0.3 * correct + 0.2 * reach;
            return new OutcomeAggregate(runCount, assessmentCount, gain, correct, reach, score);
        }

        private double clamp(double value) {
            return Math.max(0.0, Math.min(1.0, value));
        }
    }
}
