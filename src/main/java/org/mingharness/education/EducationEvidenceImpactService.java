package org.mingharness.education;

import org.mingharness.context.ContextEvidenceSnapshotCodec;
import org.mingharness.context.api.ContextEvidence;
import org.mingharness.context.api.EducationRankingBreakdown;
import org.mingharness.education.api.AssessmentEvidenceReference;
import org.mingharness.education.api.EducationEvidenceImpactSummaryView;
import org.mingharness.education.api.EducationEvidenceImpactView;
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
import java.util.List;
import java.util.Map;

/**
 * 将形成性测评的冻结检索引用与掌握度变化做证据级归因。
 *
 * <p>归因是可解释的描述性统计：一次测评的增益按其引用数量分摊给各来源，不能被
 * 解释为单个文档的因果贡献。只有成功教育 Run 且能在 Step 快照中重新找到引用时，
 * 才会计算排序拆解指标；历史引用仍保留在结果中并标记较低的快照匹配率。</p>
 */
@Service
public class EducationEvidenceImpactService {

    private static final long MIN_SAMPLES_FOR_ANALYSIS = 30;

    private final RunRepository runRepository;
    private final AssessmentAttemptRepository assessmentRepository;

    public EducationEvidenceImpactService(RunRepository runRepository,
                                          AssessmentAttemptRepository assessmentRepository) {
        this.runRepository = runRepository;
        this.assessmentRepository = assessmentRepository;
    }

    @Transactional(readOnly = true)
    public EducationEvidenceImpactSummaryView summarize(String tenantId, String userId,
                                                        boolean tenantScope) {
        List<Run> runs = tenantScope
                ? runRepository.findByTenantIdAndEducationModeTrueOrderByCreatedAtAsc(tenantId)
                : runRepository.findByTenantIdAndUserIdAndEducationModeTrueOrderByCreatedAtAsc(
                tenantId, userId);
        if (runs == null) runs = List.of();
        List<AssessmentAttempt> attempts = tenantScope
                ? assessmentRepository.findByTenantIdOrderByCreatedAtAsc(tenantId)
                : assessmentRepository.findByTenantIdAndUserIdOrderByCreatedAtAsc(tenantId, userId);
        if (attempts == null) attempts = List.of();

        Map<String, Run> successfulRuns = new HashMap<>();
        Map<String, SnapshotIndex> snapshotIndexes = new HashMap<>();
        for (Run run : runs) {
            if (run != null && run.getStatus() == RunStatus.SUCCEEDED) {
                successfulRuns.put(run.getId(), run);
                snapshotIndexes.put(run.getId(), snapshotIndex(run));
            }
        }

        Map<String, ImpactAccumulator> accumulators = new LinkedHashMap<>();
        long totalAssessments = 0;
        long assessmentsWithEvidence = 0;
        long assessmentsWithMatchedEvidence = 0;
        long referenceCount = 0;
        long matchedReferenceCount = 0;

        for (AssessmentAttempt attempt : attempts) {
            if (attempt == null || attempt.getAssessmentType() != AssessmentAttemptType.FORMATIVE) continue;
            Run run = successfulRuns.get(attempt.getRunId());
            if (run == null) continue;
            totalAssessments++;
            List<AssessmentEvidenceReference> references = uniqueReferences(
                    EducationRetrievalEvidence.decode(attempt.getRetrievalEvidenceJson()));
            if (references.isEmpty()) continue;
            assessmentsWithEvidence++;
            referenceCount += references.size();
            double attributionWeight = 1.0 / references.size();
            SnapshotIndex snapshotIndex = snapshotIndexes.get(run.getId());
            boolean matchedForAssessment = false;
            String strategy = effectiveExperimentStrategy(run);
            double gain = attempt.getMasteryAfter() - attempt.getMasteryBefore();
            for (AssessmentEvidenceReference reference : references) {
                ContextEvidence snapshot = snapshotIndex == null ? null
                        : snapshotIndex.byCitation().get(reference.citation());
                if (snapshot == null && reference.documentId() != null && !reference.documentId().isBlank()) {
                    snapshot = snapshotIndex == null ? null
                            : snapshotIndex.byDocument().get(reference.documentId());
                }
                boolean matched = snapshot != null;
                if (matched) {
                    matchedForAssessment = true;
                    matchedReferenceCount++;
                }
                String evidenceKey = referenceKey(reference);
                String key = strategy + "\u0000" + evidenceKey;
                accumulators.computeIfAbsent(key, ignored -> new ImpactAccumulator(
                                strategy, reference.documentId(), reference.title(), reference.citation()))
                        .add(attributionWeight, gain, attempt.isCorrect(), snapshot, matched);
            }
            if (matchedForAssessment) assessmentsWithMatchedEvidence++;
        }

        List<EducationEvidenceImpactView> impacts = accumulators.values().stream()
                .map(ImpactAccumulator::view)
                .sorted(Comparator.comparingDouble(EducationEvidenceImpactView::attributedAssessmentWeight)
                        .reversed()
                        .thenComparing(Comparator.comparingDouble(
                                EducationEvidenceImpactView::attributedMasteryGain).reversed())
                        .thenComparing(EducationEvidenceImpactView::retrievalStrategy)
                        .thenComparing(EducationEvidenceImpactView::citation))
                .toList();
        double linkRate = ratio(assessmentsWithEvidence, totalAssessments);
        double snapshotRate = ratio(matchedReferenceCount, referenceCount);
        return new EducationEvidenceImpactSummaryView(Instant.now(), tenantScope,
                totalAssessments, assessmentsWithEvidence, assessmentsWithMatchedEvidence,
                referenceCount, matchedReferenceCount, linkRate, snapshotRate,
                sampleStatus(totalAssessments), impacts);
    }

