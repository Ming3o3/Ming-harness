package org.mingharness.education;

import org.mingharness.common.BusinessException;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.education.api.MasteryUpdateRequest;
import org.mingharness.runtime.domain.Run;
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
    private final SensitiveDataSanitizer sanitizer;

    public EducationAssessmentService(AssessmentAttemptRepository attemptRepository,
                                      RunRepository runRepository,
                                      LearningGoalRepository goalRepository,
                                      LearnerMasteryRepository masteryRepository,
                                      EducationLearnerService learnerService,
                                      SensitiveDataSanitizer sanitizer) {
        this.attemptRepository = attemptRepository;
        this.runRepository = runRepository;
        this.goalRepository = goalRepository;
        this.masteryRepository = masteryRepository;
        this.learnerService = learnerService;
        this.sanitizer = sanitizer;
    }

    @Transactional
    public AssessmentAttempt record(String tenantId, String userId, String runId, String stepId,
                                    String profileId, String conceptKey, boolean correct,
                                    double observedMastery, String feedback) {
        Run run = runRepository.findById(runId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "RUN_NOT_FOUND", "测评所属 Run 不存在"));
        if (!tenantId.equals(run.getTenantId()) || !userId.equals(run.getUserId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "ASSESSMENT_ACCESS_DENIED",
                    "无权记录其他用户 Run 的测评");
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
        if (goal.getStatus() != LearningGoalStatus.ACTIVE) {
            throw new BusinessException(HttpStatus.CONFLICT, "LEARNING_GOAL_NOT_ACTIVE",
                    "只有进行中的学习目标可以记录测评");
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
                updated.getMasteryScore(), cleanFeedback(feedback));
        AssessmentAttempt saved = attemptRepository.save(attempt);
        if (updated.getMasteryScore() >= goal.getTargetMastery()) {
            goal.changeStatus(LearningGoalStatus.COMPLETED);
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

    private double clamp(double value) {
        if (!Double.isFinite(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
    }
}
