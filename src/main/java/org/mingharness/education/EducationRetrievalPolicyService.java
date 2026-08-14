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
 * <p>该服务只读取已经完成的 Run，并把候选统计、收缩分数和选择理由冻结到新 Run；
 * 小样本不会直接把一次偶然高分当成策略结论，推荐结果也不替代随机对照实验。</p>
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
        if (runs == null || attempts == null || runs.isEmpty() || attempts.isEmpty()) {
            return EducationRetrievalPolicySnapshot.prior(conditioning);
        }
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
            if (!eligible(run, configuration, conditioning)) continue;
            EducationRetrievalStrategy strategy = EducationRetrievalStrategy.parse(
                    run.getEducationRetrievalStrategy());
            OutcomeAccumulator accumulator = accumulators.get(strategy.name());
            if (accumulator == null) continue;
            List<AssessmentAttempt> runAttempts = attemptsByRun.getOrDefault(run.getId(), List.of());
            if (runAttempts.isEmpty()) continue;
            double target = run.getEducationLearningGoalTarget() == null
                    ? 1.0 : run.getEducationLearningGoalTarget();
            boolean reached = runAttempts.stream().anyMatch(item -> item.getMasteryAfter() >= target);
            accumulator.add(runAttempts, reached);
        }

        List<EducationRetrievalPolicyCandidate> candidates = accumulators.entrySet().stream()
                .map(entry -> entry.getValue().candidate(entry.getKey()))
                .sorted(Comparator.comparingDouble(EducationRetrievalPolicyCandidate::adjustedScore).reversed()
                        .thenComparing(EducationRetrievalPolicyCandidate::strategy))
                .toList();
        EducationRetrievalPolicyCandidate best = candidates.stream().findFirst().orElse(null);
        if (best == null || best.runCount() == 0) {
            return new EducationRetrievalPolicySnapshot(EducationRetrievalPolicySnapshot.VERSION,
                    conditioning, EducationRetrievalStrategy.FULL.name(), 0,
                    "当前状态没有可用历史测评，回退 FULL", candidates);
        }
        String reason = best.runCount() < MIN_RUNS_FOR_ANALYSIS
                ? "样本量不足，使用收缩后的候选分数选择 " + best.strategy()
                : "按状态条件化学习结果选择收缩分数最高的 " + best.strategy();
        long eligibleRuns = candidates.stream().mapToLong(EducationRetrievalPolicyCandidate::runCount).sum();
        return new EducationRetrievalPolicySnapshot(EducationRetrievalPolicySnapshot.VERSION,
                conditioning, best.strategy(), eligibleRuns, reason, candidates);
    }

    @Transactional(readOnly = true)
    public String encodedSnapshotFor(String tenantId, String userId,
                                     EducationRunConfiguration configuration) {
        return EducationRetrievalPolicySnapshotCodec.encode(snapshotFor(tenantId, userId, configuration));
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
        if (requested != EducationRetrievalStrategy.ADAPTIVE) {
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
                "ADAPTIVE_POLICY", snapshot.version(), snapshot.conditioning(), snapshot.eligibleRunCount(),
                snapshot.selectionReason(), candidates);
    }

    public EducationRetrievalStrategy effectiveStrategy(String requestedStrategy, String encodedSnapshot) {
        EducationRetrievalStrategy requested = EducationRetrievalStrategy.parse(requestedStrategy);
        if (requested != EducationRetrievalStrategy.ADAPTIVE) return requested;
        return EducationRetrievalStrategy.parse(
                EducationRetrievalPolicySnapshotCodec.decode(encodedSnapshot).selectedStrategy());
    }

    private boolean eligible(Run run, EducationRunConfiguration configuration, String conditioning) {
        if (run == null || run.getStatus() != RunStatus.SUCCEEDED || !run.isEducationMode()
                || !same(run.getEducationSubject(), configuration.subject())
                || !same(run.getEducationGradeLevel(), configuration.gradeLevel())
                || !same(run.getEducationCurriculumVersion(), configuration.curriculumVersion())
                || !same(run.getEducationConceptKey(), configuration.conceptKey())) {
            return false;
        }
        return conditioning.equals(calibrationService.conditioningFor(run));
    }

    private boolean same(String left, String right) {
        if (left == null || right == null) return left == null && right == null;
        return left.trim().equalsIgnoreCase(right.trim());
    }

    private static final class OutcomeAccumulator {
        private long runCount;
        private long assessmentCount;
        private long correctCount;
        private long reachedRunCount;
        private double gainSum;

        private void add(List<AssessmentAttempt> attempts, boolean reached) {
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
                    gain, accuracy, reach, score, confidence, adjusted, status);
        }

        private double clamp(double value) {
            return Math.max(0.0, Math.min(1.0, value));
        }
    }
}
