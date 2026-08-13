package org.mingharness.education;

import org.mingharness.context.ContextEvidenceSnapshotCodec;
import org.mingharness.context.api.ContextEvidence;
import org.mingharness.context.api.EducationRankingBreakdown;
import org.mingharness.education.api.EducationExperimentStrategyView;
import org.mingharness.education.api.EducationExperimentView;
import org.mingharness.runtime.domain.Run;
import org.mingharness.runtime.domain.RunStatus;
import org.mingharness.runtime.domain.Step;
import org.mingharness.runtime.repository.RunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;

/**
 * 聚合可复现教育检索实验事实。
 *
 * <p>策略在 Run 创建时已经冻结，因此这里不重新推断方法类型；证据直接从步骤快照
 * 解码，测评直接按 runId 关联。这样导出的比较结果可以和任意一轮回答回放对应，且
 * 不会因当前服务配置变化而污染历史样本。</p>
 */
@Service
public class EducationExperimentService {

    /** 仅表示是否达到进入基础统计分析的样本门槛，不等价于显著性检验。 */
    private static final int MIN_RUNS_FOR_ANALYSIS = 30;
    private static final int MIN_ASSESSMENTS_FOR_ANALYSIS = 30;

    private final RunRepository runRepository;
    private final AssessmentAttemptRepository assessmentRepository;

    public EducationExperimentService(RunRepository runRepository,
                                      AssessmentAttemptRepository assessmentRepository) {
        this.runRepository = runRepository;
        this.assessmentRepository = assessmentRepository;
    }

    @Transactional(readOnly = true)
    public EducationExperimentView summarize(String tenantId, String userId, boolean tenantScope) {
        List<Run> runs = tenantScope
                ? runRepository.findByTenantIdAndEducationModeTrueOrderByCreatedAtAsc(tenantId)
                : runRepository.findByTenantIdAndUserIdAndEducationModeTrueOrderByCreatedAtAsc(
                tenantId, userId);
        if (runs == null) runs = List.of();

        List<AssessmentAttempt> attempts = tenantScope
                ? assessmentRepository.findByTenantIdOrderByCreatedAtAsc(tenantId)
                : assessmentRepository.findByTenantIdAndUserIdOrderByCreatedAtAsc(tenantId, userId);
        if (attempts == null) attempts = List.of();

        Map<String, Run> runsById = new HashMap<>();
        Map<String, List<AssessmentAttempt>> attemptsByRun = new HashMap<>();
        for (Run run : runs) {
            runsById.put(run.getId(), run);
        }
        for (AssessmentAttempt attempt : attempts) {
            if (attempt == null || !runsById.containsKey(attempt.getRunId())) continue;
            attemptsByRun.computeIfAbsent(attempt.getRunId(), ignored -> new ArrayList<>()).add(attempt);
        }
        // 教育实验的分母必须与教育 Run 对齐；同一租户下可能还存在旧版手工测评或
        // 普通业务记录，不能把它们混入策略比较的总测评数。
        List<AssessmentAttempt> scopedAttempts = attempts.stream()
                .filter(attempt -> attempt != null && runsById.containsKey(attempt.getRunId()))
                .toList();

        Map<String, List<Run>> byStrategy = new LinkedHashMap<>();
        for (EducationRetrievalStrategy strategy : EducationRetrievalStrategy.values()) {
            byStrategy.put(strategy.name(), new ArrayList<>());
        }
        for (Run run : runs) {
            byStrategy.computeIfAbsent(run.getEducationRetrievalStrategy(), ignored -> new ArrayList<>())
                    .add(run);
        }

        List<EducationExperimentStrategyView> summaries = byStrategy.entrySet().stream()
                .map(entry -> summarizeStrategy(entry.getKey(), entry.getValue(), attemptsByRun))
                .toList();
        long successfulRuns = runs.stream().filter(run -> run.getStatus() == RunStatus.SUCCEEDED).count();
        return new EducationExperimentView(Instant.now(), runs.size(), successfulRuns,
                scopedAttempts.size(), tenantScope, summaries);
    }

