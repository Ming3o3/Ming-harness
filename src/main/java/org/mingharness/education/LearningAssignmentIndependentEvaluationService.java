package org.mingharness.education;

import org.mingharness.common.BusinessException;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.education.api.LearningAssignmentEvaluationConsensusView;
import org.mingharness.education.api.LearningAssignmentEvaluationRequest;
import org.mingharness.education.api.LearningAssignmentEvaluationView;
import org.mingharness.education.api.LearningAssignmentView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/** 管理第二评分者评价，不直接改变教师确认和学习者作业状态。 */
@Service
public class LearningAssignmentIndependentEvaluationService {

    private static final double CONSENSUS_MAX_SCORE_DIFFERENCE = 1.0;

    private final LearningAssignmentRepository assignmentRepository;
    private final LearningAssignmentEvaluationRepository evaluationRepository;
    private final SensitiveDataSanitizer sanitizer;

    public LearningAssignmentIndependentEvaluationService(
            LearningAssignmentRepository assignmentRepository,
            LearningAssignmentEvaluationRepository evaluationRepository,
            SensitiveDataSanitizer sanitizer) {
        this.assignmentRepository = assignmentRepository;
        this.evaluationRepository = evaluationRepository;
        this.sanitizer = sanitizer;
    }

    @Transactional(readOnly = true)
    public List<LearningAssignmentView> queue(String tenantId, String evaluatorUserId) {
        return assignmentRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(
                        tenantId, LearningAssignmentStatus.COMPLETED).stream()
                .filter(assignment -> !evaluatorUserId.equals(assignment.getTeacherUserId()))
                .filter(assignment -> !evaluationRepository
                        .existsByTenantIdAndLearningAssignmentIdAndEvaluatorUserIdAndDecision(
                                tenantId, assignment.getId(), evaluatorUserId,
                                LearningAssignmentEvaluationDecision.INDEPENDENT))
                .map(LearningAssignmentView::from)
                .toList();
    }

