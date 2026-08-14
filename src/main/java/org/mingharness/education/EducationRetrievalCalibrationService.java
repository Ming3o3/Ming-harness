package org.mingharness.education;

import org.mingharness.context.api.EducationRankingWeights;
import org.mingharness.runtime.domain.Run;
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

    public EducationRetrievalCalibrationService(EducationRetrievalJudgmentRepository judgmentRepository) {
        this(judgmentRepository, null);
    }

    @Autowired
    public EducationRetrievalCalibrationService(EducationRetrievalJudgmentRepository judgmentRepository,
                                                RunRepository runRepository) {
        this.judgmentRepository = judgmentRepository;
        this.runRepository = runRepository;
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
        Map<String, EducationRetrievalCalibrationSlice> slices = stateSlices(latest.values());
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
            java.util.Collection<EducationRetrievalJudgment> judgments) {
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
            EducationRankingWeights weights = EducationRankingWeights.calibrated(
                    aggregate.targetGroundingMean(), aggregate.prerequisiteUtilityMean(),
                    aggregate.difficultyFitMean(), aggregate.overallUtilityMean(),
                    aggregate.sampleCount()).withConditioning(
                    "CALIBRATED_V2:" + conditioning + ":n=" + aggregate.sampleCount());
            result.put(conditioning, new EducationRetrievalCalibrationSlice(
                    conditioning, aggregate.sampleCount(), aggregate.targetGroundingMean(),
                    aggregate.prerequisiteUtilityMean(), aggregate.difficultyFitMean(),
                    aggregate.overallUtilityMean(), weights));
        });
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

    private String conditioningFor(Run run) {
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
}