    @Transactional(readOnly = true)
    public String exportCsv(String tenantId, String userId, boolean tenantScope) {
        EducationExperimentView view = summarize(tenantId, userId, tenantScope);
        StringBuilder csv = new StringBuilder();
        csv.append("retrieval_strategy,run_count,successful_run_count,runs_with_evidence,"
                + "evidence_coverage_rate,average_evidence_count,average_unique_evidence_count,"
                + "prerequisite_gap_coverage_rate,evidence_redundancy_rate,average_ranking_score,"
                + "average_marginal_coverage,average_target_concept_match,average_graph_coverage,"
                + "average_difficulty_fit,assessment_count,correct_assessment_count,"
                + "assessment_accuracy_rate,average_mastery_gain,target_goal_count,"
                + "target_reached_goal_count,target_reach_rate,average_rounds_to_target,sample_status\n");
        for (EducationExperimentStrategyView item : view.strategies()) {
            csv.append(csv(item.retrievalStrategy())).append(',')
                    .append(item.runCount()).append(',').append(item.successfulRunCount()).append(',')
                    .append(item.runsWithEvidence()).append(',').append(item.evidenceCoverageRate()).append(',')
                    .append(item.averageEvidenceCount()).append(',').append(item.averageUniqueEvidenceCount()).append(',')
                    .append(item.prerequisiteGapCoverageRate()).append(',').append(item.evidenceRedundancyRate()).append(',')
                    .append(item.averageRankingScore()).append(',').append(item.averageMarginalCoverage()).append(',')
                    .append(item.averageTargetConceptMatch()).append(',').append(item.averageGraphCoverage()).append(',')
                    .append(item.averageDifficultyFit()).append(',').append(item.assessmentCount()).append(',')
                    .append(item.correctAssessmentCount()).append(',').append(item.assessmentAccuracyRate()).append(',')
                    .append(item.averageMasteryGain()).append(',').append(item.targetGoalCount()).append(',')
                    .append(item.targetReachedGoalCount()).append(',').append(item.targetReachRate()).append(',')
                    .append(item.averageRoundsToTarget()).append(',').append(csv(item.sampleStatus())).append('\n');
        }
        return csv.toString();
    }

    private String csv(String value) {
        String safe = value == null ? "" : value.replace("\"", "\"\"");
        return "\"" + safe + "\"";
    }

    private EducationExperimentStrategyView summarizeStrategy(
            String strategy, List<Run> runs,
            Map<String, List<AssessmentAttempt>> attemptsByRun) {
        long successfulRuns = runs.stream().filter(run -> run.getStatus() == RunStatus.SUCCEEDED).count();
        List<EvidenceStats> evidenceStats = runs.stream().map(this::evidenceStats).toList();
        long runsWithEvidence = evidenceStats.stream().filter(EvidenceStats::hasEvidence).count();
        List<EvidenceStats> nonEmptyEvidence = evidenceStats.stream()
                .filter(EvidenceStats::hasEvidence).toList();

        List<AssessmentAttempt> attempts = runs.stream()
                .flatMap(run -> attemptsByRun.getOrDefault(run.getId(), List.of()).stream())
                .toList();
        long correct = attempts.stream().filter(AssessmentAttempt::isCorrect).count();
        Map<String, List<Run>> goalRuns = targetGoalRuns(runs, attemptsByRun);
        long reached = 0;
        List<Double> rounds = new ArrayList<>();
        for (List<Run> goalRunList : goalRuns.values()) {
            int round = firstReachedRound(goalRunList, attemptsByRun);
            if (round > 0) {
                reached++;
                rounds.add((double) round);
            }
        }

        return new EducationExperimentStrategyView(
                strategy,
                runs.size(), successfulRuns, runsWithEvidence,
                ratio(runsWithEvidence, runs.size()),
                average(evidenceStats, EvidenceStats::totalCount),
                average(evidenceStats, EvidenceStats::uniqueCount),
                average(nonEmptyEvidence, EvidenceStats::prerequisiteGapCoverage),
                average(nonEmptyEvidence, EvidenceStats::redundancyRate),
                average(nonEmptyEvidence, EvidenceStats::rankingScore),
                average(nonEmptyEvidence, EvidenceStats::marginalCoverage),
                average(nonEmptyEvidence, EvidenceStats::targetConceptMatch),
                average(nonEmptyEvidence, EvidenceStats::graphCoverage),
                average(nonEmptyEvidence, EvidenceStats::difficultyFit),
                attempts.size(), correct, ratio(correct, attempts.size()),
                attempts.stream().mapToDouble(item -> item.getMasteryAfter() - item.getMasteryBefore())
                        .average().orElse(0.0),
                goalRuns.size(), reached, ratio(reached, goalRuns.size()),
                rounds.stream().mapToDouble(Double::doubleValue).average().orElse(0.0),
                sampleStatus(runs.size(), attempts.size()));
    }

    private String sampleStatus(int runCount, int assessmentCount) {
        if (runCount == 0) return "NO_DATA";
        if (runCount < MIN_RUNS_FOR_ANALYSIS || assessmentCount < MIN_ASSESSMENTS_FOR_ANALYSIS) {
            return "INSUFFICIENT_SAMPLE";
        }
        return "ANALYSIS_READY";
    }

