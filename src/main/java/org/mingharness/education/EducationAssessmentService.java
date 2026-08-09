package org.mingharness.education;

import org.mingharness.common.BusinessException;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.education.api.MasteryUpdateRequest;
import org.mingharness.runtime.domain.Run;
import org.mingharness.runtime.domain.RunStatus;
import org.mingharness.runtime.repository.RunRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 将测评、掌握度变化和学习目标状态放在同一个可追踪事务中。 */
@Service
public class EducationAssessmentService {

    private final AssessmentAttemptRepository attemptRepository;
    private final RunRepository runRepository;
    private final LearningGoalRepository goalRepository;
    private final LearnerMasteryRepository masteryRepository;
    private final EducationLearnerService learnerService;
    private final LearningReviewPlanService reviewPlanService;
    private final SensitiveDataSanitizer sanitizer;

    public EducationAssessmentService(AssessmentAttemptRepository attemptRepository,
                                      RunRepository runRepository,
                                      LearningGoalRepository goalRepository,
                                      LearnerMasteryRepository masteryRepository,
                                      EducationLearnerService learnerService,
                                      SensitiveDataSanitizer sanitizer) {
        this(attemptRepository, runRepository, goalRepository, masteryRepository, learnerService, null, sanitizer);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public EducationAssessmentService(AssessmentAttemptRepository attemptRepository,
                                      RunRepository runRepository,
                                      LearningGoalRepository goalRepository,
                                      LearnerMasteryRepository masteryRepository,
                                      EducationLearnerService learnerService,
                                      LearningReviewPlanService reviewPlanService,
                                      SensitiveDataSanitizer sanitizer) {
        this.attemptRepository = attemptRepository;
        this.runRepository = runRepository;
        this.goalRepository = goalRepository;
        this.masteryRepository = masteryRepository;
        this.learnerService = learnerService;
        this.reviewPlanService = reviewPlanService;
        this.sanitizer = sanitizer;
    }

    @Transactional
    public AssessmentAttempt record(String tenantId, String userId, String runId, String stepId,
                                    String profileId, String conceptKey, boolean correct,
                                    double observedMastery, String feedback) {
        return recordInternal(tenantId, userId, runId, stepId, null, profileId, conceptKey,
                correct, observedMastery, "MODEL_TOOL", null, feedback);
    }

    @Transactional
    public AssessmentAttempt record(String tenantId, String userId, String runId, String stepId,
                                    String profileId, String conceptKey, boolean correct,
                                    double observedMastery, String evidenceSource,
                                    String evidenceText, String feedback) {
        return recordInternal(tenantId, userId, runId, stepId, null, profileId, conceptKey,
                correct, observedMastery, evidenceSource, evidenceText, feedback);
    }

    /** 由路径绑定的目标提交复核，防止请求体里的 Run 与 URL 目标交叉写入。 */
    @Transactional
    public AssessmentAttempt recordForGoal(String tenantId, String userId, String expectedGoalId,
                                           String runId, String stepId, String profileId,
                                           String conceptKey, boolean correct, double observedMastery,
                                           String evidenceSource, String evidenceText, String feedback) {
        return recordInternal(tenantId, userId, runId, stepId, expectedGoalId, profileId, conceptKey,
                correct, observedMastery, evidenceSource, evidenceText, feedback);
    }

    private AssessmentAttempt recordInternal(String tenantId, String userId, String runId, String stepId,
                                             String expectedGoalId, String profileId, String conceptKey,
                                             boolean correct, double observedMastery, String evidenceSource,
                                             String evidenceText, String feedback) {
        Run run = runRepository.findById(runId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "RUN_NOT_FOUND", "测评所属 Run 不存在"));
        if (!tenantId.equals(run.getTenantId()) || !userId.equals(run.getUserId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "ASSESSMENT_ACCESS_DENIED",
                    "无权记录其他用户 Run 的测评");
        }
        String normalizedEvidenceSource = clean(evidenceSource).toUpperCase(java.util.Locale.ROOT);
        if (!"MODEL_TOOL".equals(normalizedEvidenceSource)
                && !"MANUAL_REVIEW".equals(normalizedEvidenceSource)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ASSESSMENT_EVIDENCE_SOURCE_INVALID",
                    "测评证据来源只支持 MODEL_TOOL 或 MANUAL_REVIEW");
        }
        if ("MANUAL_REVIEW".equals(normalizedEvidenceSource)) {
            if (cleanEvidence(evidenceText) == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "ASSESSMENT_EVIDENCE_REQUIRED",
                        "人工复核必须提供作答或评分依据");
            }
            if (run.getStatus() != RunStatus.SUCCEEDED) {
                throw new BusinessException(HttpStatus.CONFLICT, "ASSESSMENT_RUN_NOT_FINISHED",
                        "人工复核只能提交已完成的教育 Run");
            }
            boolean stepExists = run.getSteps().stream()
                    .anyMatch(step -> java.util.Objects.equals(stepId, step.getId()));
            if (!stepExists) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "ASSESSMENT_STEP_NOT_FOUND",
                        "人工复核步骤不属于指定 Run");
            }
        }
        if (!run.isEducationMode() || run.getEducationLearningGoalId() == null) {
            throw new BusinessException(HttpStatus.CONFLICT, "LEARNING_GOAL_REQUIRED",
                    "形成性测评必须绑定进行中的学习目标");
        }
        if (!run.getEducationLearnerProfileId().equals(profileId)) {
            throw new BusinessException(HttpStatus.CONFLICT, "ASSESSMENT_PROFILE_MISMATCH",
                    "测评画像与 Run 冻结画像不一致");
        }

        LearningGoal goal = goalRepository.findByIdAndTenantIdAndUserId(
                        run.getEducationLearningGoalId(), tenantId, userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "LEARNING_GOAL_NOT_FOUND", "测评绑定的学习目标不存在"));
        if (expectedGoalId != null && !expectedGoalId.equals(goal.getId())) {
            throw new BusinessException(HttpStatus.CONFLICT, "ASSESSMENT_GOAL_MISMATCH",
                    "测评 Run 与请求路径中的学习目标不一致");
        }
        String reviewPlanId = run.getEducationReviewPlanId();
        AssessmentAttemptType attemptType = AssessmentAttemptType.FORMATIVE;
        if (reviewPlanId != null && !reviewPlanId.isBlank()) {
            if (reviewPlanService == null) {
                throw new BusinessException(HttpStatus.CONFLICT, "LEARNING_REVIEW_UNAVAILABLE",
                        "当前运行环境未启用保持度复习计划");
            }
            LearningReviewPlan plan = reviewPlanService.getById(tenantId, userId, reviewPlanId);
            if (!goal.getId().equals(plan.getLearningGoalId())
                    || !profileId.equals(plan.getLearnerProfileId())
                    || !goal.getConceptKey().equalsIgnoreCase(plan.getConceptKey())) {
                throw new BusinessException(HttpStatus.CONFLICT, "LEARNING_REVIEW_CONTEXT_MISMATCH",
                        "复习计划与目标、画像或知识点不一致");
            }
            if (goal.getStatus() != LearningGoalStatus.COMPLETED) {
                throw new BusinessException(HttpStatus.CONFLICT, "LEARNING_REVIEW_GOAL_NOT_COMPLETED",
                        "保持度复习只能用于已完成的学习目标");
            }
            attemptType = AssessmentAttemptType.REVIEW;
        } else if (goal.getStatus() != LearningGoalStatus.ACTIVE) {
            throw new BusinessException(HttpStatus.CONFLICT, "LEARNING_GOAL_NOT_ACTIVE",
                    "只有进行中的学习目标可以记录形成性测评");
        }
        String normalizedConcept = clean(conceptKey);
        if (!goal.getConceptKey().equalsIgnoreCase(normalizedConcept)
                || !goal.getConceptKey().equalsIgnoreCase(run.getEducationConceptKey())) {
            throw new BusinessException(HttpStatus.CONFLICT, "ASSESSMENT_CONCEPT_MISMATCH",
                    "测评知识点与学习目标不一致");
        }

        double before = masteryRepository
                .findByTenantIdAndLearnerProfileIdAndConceptKey(tenantId, profileId, normalizedConcept)
                .map(LearnerMastery::getMasteryScore)
                .orElse(0.0);
        double boundedObserved = clamp(observedMastery);
        LearnerMastery updated = learnerService.updateMastery(tenantId, userId, profileId,
                new MasteryUpdateRequest(normalizedConcept, boundedObserved, correct, null, null));
        AssessmentAttempt attempt = new AssessmentAttempt(tenantId, userId, runId, stepId,
                goal.getId(), profileId, normalizedConcept, correct, boundedObserved, before,
                updated.getMasteryScore(), attemptType, reviewPlanId, normalizedEvidenceSource,
                cleanEvidence(evidenceText),
                cleanFeedback(feedback));
        AssessmentAttempt saved = attemptRepository.save(attempt);
        if (attemptType == AssessmentAttemptType.REVIEW) {
            reviewPlanService.recordReview(tenantId, userId, reviewPlanId, correct, java.time.Instant.now());
        } else if (updated.getMasteryScore() >= goal.getTargetMastery()) {
            goal.changeStatus(LearningGoalStatus.COMPLETED);
            if (reviewPlanService != null) {
                reviewPlanService.ensureForCompletedGoal(goal);
            }
            goalRepository.save(goal);
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public List<AssessmentAttempt> listByGoal(String tenantId, String userId, String goalId) {
        goalRepository.findByIdAndTenantIdAndUserId(goalId, tenantId, userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "LEARNING_GOAL_NOT_FOUND", "学习目标不存在"));
        return attemptRepository.findByTenantIdAndUserIdAndLearningGoalIdOrderByCreatedAtAsc(
                tenantId, userId, goalId);
    }

    private String clean(String value) {
        return sanitizer.sanitize(value == null ? "" : value.trim());
    }

    private String cleanFeedback(String value) {
        String cleaned = clean(value);
        return cleaned.isBlank() ? null : cleaned;
    }

    private String cleanEvidence(String value) {
        String cleaned = clean(value);
        return cleaned.isBlank() ? null : cleaned;
    }

    private double clamp(double value) {
        if (!Double.isFinite(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
    }
}