    @Transactional(readOnly = true)
    public String exportCsv(String tenantId, String userId, boolean tenantScope) {
        EducationEvidenceImpactSummaryView summary = summarize(tenantId, userId, tenantScope);
        StringBuilder csv = new StringBuilder();
        csv.append("retrieval_strategy,document_id,title,citation,evidence_reference_count,"
                + "attributed_assessment_weight,attributed_mastery_gain,average_mastery_gain,"
                + "attributed_correct_rate,average_ranking_score,average_marginal_coverage,"
                + "average_target_concept_match,average_graph_coverage,average_difficulty_fit,"
                + "snapshot_match_rate,sample_status\n");
        for (EducationEvidenceImpactView item : summary.impacts()) {
            csv.append(csv(item.retrievalStrategy())).append(',')
                    .append(csv(item.documentId())).append(',')
                    .append(csv(item.title())).append(',')
                    .append(csv(item.citation())).append(',')
                    .append(item.evidenceReferenceCount()).append(',')
                    .append(item.attributedAssessmentWeight()).append(',')
                    .append(item.attributedMasteryGain()).append(',')
                    .append(item.averageMasteryGain()).append(',')
                    .append(item.attributedCorrectRate()).append(',')
                    .append(item.averageRankingScore()).append(',')
                    .append(item.averageMarginalCoverage()).append(',')
                    .append(item.averageTargetConceptMatch()).append(',')
                    .append(item.averageGraphCoverage()).append(',')
                    .append(item.averageDifficultyFit()).append(',')
                    .append(item.snapshotMatchRate()).append(',')
                    .append(csv(item.sampleStatus())).append('\n');
        }
        return csv.toString();
    }

    private List<AssessmentEvidenceReference> uniqueReferences(
            List<AssessmentEvidenceReference> references) {
        if (references == null || references.isEmpty()) return List.of();
        Map<String, AssessmentEvidenceReference> unique = new LinkedHashMap<>();
        for (AssessmentEvidenceReference reference : references) {
            if (reference == null) continue;
            String key = referenceKey(reference);
            if (!key.isBlank()) unique.putIfAbsent(key, reference);
        }
        return List.copyOf(unique.values());
    }

    private SnapshotIndex snapshotIndex(Run run) {
        Map<String, ContextEvidence> byCitation = new LinkedHashMap<>();
        Map<String, ContextEvidence> byDocument = new LinkedHashMap<>();
        for (ContextEvidence evidence : snapshot(run)) {
            if (evidence.citation() != null && !evidence.citation().isBlank()) {
                byCitation.putIfAbsent(evidence.citation(), evidence);
            }
            if (evidence.documentId() != null && !evidence.documentId().isBlank()) {
                byDocument.putIfAbsent(evidence.documentId(), evidence);
            }
        }
        return new SnapshotIndex(byCitation, byDocument);
    }

