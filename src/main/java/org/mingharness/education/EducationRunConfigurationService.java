package org.mingharness.education;

import org.mingharness.common.BusinessException;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.education.api.EducationRunOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** 将创建请求解析为可审计、可复现的教育执行快照。 */
@Service
public class EducationRunConfigurationService {

    private static final Set<String> SUPPORTED_PEDAGOGICAL_MODES = Set.of(
            "AUTO", "EXPLAIN", "SOCRATIC", "PRACTICE", "DIAGNOSE");

    private final LearnerProfileRepository profileRepository;
    private final LearnerMasteryRepository masteryRepository;
    private final LearningGoalRepository goalRepository;
    private final SensitiveDataSanitizer sanitizer;

    /** 兼容旧组件测试和扩展调用方；未启用结构化学习目标解析。 */
    public EducationRunConfigurationService(LearnerProfileRepository profileRepository,
                                            LearnerMasteryRepository masteryRepository,
                                            SensitiveDataSanitizer sanitizer) {
        this(profileRepository, masteryRepository, null, sanitizer);
    }

    @Autowired
    public EducationRunConfigurationService(LearnerProfileRepository profileRepository,
                                            LearnerMasteryRepository masteryRepository,
                                            LearningGoalRepository goalRepository,
                                            SensitiveDataSanitizer sanitizer) {
        this.profileRepository = profileRepository;
        this.masteryRepository = masteryRepository;
        this.goalRepository = goalRepository;
        this.sanitizer = sanitizer;
    }

    @Transactional(readOnly = true)
    public EducationRunConfiguration resolve(String tenantId, String userId, EducationRunOptions options) {
        if (options == null || !options.isEnabled()) {
            return EducationRunConfiguration.disabled();
        }
        LearningGoal goal = resolveGoal(tenantId, userId, options.learningGoalId());
        String profileId = options.learnerProfileId();
        if (goal != null) {
            if (profileId != null && !profileId.isBlank() && !profileId.trim().equals(goal.getLearnerProfileId())) {
                throw new BusinessException(HttpStatus.CONFLICT, "LEARNING_GOAL_PROFILE_MISMATCH",
                        "学习目标不属于指定的学习者画像");
            }
            profileId = goal.getLearnerProfileId();
        }
        LearnerProfile profile = resolveProfile(tenantId, userId, profileId);
        String subject = firstNonBlank(options.subject(), profile.getSubject());
        String gradeLevel = firstNonBlank(options.gradeLevel(), profile.getGradeLevel());
        String curriculumVersion = firstNonBlank(options.curriculumVersion(), profile.getCurriculumVersion());
        if (subject == null || gradeLevel == null || curriculumVersion == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "EDUCATION_CONTEXT_INCOMPLETE",
                    "教育 Agent 必须明确学科、年级和课程版本");
        }
        Integer minDifficulty = bound(options.minDifficulty());
        Integer maxDifficulty = bound(options.maxDifficulty());
        if (minDifficulty != null && maxDifficulty != null && minDifficulty > maxDifficulty) {
            int temporary = minDifficulty;
            minDifficulty = maxDifficulty;
            maxDifficulty = temporary;
        }
        String pedagogicalMode = options.effectivePedagogicalMode();
        if (!SUPPORTED_PEDAGOGICAL_MODES.contains(pedagogicalMode)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "EDUCATION_PEDAGOGICAL_MODE_INVALID",
                    "不支持的教学策略: " + pedagogicalMode);
        }
        String requestedConcept = clean(options.conceptKey());
        if (goal != null && requestedConcept != null && !requestedConcept.equalsIgnoreCase(goal.getConceptKey())) {
            throw new BusinessException(HttpStatus.CONFLICT, "LEARNING_GOAL_CONCEPT_MISMATCH",
                    "学习目标知识点与本次 Run 的目标知识点不一致");
        }
        String conceptKey = goal == null ? requestedConcept : goal.getConceptKey();
        return new EducationRunConfiguration(true, profile.getId(),
                goal == null ? null : goal.getId(), goal == null ? null : goal.getTitle(),
                goal == null ? 0.0 : goal.getBaselineMastery(),
                goal == null ? 0.0 : goal.getTargetMastery(),
                clean(subject), clean(gradeLevel), clean(curriculumVersion), conceptKey,
                minDifficulty, maxDifficulty, pedagogicalMode, masterySummary(tenantId, profile.getId()));
    }

    private LearningGoal resolveGoal(String tenantId, String userId, String goalId) {
        if (goalId == null || goalId.isBlank()) return null;
        if (goalRepository == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "LEARNING_GOAL_UNAVAILABLE",
                    "当前运行环境未启用学习目标存储");
        }
        LearningGoal goal = goalRepository.findByIdAndTenantIdAndUserId(goalId.trim(), tenantId, userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "LEARNING_GOAL_NOT_FOUND", "学习目标不存在"));
        if (goal.getStatus() != LearningGoalStatus.ACTIVE) {
            throw new BusinessException(HttpStatus.CONFLICT, "LEARNING_GOAL_NOT_ACTIVE",
                    "只有进行中的学习目标可以绑定新的教育 Run");
        }
        return goal;
    }

    private LearnerProfile resolveProfile(String tenantId, String userId, String profileId) {
        if (profileId != null && !profileId.isBlank()) {
            return profileRepository.findByIdAndTenantIdAndUserId(profileId.trim(), tenantId, userId)
                    .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                            "LEARNER_PROFILE_NOT_FOUND", "指定的学习者画像不存在"));
        }
        return profileRepository.findTop1ByTenantIdAndUserIdAndActiveTrueOrderByUpdatedAtDesc(tenantId, userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST,
                        "LEARNER_PROFILE_REQUIRED", "启用教育 Agent 前请先创建学习者画像"));
    }

    private String masterySummary(String tenantId, String profileId) {
        List<LearnerMastery> mastery = masteryRepository
                .findByTenantIdAndLearnerProfileIdOrderByConceptKeyAsc(tenantId, profileId);
        if (mastery.isEmpty()) return "暂无掌握度记录";
        return mastery.stream()
                .limit(40)
                .map(item -> item.getConceptKey() + "=" + String.format(Locale.ROOT, "%.2f", item.getMasteryScore()))
                .collect(Collectors.joining(", "));
    }

    private String firstNonBlank(String first, String fallback) {
        return first == null || first.isBlank() ? clean(fallback) : clean(first);
    }

    private String clean(String value) {
        String result = sanitizer.sanitize(value == null ? "" : value.trim());
        return result.isBlank() ? null : result;
    }

    private Integer bound(Integer value) {
        return value == null ? null : Math.max(1, Math.min(5, value));
    }
}
