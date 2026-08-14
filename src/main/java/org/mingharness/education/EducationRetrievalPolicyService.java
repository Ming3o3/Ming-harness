package org.mingharness.education;

import org.mingharness.common.BusinessException;
import org.mingharness.education.api.EducationRetrievalPolicyCandidateView;
import org.mingharness.education.api.EducationRetrievalRunPolicyView;
import org.mingharness.runtime.domain.Run;
import org.mingharness.runtime.domain.RunStatus;
import org.mingharness.runtime.repository.RunRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 根据冻结学习状态和历史形成性结果选择下一轮教育检索策略。
 *
 * <p>结果统计只把成功且带形成性测评的 Run 纳入学习结果；均衡分配则额外记录所有
 * 已分配的匹配 Run，避免“没有测评的失败样本”被反复重新分配。候选统计、收缩分数
 * 和选择理由会冻结到新 Run；推荐结果也不替代随机对照实验。</p>
 */
@Service
public class EducationRetrievalPolicyService {

    private static final long MIN_RUNS_FOR_ANALYSIS = 5;
    private static final List<EducationRetrievalStrategy> CANDIDATES = List.of(
            EducationRetrievalStrategy.FULL,
            EducationRetrievalStrategy.CALIBRATED,
            EducationRetrievalStrategy.NO_DEPENDENCY_GRAPH,
            EducationRetrievalStrategy.STATIC_WEIGHT);

    private final RunRepository runRepository;
    private final AssessmentAttemptRepository assessmentRepository;
    private final EducationRetrievalCalibrationService calibrationService;

    public EducationRetrievalPolicyService(RunRepository runRepository,
                                           AssessmentAttemptRepository assessmentRepository,
                                           EducationRetrievalCalibrationService calibrationService) {
        this.runRepository = runRepository;
        this.assessmentRepository = assessmentRepository;
        this.calibrationService = calibrationService;
    }

