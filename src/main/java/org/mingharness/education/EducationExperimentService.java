package org.mingharness.education;

import org.mingharness.context.ContextEvidenceSnapshotCodec;
import org.mingharness.context.api.ContextEvidence;
import org.mingharness.context.api.EducationRankingBreakdown;
import org.mingharness.education.api.EducationExperimentStrategyView;
import org.mingharness.education.api.EducationExperimentView;
import org.mingharness.education.api.EducationExperimentPairView;
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
    /** 实验表按实际执行方法聚合；ADAPTIVE/BALANCED_EXPERIMENT 是分配器而非方法。 */
    private static final List<EducationRetrievalStrategy> EXPERIMENT_STRATEGIES = List.of(
            EducationRetrievalStrategy.FULL,
            EducationRetrievalStrategy.VECTOR_ONLY,
            EducationRetrievalStrategy.KEYWORD_ONLY,
            EducationRetrievalStrategy.NO_LEARNER_STATE,
            EducationRetrievalStrategy.NO_DEPENDENCY_GRAPH,
            EducationRetrievalStrategy.STATIC_WEIGHT,
            EducationRetrievalStrategy.CALIBRATED,
            EducationRetrievalStrategy.ADAPTIVE);

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
        for (EducationRetrievalStrategy strategy : EXPERIMENT_STRATEGIES) {
            byStrategy.put(strategy.name(), new ArrayList<>());
        }
        for (Run run : runs) {
            byStrategy.computeIfAbsent(effectiveExperimentStrategy(run), ignored -> new ArrayList<>())
                    .add(run);
        }

        List<EducationExperimentStrategyView> summaries = byStrategy.entrySet().stream()
                .map(entry -> summarizeStrategy(entry.getKey(), entry.getValue(), attemptsByRun))
                .toList();
        List<EducationExperimentPairView> pairedComparisons = pairedComparisons(runs, attemptsByRun);
        long successfulRuns = runs.stream().filter(run -> run.getStatus() == RunStatus.SUCCEEDED).count();
        Map<String, Set<String>> strategiesByLearnerGoal = new HashMap<>();
        for (Run run : runs) {
            if (run.getEducationLearningGoalId() == null || run.getEducationLearningGoalId().isBlank()) continue;
            String key = run.getUserId() + "\u0000" + run.getEducationLearningGoalId();
            strategiesByLearnerGoal.computeIfAbsent(key, ignored -> new LinkedHashSet<>())
                    .add(effectiveExperimentStrategy(run));
        }
        long paired = strategiesByLearnerGoal.values().stream().filter(value -> value.size() > 1).count();
        long fullyPaired = strategiesByLearnerGoal.values().stream()
                .filter(value -> value.size() == EXPERIMENT_STRATEGIES.size()).count();
        return new EducationExperimentView(Instant.now(), runs.size(), successfulRuns,
                scopedAttempts.size(), tenantScope, paired, fullyPaired, summaries, pairedComparisons);
    }

    @Transactional(readOnly = true)
    public String exportCsv(String tenantId, String userId, boolean tenantScope) {
        EducationExperimentView view = summarize(tenantId, userId, tenantScope);
        StringBuilder csv = new StringBuilder();
        csv.append("retrieval_strategy,run_count,successful_run_count,runs_with_evidence,"
                + "evidence_coverage_rate,average_evidence_count,average_unique_evidence_count,"
                + "average_evidence_chars,average_utility_per_thousand_chars,"
                + "average_marginal_coverage_per_thousand_chars,"
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
                    .append(item.averageEvidenceChars()).append(',')
                    .append(item.averageUtilityPerThousandChars()).append(',')
                    .append(item.averageMarginalCoveragePerThousandChars()).append(',')
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

    @Transactional(readOnly = true)
    public String exportPairedCsv(String tenantId, String userId, boolean tenantScope) {
        EducationExperimentView view = summarize(tenantId, userId, tenantScope);
        StringBuilder csv = new StringBuilder();
        csv.append("reference_strategy,compared_strategy,paired_learner_goal_count,"
                + "reference_average_mastery_gain,compared_average_mastery_gain,mastery_gain_delta,"
                + "reference_target_reach_rate,compared_target_reach_rate,target_reach_rate_delta,"
                + "reference_average_rounds_to_target,compared_average_rounds_to_target,"
                + "average_rounds_to_target_delta,reference_prerequisite_gap_coverage,"
                + "compared_prerequisite_gap_coverage,prerequisite_gap_coverage_delta,sample_status\n");
        for (EducationExperimentPairView item : view.pairedComparisons()) {
            csv.append(csv(item.referenceStrategy())).append(',')
                    .append(csv(item.comparedStrategy())).append(',')
                    .append(item.pairedLearnerGoalCount()).append(',')
                    .append(item.referenceAverageMasteryGain()).append(',')
                    .append(item.comparedAverageMasteryGain()).append(',')
                    .append(item.masteryGainDelta()).append(',')
                    .append(item.referenceTargetReachRate()).append(',')
                    .append(item.comparedTargetReachRate()).append(',')
                    .append(item.targetReachRateDelta()).append(',')
                    .append(item.referenceAverageRoundsToTarget()).append(',')
                    .append(item.comparedAverageRoundsToTarget()).append(',')
                    .append(item.averageRoundsToTargetDelta()).append(',')
                    .append(item.referencePrerequisiteGapCoverage()).append(',')
                    .append(item.comparedPrerequisiteGapCoverage()).append(',')
                    .append(item.prerequisiteGapCoverageDelta()).append(',')
                    .append(csv(item.sampleStatus())).append('\n');
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
                average(nonEmptyEvidence, EvidenceStats::evidenceChars),
                average(nonEmptyEvidence, EvidenceStats::utilityPerThousandChars),
                average(nonEmptyEvidence, EvidenceStats::marginalCoveragePerThousandChars),
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

    /**
     * 在同一学习者-目标内做策略配对，避免把不同学习者的掌握度差异误当成检索收益。
     * FULL 作为参考策略，其他策略分别与它比较；只有两边都有成功 Run 和测评事实时
     * 才计入样本。该结果用于描述性配对分析，不替代随机实验或显著性检验。
     */
    private List<EducationExperimentPairView> pairedComparisons(
            List<Run> runs, Map<String, List<AssessmentAttempt>> attemptsByRun) {
        Map<String, Map<String, List<Run>>> grouped = new LinkedHashMap<>();
        for (Run run : runs) {
            if (run == null || run.getStatus() != RunStatus.SUCCEEDED
                    || run.getUserId() == null || run.getUserId().isBlank()
                    || run.getEducationLearningGoalId() == null
                    || run.getEducationLearningGoalId().isBlank()) {
                continue;
            }
            String goalKey = run.getUserId() + "\u0000" + run.getEducationLearningGoalId();
            grouped.computeIfAbsent(goalKey, ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(effectiveExperimentStrategy(run), ignored -> new ArrayList<>())
                    .add(run);
        }

        Map<String, PairAccumulator> accumulators = new LinkedHashMap<>();
        for (Map<String, List<Run>> byStrategy : grouped.values()) {
            GoalStrategyOutcome reference = goalStrategyOutcome(
                    byStrategy.get(EducationRetrievalStrategy.FULL.name()), attemptsByRun);
            if (reference == null) continue;
            for (EducationRetrievalStrategy strategy : EXPERIMENT_STRATEGIES) {
                if (strategy == EducationRetrievalStrategy.FULL) continue;
                GoalStrategyOutcome compared = goalStrategyOutcome(
                        byStrategy.get(strategy.name()), attemptsByRun);
                if (compared == null) continue;
                accumulators.computeIfAbsent(strategy.name(), ignored -> new PairAccumulator())
                        .add(reference, compared);
            }
        }

        return accumulators.entrySet().stream()
                .map(entry -> entry.getValue().view(EducationRetrievalStrategy.FULL.name(),
                        entry.getKey(), sampleStatusForPairs(entry.getValue().count)))
                .toList();
    }

    /** 将分配器 Run 解码为实际执行方法，避免把 BALANCED_EXPERIMENT 当成一种检索算法。 */
    private String effectiveExperimentStrategy(Run run) {
        EducationRetrievalStrategy requested = EducationRetrievalStrategy.parse(
                run == null ? null : run.getEducationRetrievalStrategy());
        if (requested != EducationRetrievalStrategy.ADAPTIVE
                && requested != EducationRetrievalStrategy.BALANCED_EXPERIMENT) {
            return requested.name();
        }
        EducationRetrievalStrategy effective = EducationRetrievalStrategy.parse(
                EducationRetrievalPolicySnapshotCodec.decode(
                        run == null ? null : run.getEducationRetrievalWeights()).selectedStrategy());
        return EXPERIMENT_STRATEGIES.contains(effective)
                ? effective.name() : EducationRetrievalStrategy.FULL.name();
    }

    private GoalStrategyOutcome goalStrategyOutcome(List<Run> strategyRuns,
                                                    Map<String, List<AssessmentAttempt>> attemptsByRun) {
        if (strategyRuns == null || strategyRuns.isEmpty()) return null;
        List<Run> ordered = strategyRuns.stream()
                .sorted(Comparator.comparing(Run::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        List<AssessmentAttempt> attempts = ordered.stream()
                .flatMap(run -> attemptsByRun.getOrDefault(run.getId(), List.of()).stream())
                .toList();
        if (attempts.isEmpty()) return null;

        double masteryGain = attempts.stream()
                .mapToDouble(attempt -> attempt.getMasteryAfter() - attempt.getMasteryBefore())
                .average().orElse(0.0);
        int reachedRound = 0;
        for (int index = 0; index < ordered.size(); index++) {
            Run run = ordered.get(index);
            double target = run.getEducationLearningGoalTarget() == null
                    ? 1.0 : run.getEducationLearningGoalTarget();
            if (attemptsByRun.getOrDefault(run.getId(), List.of()).stream()
                    .anyMatch(attempt -> attempt.getMasteryAfter() >= target)) {
                reachedRound = index + 1;
                break;
            }
        }
        List<EvidenceStats> evidence = ordered.stream().map(this::evidenceStats).toList();
        return new GoalStrategyOutcome(masteryGain, reachedRound,
                average(evidence, EvidenceStats::prerequisiteGapCoverage));
    }

    private String sampleStatusForPairs(long pairCount) {
        if (pairCount == 0) return "NO_DATA";
        return pairCount < MIN_RUNS_FOR_ANALYSIS ? "INSUFFICIENT_SAMPLE" : "ANALYSIS_READY";
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
        int evidenceChars = evidences.stream().mapToInt(this::evidenceChars).sum();
        double utility = breakdowns.stream()
                .mapToDouble(EducationRankingBreakdown::finalScore).sum();
        double marginalCoverage = breakdowns.stream()
                .mapToDouble(EducationRankingBreakdown::marginalCoverageScore).sum();
        return new EvidenceStats(evidences.size(), unique.size(), prerequisiteCoverage,
                ratio(evidences.size() - unique.size(), evidences.size()),
                averageBreakdown(breakdowns, EducationRankingBreakdown::finalScore),
                averageBreakdown(breakdowns, EducationRankingBreakdown::marginalCoverageScore),
                averageBreakdown(breakdowns, EducationRankingBreakdown::targetConceptMatch),
                graphCoverage,
                averageBreakdown(breakdowns, EducationRankingBreakdown::difficultyFit),
                evidenceChars, perThousandChars(utility, evidenceChars),
                perThousandChars(marginalCoverage, evidenceChars));
    }

    /** 证据摘录字符数是跨供应商 token 计数不可得时的稳定、可回放效率代理。 */
    private int evidenceChars(ContextEvidence evidence) {
        if (evidence == null) return 0;
        return safeLength(evidence.title()) + safeLength(evidence.excerpt()) + 10;
    }

    private int safeLength(String value) {
        return value == null ? 0 : value.length();
    }

    private double perThousandChars(double value, int chars) {
        return chars <= 0 ? 0.0 : value * 1000.0 / chars;
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
                                 double graphCoverage, double difficultyFit,
                                 double evidenceChars,
                                 double utilityPerThousandChars,
                                 double marginalCoveragePerThousandChars) {

        private static EvidenceStats empty() {
            return new EvidenceStats(0, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                    0.0, 0.0, 0.0);
        }

        private boolean hasEvidence() {
            return totalCount > 0;
        }
    }

    private record GoalStrategyOutcome(double masteryGain, int reachedRound,
                                       double prerequisiteGapCoverage) {
    }

    private static final class PairAccumulator {
        private long count;
        private double referenceMasteryGain;
        private double comparedMasteryGain;
        private long referenceReached;
        private long comparedReached;
        private double referenceRounds;
        private double comparedRounds;
        private long referenceRoundCount;
        private long comparedRoundCount;
        private double referencePrerequisiteGapCoverage;
        private double comparedPrerequisiteGapCoverage;

        private void add(GoalStrategyOutcome reference, GoalStrategyOutcome compared) {
            count++;
            referenceMasteryGain += reference.masteryGain();
            comparedMasteryGain += compared.masteryGain();
            referencePrerequisiteGapCoverage += reference.prerequisiteGapCoverage();
            comparedPrerequisiteGapCoverage += compared.prerequisiteGapCoverage();
            if (reference.reachedRound() > 0) {
                referenceReached++;
                referenceRounds += reference.reachedRound();
                referenceRoundCount++;
            }
            if (compared.reachedRound() > 0) {
                comparedReached++;
                comparedRounds += compared.reachedRound();
                comparedRoundCount++;
            }
        }

        private EducationExperimentPairView view(String referenceStrategy,
                                                 String comparedStrategy,
                                                 String sampleStatus) {
            double refGain = average(referenceMasteryGain, count);
            double cmpGain = average(comparedMasteryGain, count);
            double refReach = average(referenceReached, count);
            double cmpReach = average(comparedReached, count);
            double refRounds = average(referenceRounds, referenceRoundCount);
            double cmpRounds = average(comparedRounds, comparedRoundCount);
            double refGap = average(referencePrerequisiteGapCoverage, count);
            double cmpGap = average(comparedPrerequisiteGapCoverage, count);
            return new EducationExperimentPairView(referenceStrategy, comparedStrategy, count,
                    refGain, cmpGain, cmpGain - refGain,
                    refReach, cmpReach, cmpReach - refReach,
                    refRounds, cmpRounds, cmpRounds - refRounds,
                    refGap, cmpGap, cmpGap - refGap, sampleStatus);
        }

        private static double average(double total, long count) {
            return count <= 0 ? 0.0 : total / count;
        }
    }
}
