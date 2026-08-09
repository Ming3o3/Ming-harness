package org.mingharness.education;

import org.mingharness.common.BusinessException;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.education.api.LearningGoalRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/** 管理可绑定到教育 Run 的学习目标，目标状态与学习者画像严格隔离。 */
@Service
public class LearningGoalService {

    private final LearningGoalRepository goalRepository;
    private final LearnerProfileRepository profileRepository;
    private final LearnerMasteryRepository masteryRepository;
    private final SensitiveDataSanitizer sanitizer;

    public LearningGoalService(LearningGoalRepository goalRepository,
                               LearnerProfileRepository profileRepository,
                               LearnerMasteryRepository masteryRepository,
                               SensitiveDataSanitizer sanitizer) {
        this.goalRepository = goalRepository;
        this.profileRepository = profileRepository;
        this.masteryRepository = masteryRepository;
        this.sanitizer = sanitizer;
    }

    @Transactional
    public LearningGoal create(String tenantId, String userId, LearningGoalRequest request) {
        LearnerProfile profile = resolveProfile(tenantId, userId, request.learnerProfileId());
        String conceptKey = clean(request.conceptKey());
        double baseline = masteryRepository
                .findByTenantIdAndLearnerProfileIdAndConceptKey(tenantId, profile.getId(), conceptKey)
                .map(LearnerMastery::getMasteryScore)
                .orElse(0.0);
        double target = request.effectiveTargetMastery();
        if (target <= baseline) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "LEARNING_GOAL_TARGET_INVALID",
                    "目标掌握度必须高于当前掌握度");
        }
        return goalRepository.save(new LearningGoal(tenantId, userId, profile.getId(),
                clean(request.title()), conceptKey, baseline, target));
    }

    @Transactional(readOnly = true)
    public List<LearningGoal> list(String tenantId, String userId) {
        return goalRepository.findByTenantIdAndUserIdOrderByUpdatedAtDesc(tenantId, userId);
    }

    @Transactional(readOnly = true)
    public LearningGoal get(String tenantId, String userId, String goalId) {
        return goalRepository.findByIdAndTenantIdAndUserId(goalId, tenantId, userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "LEARNING_GOAL_NOT_FOUND", "学习目标不存在"));
    }

    @Transactional
    public LearningGoal changeStatus(String tenantId, String userId, String goalId, String status) {
        LearningGoal goal = get(tenantId, userId, goalId);
        LearningGoalStatus next;
        try {
            next = LearningGoalStatus.valueOf(clean(status).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "LEARNING_GOAL_STATUS_INVALID",
                    "不支持的学习目标状态: " + status);
        }
        try {
            goal.changeStatus(next);
        } catch (IllegalStateException exception) {
            throw new BusinessException(HttpStatus.CONFLICT, "LEARNING_GOAL_STATUS_CONFLICT",
                    exception.getMessage());
        }
        return goalRepository.save(goal);
    }

    private LearnerProfile resolveProfile(String tenantId, String userId, String profileId) {
        if (profileId != null && !profileId.isBlank()) {
            return profileRepository.findByIdAndTenantIdAndUserId(profileId.trim(), tenantId, userId)
                    .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                            "LEARNER_PROFILE_NOT_FOUND", "学习者画像不存在"));
        }
        return profileRepository.findTop1ByTenantIdAndUserIdAndActiveTrueOrderByUpdatedAtDesc(tenantId, userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST,
                        "LEARNER_PROFILE_REQUIRED", "创建学习目标前请先创建学习者画像"));
    }

    private String clean(String value) {
        return sanitizer.sanitize(value == null ? "" : value.trim());
    }
}