    private Map<String, List<Run>> targetGoalRuns(List<Run> runs,
                                                    Map<String, List<AssessmentAttempt>> attemptsByRun) {
        Map<String, List<Run>> result = new LinkedHashMap<>();
        for (Run run : runs) {
            if (run.getEducationLearningGoalId() == null
                    || run.getEducationLearningGoalId().isBlank()
                    || run.getEducationLearningGoalTarget() == null
                    || run.getEducationLearningGoalTarget() <= 0.0
                    || attemptsByRun.getOrDefault(run.getId(), List.of()).isEmpty()) {
                continue;
            }
            String key = run.getUserId() + "\u0000" + run.getEducationLearningGoalId();
            result.computeIfAbsent(key, ignored -> new ArrayList<>()).add(run);
        }
        result.values().forEach(value -> value.sort(Comparator.comparing(Run::getCreatedAt)));
        return result;
    }

    private int firstReachedRound(List<Run> runs,
                                  Map<String, List<AssessmentAttempt>> attemptsByRun) {
        for (int index = 0; index < runs.size(); index++) {
            Run run = runs.get(index);
            double target = run.getEducationLearningGoalTarget() == null
                    ? 1.0 : run.getEducationLearningGoalTarget();
            boolean reached = attemptsByRun.getOrDefault(run.getId(), List.of()).stream()
                    .anyMatch(attempt -> attempt.getMasteryAfter() >= target);
            if (reached) return index + 1;
        }
        return 0;
    }

    private EvidenceStats evidenceStats(Run run) {
        List<ContextEvidence> evidences = run.getSteps() == null ? List.of()
                : run.getSteps().stream()
                .filter(step -> step != null)
                .flatMap(step -> ContextEvidenceSnapshotCodec.decode(step.getContextEvidenceJson()).stream())
                .toList();
        if (evidences.isEmpty()) return EvidenceStats.empty();

        Set<String> unique = new LinkedHashSet<>();
        for (ContextEvidence evidence : evidences) {
            unique.add(evidenceKey(evidence));
        }
        List<EducationRankingBreakdown> breakdowns = evidences.stream()
                .map(ContextEvidence::rankingBreakdown).toList();
        double graphCoverage = averageBreakdown(breakdowns, EducationRankingBreakdown::graphCoverage);
        double prerequisiteCoverage = evidences.stream()
                .filter(evidence -> !evidence.prerequisiteGaps().isEmpty())
                .mapToDouble(evidence -> evidence.rankingBreakdown().graphCoverage())
                .average().orElse(graphCoverage);
        return new EvidenceStats(evidences.size(), unique.size(), prerequisiteCoverage,
                ratio(evidences.size() - unique.size(), evidences.size()),
                averageBreakdown(breakdowns, EducationRankingBreakdown::finalScore),
                averageBreakdown(breakdowns, EducationRankingBreakdown::marginalCoverageScore),
                averageBreakdown(breakdowns, EducationRankingBreakdown::targetConceptMatch),
                graphCoverage,
                averageBreakdown(breakdowns, EducationRankingBreakdown::difficultyFit));
    }

    private String evidenceKey(ContextEvidence evidence) {
        String citation = evidence == null ? "" : evidence.citation();
        if (citation == null || citation.isBlank()) {
            return evidence == null ? "" : evidence.documentId();
        }
        int chunk = citation.indexOf("#chunk:");
        return chunk < 0 ? citation : citation.substring(0, chunk);
    }

    private double average(List<EvidenceStats> values, ToDoubleFunction<EvidenceStats> extractor) {
        return values.stream().mapToDouble(extractor).average().orElse(0.0);
    }

    private double averageBreakdown(List<EducationRankingBreakdown> values,
                                    ToDoubleFunction<EducationRankingBreakdown> extractor) {
        return values.stream().mapToDouble(extractor).average().orElse(0.0);
    }

    private double ratio(long numerator, long denominator) {
        return denominator <= 0 ? 0.0 : numerator / (double) denominator;
    }

    private record EvidenceStats(int totalCount, int uniqueCount,
                                 double prerequisiteGapCoverage,
                                 double redundancyRate, double rankingScore,
                                 double marginalCoverage, double targetConceptMatch,
                                 double graphCoverage, double difficultyFit) {

        private static EvidenceStats empty() {
            return new EvidenceStats(0, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        }

        private boolean hasEvidence() {
            return totalCount > 0;
        }
    }
}