    @Transactional
    public LearningAssignmentEvaluationView evaluate(String tenantId, String evaluatorUserId,
                                                      String assignmentId,
                                                      LearningAssignmentEvaluationRequest request) {
        LearningAssignment assignment = find(tenantId, assignmentId);
        if (evaluatorUserId.equals(assignment.getTeacherUserId())) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "LEARNING_ASSIGNMENT_INDEPENDENT_EVALUATOR_REQUIRED",
                    "独立评分者不能与布置教师相同");
        }
        if (assignment.getStatus() != LearningAssignmentStatus.COMPLETED) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "LEARNING_ASSIGNMENT_INDEPENDENT_EVALUATION_NOT_READY",
                    "只有已完成的课程作业可以进行独立评价");
        }
        validate(request);
        if (evaluationRepository.existsByTenantIdAndLearningAssignmentIdAndEvaluatorUserIdAndDecision(
                tenantId, assignmentId, evaluatorUserId,
                LearningAssignmentEvaluationDecision.INDEPENDENT)) {
            return latest(tenantId, assignmentId, evaluatorUserId);
        }
        LearningAssignmentEvaluation saved = evaluationRepository.save(new LearningAssignmentEvaluation(
                tenantId, assignmentId, assignment.getCourseId(), assignment.getLearnerUserId(),
                evaluatorUserId, LearningAssignmentEvaluationDecision.INDEPENDENT,
                request.contentCorrectnessScore(), request.evidenceQualityScore(),
                request.transferReadinessScore(), cleanNullable(request.note()), Instant.now()));
        return LearningAssignmentEvaluationView.from(saved);
    }

    @Transactional(readOnly = true)
    public LearningAssignmentEvaluationConsensusView consensus(String tenantId, String userId,
                                                               String assignmentId) {
        LearningAssignment assignment = find(tenantId, assignmentId);
        if (!userId.equals(assignment.getTeacherUserId())
                && !userId.equals(assignment.getLearnerUserId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "LEARNING_ASSIGNMENT_ACCESS_DENIED",
                    "无权查看该课程作业评价共识");
        }
        List<LearningAssignmentEvaluation> evaluations = evaluationRepository
                .findByTenantIdAndLearningAssignmentIdOrderByCreatedAtDesc(tenantId, assignmentId);
        LearningAssignmentEvaluation teacher = evaluations.stream()
                .filter(item -> item.getEvaluatorUserId().equals(assignment.getTeacherUserId()))
                .findFirst().orElse(null);
        LearningAssignmentEvaluation independent = evaluations.stream()
                .filter(item -> item.getDecision() == LearningAssignmentEvaluationDecision.INDEPENDENT)
                .findFirst().orElse(null);
        if (teacher == null || independent == null) {
            return new LearningAssignmentEvaluationConsensusView(assignmentId, "PENDING",
                    teacher == null ? null : teacher.getId(),
                    independent == null ? null : independent.getId(), 0, 0, 0,
                    "需要教师量规和第二评分者量规各一份");
        }
        double contentDifference = Math.abs(teacher.getContentCorrectnessScore()
                - independent.getContentCorrectnessScore());
        double evidenceDifference = Math.abs(teacher.getEvidenceQualityScore()
                - independent.getEvidenceQualityScore());
        double transferDifference = Math.abs(teacher.getTransferReadinessScore()
                - independent.getTransferReadinessScore());
        boolean agreed = contentDifference <= CONSENSUS_MAX_SCORE_DIFFERENCE
                && evidenceDifference <= CONSENSUS_MAX_SCORE_DIFFERENCE
                && transferDifference <= CONSENSUS_MAX_SCORE_DIFFERENCE;
        return new LearningAssignmentEvaluationConsensusView(assignmentId,
                agreed ? "AGREED" : "DISAGREED", teacher.getId(), independent.getId(),
                contentDifference, evidenceDifference, transferDifference,
                "三个维度的评分差异均不超过 1 分时判定为 AGREED");
    }

    private LearningAssignmentEvaluationView latest(String tenantId, String assignmentId,
                                                    String evaluatorUserId) {
        return evaluationRepository.findByTenantIdAndLearningAssignmentIdOrderByCreatedAtDesc(
                        tenantId, assignmentId).stream()
                .filter(item -> item.getEvaluatorUserId().equals(evaluatorUserId))
                .max(Comparator.comparing(LearningAssignmentEvaluation::getCreatedAt))
                .map(LearningAssignmentEvaluationView::from)
                .orElseThrow(() -> new BusinessException(HttpStatus.CONFLICT,
                        "LEARNING_ASSIGNMENT_EVALUATION_NOT_FOUND", "独立评价记录不存在"));
    }

    private LearningAssignment find(String tenantId, String assignmentId) {
        return assignmentRepository.findByTenantIdAndId(tenantId, assignmentId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "LEARNING_ASSIGNMENT_NOT_FOUND", "课程作业不存在"));
    }

    private void validate(LearningAssignmentEvaluationRequest request) {
        if (request == null || request.contentCorrectnessScore() == null
                || request.evidenceQualityScore() == null
                || request.transferReadinessScore() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "LEARNING_ASSIGNMENT_RUBRIC_REQUIRED",
                    "独立评价必须填写三个量规评分");
        }
        if (request.contentCorrectnessScore() < 1 || request.contentCorrectnessScore() > 5
                || request.evidenceQualityScore() < 1 || request.evidenceQualityScore() > 5
                || request.transferReadinessScore() < 1 || request.transferReadinessScore() > 5) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "LEARNING_ASSIGNMENT_RUBRIC_INVALID",
                    "独立评价量规评分必须都在 1 到 5 之间");
        }
    }

    private String cleanNullable(String value) {
        String cleaned = sanitizer.sanitize(value == null ? "" : value.trim());
        return cleaned.isBlank() ? null : cleaned;
    }
}
