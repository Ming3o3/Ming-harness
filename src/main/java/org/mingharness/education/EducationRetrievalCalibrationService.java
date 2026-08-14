package org.mingharness.education;

import org.mingharness.context.api.EducationRankingWeights;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将教师对真实 Run 证据的评价聚合为下一轮教育检索使用的权重快照。
 *
 * <p>同一评价者对同一 Run 证据的重复提交只保留最新记录，防止 UI 重试把一个样本
 * 放大；权重本身仍向固定先验收缩，低样本量时不会破坏既有排序行为。</p>
 */
@Service
public class EducationRetrievalCalibrationService {

    private final EducationRetrievalJudgmentRepository judgmentRepository;

    public EducationRetrievalCalibrationService(EducationRetrievalJudgmentRepository judgmentRepository) {
        this.judgmentRepository = judgmentRepository;
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

        double target = latest.values().stream()
                .mapToInt(EducationRetrievalJudgment::getTargetGroundingScore).average().orElse(3.0);
        double prerequisite = latest.values().stream()
                .mapToInt(EducationRetrievalJudgment::getPrerequisiteUtilityScore).average().orElse(3.0);
        double difficulty = latest.values().stream()
                .mapToInt(EducationRetrievalJudgment::getDifficultyFitScore).average().orElse(3.0);
        double overall = latest.values().stream()
                .mapToInt(EducationRetrievalJudgment::getOverallUtilityScore).average().orElse(3.0);
        long sampleCount = latest.size();
        EducationRankingWeights weights = EducationRankingWeights.calibrated(
                target, prerequisite, difficulty, overall, sampleCount);
        return new EducationRetrievalCalibrationSnapshot(
                EducationRetrievalCalibrationSnapshot.VERSION, sampleCount,
                target, prerequisite, difficulty, overall, weights);
    }

    @Transactional(readOnly = true)
    public String encodedSnapshotForTenant(String tenantId) {
        return EducationRetrievalCalibrationSnapshotCodec.encode(snapshotForTenant(tenantId));
    }

    public EducationRankingWeights weightsFromSnapshot(String encodedSnapshot) {
        return EducationRetrievalCalibrationSnapshotCodec.decode(encodedSnapshot).weights();
    }

    private boolean after(Instant candidate, Instant previous) {
        if (candidate == null) return false;
        return previous == null || candidate.isAfter(previous);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