    @Transactional(readOnly = true)
    public EducationRetrievalPolicySnapshot snapshotFor(String tenantId, String userId,
                                                        EducationRunConfiguration configuration) {
        if (configuration == null || !configuration.enabled()) {
            return EducationRetrievalPolicySnapshot.prior("UNKNOWN");
        }
        String conditioning = calibrationService.conditioningFor(configuration);
        if (runRepository == null || assessmentRepository == null
                || tenantId == null || tenantId.isBlank()) {
            return EducationRetrievalPolicySnapshot.prior(conditioning);
        }
        List<Run> runs = runRepository.findByTenantIdAndEducationModeTrueOrderByCreatedAtAsc(tenantId);
        List<AssessmentAttempt> attempts = assessmentRepository.findByTenantIdOrderByCreatedAtAsc(tenantId);
        if (runs == null || runs.isEmpty()) {
            return EducationRetrievalPolicySnapshot.prior(conditioning);
        }
        if (attempts == null) attempts = List.of();
        Map<String, List<AssessmentAttempt>> attemptsByRun = new LinkedHashMap<>();
        for (AssessmentAttempt attempt : attempts) {
            if (attempt == null || attempt.getAssessmentType() != AssessmentAttemptType.FORMATIVE) continue;
            attemptsByRun.computeIfAbsent(attempt.getRunId(), ignored -> new ArrayList<>()).add(attempt);
        }

        Map<String, OutcomeAccumulator> accumulators = new LinkedHashMap<>();
        for (EducationRetrievalStrategy candidate : CANDIDATES) {
            accumulators.put(candidate.name(), new OutcomeAccumulator());
        }
        for (Run run : runs) {
            if (!matchesConditioning(run, configuration, conditioning)) continue;
            EducationRetrievalStrategy strategy = effectiveHistoricalStrategy(run);
            OutcomeAccumulator accumulator = accumulators.get(strategy.name());
            if (accumulator == null) continue;
            accumulator.recordAllocation();
            if (run.getStatus() != RunStatus.SUCCEEDED) continue;
            List<AssessmentAttempt> runAttempts = attemptsByRun.getOrDefault(run.getId(), List.of());
            if (runAttempts.isEmpty()) continue;
            double target = run.getEducationLearningGoalTarget() == null
                    ? 1.0 : run.getEducationLearningGoalTarget();
            boolean reached = runAttempts.stream().anyMatch(item -> item.getMasteryAfter() >= target);
            accumulator.recordOutcome(runAttempts, reached);
        }

        List<EducationRetrievalPolicyCandidate> candidates = candidateViews(accumulators,
                configuration.retrievalStrategyValue() == EducationRetrievalStrategy.BALANCED_EXPERIMENT);
        long outcomeRuns = candidates.stream().mapToLong(EducationRetrievalPolicyCandidate::runCount).sum();
        long allocationRuns = candidates.stream().mapToLong(EducationRetrievalPolicyCandidate::allocationCount).sum();
        EducationRetrievalPolicyCandidate best;
        String reason;
        if (configuration.retrievalStrategyValue() == EducationRetrievalStrategy.BALANCED_EXPERIMENT) {
            best = candidates.stream()
                    .min(Comparator.comparingLong(EducationRetrievalPolicyCandidate::allocationCount)
                            .thenComparingInt(item -> candidateOrder(item.strategy())))
                    .orElse(null);
            if (best == null) best = candidateFor(EducationRetrievalStrategy.FULL, new OutcomeAccumulator());
            reason = "按状态条件下历史 Run 分配次数均衡选择 " + best.strategy()
                    + "（当前已分配 " + best.allocationCount() + " 次）";
        } else {
            best = candidates.stream()
                    .filter(item -> item.runCount() > 0)
                    .findFirst().orElse(null);
            if (best == null) {
                return new EducationRetrievalPolicySnapshot(EducationRetrievalPolicySnapshot.VERSION,
                        conditioning, EducationRetrievalStrategy.FULL.name(), 0,
                        "当前状态没有可用历史测评，回退 FULL", candidates, allocationRuns, null);
            }
            reason = best.runCount() < MIN_RUNS_FOR_ANALYSIS
                    ? "样本量不足，使用收缩后的候选分数选择 " + best.strategy()
                    : "按状态条件化学习结果选择收缩分数最高的 " + best.strategy();
        }
        return new EducationRetrievalPolicySnapshot(EducationRetrievalPolicySnapshot.VERSION,
                conditioning, best.strategy(), outcomeRuns, reason, candidates, allocationRuns, null);
    }

    @Transactional(readOnly = true)
    public String encodedSnapshotFor(String tenantId, String userId,
                                     EducationRunConfiguration configuration) {
        return EducationRetrievalPolicySnapshotCodec.encode(snapshotFor(tenantId, userId, configuration));
    }

    /** 将教师校准快照和策略选择快照绑定在同一个 Run 审计对象中，保证实际策略回放完整。 */
    @Transactional(readOnly = true)
    public String encodedSnapshotFor(String tenantId, String userId,
                                     EducationRunConfiguration configuration,
                                     String calibrationSnapshot) {
        return EducationRetrievalPolicySnapshotCodec.encode(
                snapshotFor(tenantId, userId, configuration).withCalibrationSnapshot(calibrationSnapshot));
    }

    /** 给学生/教师面板展示当前用户最近一次教育状态下的推荐，不创建新 Run。 */
    @Transactional(readOnly = true)
    public EducationRetrievalPolicySnapshot snapshotForLatestRun(String tenantId, String userId) {
        if (runRepository == null || tenantId == null || tenantId.isBlank()
                || userId == null || userId.isBlank()) {
            return EducationRetrievalPolicySnapshot.prior("UNKNOWN");
        }
        List<Run> runs = runRepository.findByTenantIdAndUserIdAndEducationModeTrueOrderByCreatedAtAsc(
                tenantId, userId);
        if (runs == null || runs.isEmpty()) return EducationRetrievalPolicySnapshot.prior("UNKNOWN");
        Run latest = runs.get(runs.size() - 1);
        return snapshotFor(tenantId, userId, latest.educationConfiguration());
    }

