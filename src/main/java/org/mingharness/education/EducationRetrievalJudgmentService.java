package org.mingharness.education;

import org.mingharness.common.BusinessException;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.context.ContextEvidenceSnapshotCodec;
import org.mingharness.context.api.ContextEvidence;
import org.mingharness.education.api.EducationRetrievalJudgmentRequest;
import org.mingharness.education.api.EducationRetrievalJudgmentView;
import org.mingharness.runtime.domain.Run;
import org.mingharness.runtime.domain.RunStatus;
import org.mingharness.runtime.domain.Step;
import org.mingharness.runtime.repository.RunRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 管理证据级教师标注，为后续排序权重校准提供可审计训练事实。
 *
 * <p>服务端从 Run 快照解析 documentId，不信任请求体自报来源；同一评价者可以
 * 对同一条证据提交多次带版本的判断，历史记录保持不可变，校准器再按时间和量规
 * 版本选择样本。</p>
 */
@Service
public class EducationRetrievalJudgmentService {

    private final RunRepository runRepository;
    private final EducationRetrievalJudgmentRepository judgmentRepository;
    private final SensitiveDataSanitizer sanitizer;

    public EducationRetrievalJudgmentService(RunRepository runRepository,
                                             EducationRetrievalJudgmentRepository judgmentRepository,
                                             SensitiveDataSanitizer sanitizer) {
        this.runRepository = runRepository;
        this.judgmentRepository = judgmentRepository;
        this.sanitizer = sanitizer;
    }

    @Transactional
    public EducationRetrievalJudgmentView submit(String tenantId, String evaluatorUserId,
                                                 String runId,
                                                 EducationRetrievalJudgmentRequest request) {
        Run run = requireRun(tenantId, runId);
        requireEvaluableRun(run);
        if (request == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "EDUCATION_RETRIEVAL_JUDGMENT_REQUIRED", "证据标注不能为空");
        }
        validateScore(request.targetGroundingScore(), "目标 grounding");
        validateScore(request.prerequisiteUtilityScore(), "前置补强");
        validateScore(request.difficultyFitScore(), "难度适配");
        validateScore(request.overallUtilityScore(), "总体效用");
        EvidenceReference evidence = findEvidence(run, request.stepId(), request.evidenceCitation());
        EducationRetrievalJudgment judgment = judgmentRepository.save(new EducationRetrievalJudgment(
                tenantId, runId, evidence.stepId(), evaluatorUserId, evidence.evidence().documentId(),
                evidence.evidence().citation(), request.targetGroundingScore(),
                request.prerequisiteUtilityScore(), request.difficultyFitScore(),
                request.overallUtilityScore(), cleanNullable(request.note()), Instant.now()));
        return EducationRetrievalJudgmentView.from(judgment);
    }

    @Transactional(readOnly = true)
    public List<EducationRetrievalJudgmentView> listForRun(String tenantId, String userId,
                                                           String runId, boolean canEvaluate,
                                                           boolean governance) {
        Run run = requireRun(tenantId, runId);
        if (!governance && !canEvaluate && !userId.equals(run.getUserId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN,
                    "EDUCATION_RETRIEVAL_JUDGMENT_ACCESS_DENIED", "无权查看该 Run 的证据标注");
        }
        return judgmentRepository.findByTenantIdAndRunIdOrderByCreatedAtAsc(tenantId, runId)
                .stream().map(org.mingharness.education.api.EducationRetrievalJudgmentView::from).toList();
    }

    /** 校准器使用的租户范围事实出口；调用方必须已经通过治理权限检查。 */
    @Transactional(readOnly = true)
    public List<EducationRetrievalJudgment> listForCalibration(String tenantId) {
        return judgmentRepository.findByTenantIdOrderByCreatedAtAsc(tenantId);
    }

    private Run requireRun(String tenantId, String runId) {
        return runRepository.findById(runId)
                .filter(run -> tenantId.equals(run.getTenantId()))
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "RUN_NOT_FOUND", "Run 不存在"));
    }

    private void requireEvaluableRun(Run run) {
        if (!run.isEducationMode()) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "EDUCATION_RETRIEVAL_JUDGMENT_REQUIRES_EDUCATION_RUN",
                    "只有教育 Run 可以进行证据标注");
        }
        if (run.getStatus() != RunStatus.SUCCEEDED) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "EDUCATION_RETRIEVAL_JUDGMENT_RUN_NOT_READY",
                    "只有成功完成的教育 Run 可以进行证据标注");
        }
    }

    private EvidenceReference findEvidence(Run run, String requestedStepId, String citation) {
        String requestedCitation = cleanRequired(citation, "evidenceCitation");
        String stepId = cleanNullable(requestedStepId);
        for (Step step : run.getSteps() == null ? List.<Step>of() : run.getSteps()) {
            if (step == null || stepId != null && !stepId.equals(step.getId())) continue;
            for (ContextEvidence evidence : ContextEvidenceSnapshotCodec.decode(step.getContextEvidenceJson())) {
                if (requestedCitation.equals(evidence.citation())) {
                    return new EvidenceReference(step.getId(), evidence);
                }
            }
        }
        throw new BusinessException(HttpStatus.BAD_REQUEST,
                "EDUCATION_RETRIEVAL_EVIDENCE_NOT_FOUND",
                "证据引用不在该 Run 的授权检索快照中");
    }

    private String cleanRequired(String value, String name) {
        String cleaned = sanitizer.sanitize(value == null ? "" : value.trim());
        if (cleaned.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "EDUCATION_RETRIEVAL_JUDGMENT_FIELD_REQUIRED", name + " 不能为空");
        }
        return cleaned;
    }

    private void validateScore(Integer value, String label) {
        if (value == null || value < 1 || value > 5) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "EDUCATION_RETRIEVAL_JUDGMENT_SCORE_INVALID",
                    label + "评分必须在 1 到 5 之间");
        }
    }

    private String cleanNullable(String value) {
        String cleaned = sanitizer.sanitize(value == null ? "" : value.trim());
        return cleaned.isBlank() ? null : cleaned;
    }

    private record EvidenceReference(String stepId, ContextEvidence evidence) {
    }
}