    private List<ContextEvidence> snapshot(Run run) {
        if (run == null || run.getSteps() == null) return List.of();
        List<ContextEvidence> result = new ArrayList<>();
        for (Step step : run.getSteps()) {
            if (step == null) continue;
            result.addAll(ContextEvidenceSnapshotCodec.decode(step.getContextEvidenceJson()));
        }
        return result;
    }

    private String referenceKey(AssessmentEvidenceReference reference) {
        if (reference == null) return "";
        if (reference.citation() != null && !reference.citation().isBlank()) {
            return reference.citation().trim();
        }
        return reference.documentId() == null ? "" : reference.documentId().trim();
    }

    private String sampleStatus(long count) {
        if (count == 0) return "NO_DATA";
        return count < MIN_SAMPLES_FOR_ANALYSIS ? "INSUFFICIENT_SAMPLE" : "ANALYSIS_READY";
    }

    /** 分配器 Run 的证据指标归入真实执行方法，避免 BALANCED_EXPERIMENT 污染方法比较。 */
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
        return switch (effective) {
            case FULL, VECTOR_ONLY, KEYWORD_ONLY, NO_LEARNER_STATE, NO_STATE_NO_GRAPH,
                    NO_DEPENDENCY_GRAPH, STATIC_WEIGHT, CALIBRATED -> effective.name();
            default -> EducationRetrievalStrategy.FULL.name();
        };
    }

    private double ratio(long numerator, long denominator) {
        return denominator <= 0 ? 0.0 : numerator / (double) denominator;
    }

    private String csv(String value) {
        String safe = value == null ? "" : value.replace("\"", "\"\"");
        return "\"" + safe + "\"";
    }

    private record SnapshotIndex(Map<String, ContextEvidence> byCitation,
                                 Map<String, ContextEvidence> byDocument) {
    }

    private static final class ImpactAccumulator {
        private final String strategy;
        private final String documentId;
        private final String title;
        private final String citation;
        private long evidenceReferenceCount;
        private double attributedAssessmentWeight;
        private double attributedMasteryGain;
        private double attributedCorrectRate;
        private double matchedWeight;
        private double rankingScore;
        private double marginalCoverage;
        private double targetConceptMatch;
        private double graphCoverage;
        private double difficultyFit;

        private ImpactAccumulator(String strategy, String documentId, String title, String citation) {
            this.strategy = safe(strategy);
            this.documentId = safe(documentId);
            this.title = safe(title);
            this.citation = safe(citation);
        }

        private void add(double weight, double gain, boolean correct,
                         ContextEvidence snapshot, boolean matched) {
            evidenceReferenceCount++;
            attributedAssessmentWeight += weight;
            attributedMasteryGain += weight * gain;
            if (correct) attributedCorrectRate += weight;
            if (!matched || snapshot == null) return;
            matchedWeight += weight;
            EducationRankingBreakdown breakdown = snapshot.rankingBreakdown();
            rankingScore += weight * breakdown.finalScore();
            marginalCoverage += weight * breakdown.marginalCoverageScore();
            targetConceptMatch += weight * breakdown.targetConceptMatch();
            graphCoverage += weight * breakdown.graphCoverage();
            difficultyFit += weight * breakdown.difficultyFit();
        }

        private EducationEvidenceImpactView view() {
            double averageGain = divide(attributedMasteryGain, attributedAssessmentWeight);
            return new EducationEvidenceImpactView(strategy, documentId, title, citation,
                    evidenceReferenceCount, attributedAssessmentWeight, attributedMasteryGain,
                    averageGain, divide(attributedCorrectRate, attributedAssessmentWeight),
                    divide(rankingScore, matchedWeight), divide(marginalCoverage, matchedWeight),
                    divide(targetConceptMatch, matchedWeight), divide(graphCoverage, matchedWeight),
                    divide(difficultyFit, matchedWeight),
                    divide(matchedWeight, attributedAssessmentWeight),
                    sampleStatus(attributedAssessmentWeight));
        }

        private double divide(double numerator, double denominator) {
            return denominator <= 0.0 ? 0.0 : numerator / denominator;
        }

        private String sampleStatus(double count) {
            if (count <= 0.0) return "NO_DATA";
            return count < MIN_SAMPLES_FOR_ANALYSIS ? "INSUFFICIENT_SAMPLE" : "ANALYSIS_READY";
        }

        private static String safe(String value) {
            return value == null ? "" : value.trim();
        }
    }
}