    /**
     * 返回单次 Run 创建时冻结的策略选择，而不是重新根据当前历史数据计算推荐。
     * 这样教师复核或论文实验回放时，可以区分“当时实际执行的策略”和“现在重新计算出的推荐”。
     */
    @Transactional(readOnly = true)
    public EducationRetrievalRunPolicyView viewForRun(String tenantId, String userId,
                                                       String runId, boolean elevated) {
        Run run = runRepository.findById(runId)
                .filter(item -> tenantId != null && tenantId.equals(item.getTenantId()))
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "RUN_NOT_FOUND", "Run 不存在"));
        if (!elevated && (userId == null || !userId.equals(run.getUserId()))) {
            throw new BusinessException(HttpStatus.FORBIDDEN,
                    "EDUCATION_RETRIEVAL_POLICY_ACCESS_DENIED", "无权查看该 Run 的检索策略快照");
        }
        if (!run.isEducationMode()) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "EDUCATION_RETRIEVAL_POLICY_REQUIRES_EDUCATION_RUN",
                    "只有教育 Run 才有检索策略快照");
        }

        EducationRetrievalStrategy requested = EducationRetrievalStrategy.parse(
                run.getEducationRetrievalStrategy());
        String conditioning = calibrationService.conditioningFor(run);
        if (requested != EducationRetrievalStrategy.ADAPTIVE
                && requested != EducationRetrievalStrategy.BALANCED_EXPERIMENT) {
            String snapshotType = requested == EducationRetrievalStrategy.CALIBRATED
                    ? "CALIBRATED_WEIGHTS" : "NONE";
            String snapshotVersion = requested == EducationRetrievalStrategy.CALIBRATED
                    ? EducationRetrievalCalibrationSnapshot.VERSION : "";
            boolean frozen = run.getEducationRetrievalWeights() != null
                    && !run.getEducationRetrievalWeights().isBlank();
            return new EducationRetrievalRunPolicyView(run.getId(), requested.name(), requested.name(),
                    frozen, snapshotType, snapshotVersion, conditioning, 0,
                    requested == EducationRetrievalStrategy.CALIBRATED
                            ? "Run 创建时冻结了教师校准权重" : "Run 使用固定检索策略，无自适应候选选择",
                    List.of());
        }

        EducationRetrievalPolicySnapshot snapshot = EducationRetrievalPolicySnapshotCodec.decode(
                run.getEducationRetrievalWeights());
        List<EducationRetrievalPolicyCandidateView> candidates = snapshot.candidates().stream()
                .map(EducationRetrievalPolicyCandidateView::from).toList();
        return new EducationRetrievalRunPolicyView(run.getId(), requested.name(), snapshot.selectedStrategy(),
                run.getEducationRetrievalWeights() != null && !run.getEducationRetrievalWeights().isBlank(),
                requested == EducationRetrievalStrategy.BALANCED_EXPERIMENT
                        ? "BALANCED_POLICY" : "ADAPTIVE_POLICY",
                snapshot.version(), snapshot.conditioning(), snapshot.eligibleRunCount(),
                snapshot.selectionReason(), candidates);
    }

    public EducationRetrievalStrategy effectiveStrategy(String requestedStrategy, String encodedSnapshot) {
        EducationRetrievalStrategy requested = EducationRetrievalStrategy.parse(requestedStrategy);
        if (requested != EducationRetrievalStrategy.ADAPTIVE
                && requested != EducationRetrievalStrategy.BALANCED_EXPERIMENT) return requested;
        return EducationRetrievalStrategy.parse(
                EducationRetrievalPolicySnapshotCodec.decode(encodedSnapshot).selectedStrategy());
    }

    private boolean matchesConditioning(Run run, EducationRunConfiguration configuration, String conditioning) {
        if (run == null || !run.isEducationMode()
                || !same(run.getEducationSubject(), configuration.subject())
                || !same(run.getEducationGradeLevel(), configuration.gradeLevel())
                || !same(run.getEducationCurriculumVersion(), configuration.curriculumVersion())
                || !same(run.getEducationConceptKey(), configuration.conceptKey())) {
            return false;
        }
        return conditioning.equals(calibrationService.conditioningFor(run));
    }

    private EducationRetrievalStrategy effectiveHistoricalStrategy(Run run) {
        EducationRetrievalStrategy requested = EducationRetrievalStrategy.parse(
                run.getEducationRetrievalStrategy());
        if (requested != EducationRetrievalStrategy.ADAPTIVE
                && requested != EducationRetrievalStrategy.BALANCED_EXPERIMENT) {
            return requested;
        }
        EducationRetrievalStrategy selected = EducationRetrievalStrategy.parse(
                EducationRetrievalPolicySnapshotCodec.decode(run.getEducationRetrievalWeights())
                        .selectedStrategy());
        return CANDIDATES.contains(selected) ? selected : EducationRetrievalStrategy.FULL;
    }

    private List<EducationRetrievalPolicyCandidate> candidateViews(
            Map<String, OutcomeAccumulator> accumulators, boolean allocationOrder) {
        java.util.stream.Stream<EducationRetrievalPolicyCandidate> stream = accumulators.entrySet().stream()
                .map(entry -> entry.getValue().candidate(entry.getKey()));
        if (!allocationOrder) {
            stream = stream.sorted(Comparator.comparingDouble(EducationRetrievalPolicyCandidate::adjustedScore)
                    .reversed().thenComparingInt(item -> candidateOrder(item.strategy())));
        }
        return stream.toList();
    }

    private EducationRetrievalPolicyCandidate candidateFor(EducationRetrievalStrategy strategy,
                                                            OutcomeAccumulator accumulator) {
        return accumulator.candidate(strategy.name());
    }

    private int candidateOrder(String strategy) {
        int index = CANDIDATES.indexOf(EducationRetrievalStrategy.parse(strategy));
        return index < 0 ? Integer.MAX_VALUE : index;
    }

    private boolean same(String left, String right) {
        if (left == null || right == null) return left == null && right == null;
        return left.trim().equalsIgnoreCase(right.trim());
    }

    private static final class OutcomeAccumulator {
        private long allocationCount;
        private long runCount;
        private long assessmentCount;
        private long correctCount;
        private long reachedRunCount;
        private double gainSum;

        private void recordAllocation() {
            allocationCount++;
        }

        private void recordOutcome(List<AssessmentAttempt> attempts, boolean reached) {
            runCount++;
            if (reached) reachedRunCount++;
            for (AssessmentAttempt attempt : attempts) {
                assessmentCount++;
                gainSum += attempt.getMasteryAfter() - attempt.getMasteryBefore();
                if (attempt.isCorrect()) correctCount++;
            }
        }

        private EducationRetrievalPolicyCandidate candidate(String strategy) {
            double gain = assessmentCount == 0 ? 0.0 : gainSum / assessmentCount;
            double accuracy = assessmentCount == 0 ? 0.0 : correctCount / (double) assessmentCount;
            double reach = runCount == 0 ? 0.0 : reachedRunCount / (double) runCount;
            double score = 0.5 * clamp(0.5 + gain) + 0.3 * accuracy + 0.2 * reach;
            double confidence = runCount == 0 ? 0.0 : runCount / (double) (runCount + 5.0);
            double adjusted = 0.5 + confidence * (score - 0.5);
            String status = runCount == 0 ? "NO_DATA"
                    : runCount < MIN_RUNS_FOR_ANALYSIS ? "INSUFFICIENT_SAMPLE" : "ANALYSIS_READY";
            return new EducationRetrievalPolicyCandidate(strategy, runCount, assessmentCount,
                    gain, accuracy, reach, score, confidence, adjusted, status, allocationCount);
        }

        private double clamp(double value) {
            return Math.max(0.0, Math.min(1.0, value));
        }
    }
}
