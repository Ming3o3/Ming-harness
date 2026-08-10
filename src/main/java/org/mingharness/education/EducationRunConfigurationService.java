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
    private final LearningAssignmentRepository assignmentRepository;
    private final LearningReviewPlanService reviewPlanService;
    private final SensitiveDataSanitizer sanitizer;

    /** 兼容旧组件测试和扩展调用方；未启用结构化学习目标解析。 */
    public EducationRunConfigurationService(LearnerProfileRepository profileRepository,
                                            LearnerMasteryRepository masteryRepository,
                                            SensitiveDataSanitizer sanitizer) {
        this(profileRepository, masteryRepository, null, null, null, sanitizer);
    }

    /** 兼容已启用学习目标但尚未使用保持度复习的测试和扩展调用方。 */
    public EducationRunConfigurationService(LearnerProfileRepository profileRepository,
                                            LearnerMasteryRepository masteryRepository,
                                            LearningGoalRepository goalRepository,
                                            SensitiveDataSanitizer sanitizer) {
        this(profileRepository, masteryRepository, goalRepository, null, null, sanitizer);
    }

    /** 兼容已接入保持度复习但尚未绑定课程作业的扩展调用方。 */
    public EducationRunConfigurationService(LearnerProfileRepository profileRepository,
                                            LearnerMasteryRepository masteryRepository,
                                            LearningGoalRepository goalRepository,
                                            LearningReviewPlanService reviewPlanService,
                                            SensitiveDataSanitizer sanitizer) {
        this(profileRepository, masteryRepository, goalRepository, reviewPlanService, null, sanitizer);
    }

    @Autowired
    public EducationRunConfigurationService(LearnerProfileRepository profileRepository,
                                            LearnerMasteryRepository masteryRepository,
                                            LearningGoalRepository goalRepository,
                                            LearningReviewPlanService reviewPlanService,
                                            LearningAssignmentRepository assignmentRepository,
                                            SensitiveDataSanitizer sanitizer) {
        this.profileRepository = profileRepository;
        this.masteryRepository = masteryRepository;
        this.goalRepository = goalRepository;
        this.assignmentRepository = assignmentRepository;
        this.reviewPlanService = reviewPlanService;
        this.sanitizer = sanitizer;
    }

    @Transactional(readOnly = true)
    public EducationRunConfiguration resolve(String tenantId, String userId, EducationRunOptions options) {
        if (options == null || !options.isEnabled()) {
            return EducationRunConfiguration.disabled();
        }
        LearningReviewPlan reviewPlan = resolveReviewPlan(tenantId, userId, options.reviewPlanId());
        String requestedGoalId = options.learningGoalId();
        LearningAssignment assignment = resolveAssignment(tenantId, userId,
                options.learningAssignmentId(), requestedGoalId);
        if (assignment != null) {
            requestedGoalId = assignment.getLearningGoalId();
        }
        if (reviewPlan != null) {
            if (requestedGoalId != null && !requestedGoalId.isBlank()
                    && !requestedGoalId.trim().equals(reviewPlan.getLearningGoalId())) {
                throw new BusinessException(HttpStatus.CONFLICT, "LEARNING_REVIEW_GOAL_MISMATCH",
                        "复习计划与学习目标不一致");
            }
            requestedGoalId = reviewPlan.getLearningGoalId();
            if (!reviewPlan.isDue(java.time.Instant.now())) {
                throw new BusinessException(HttpStatus.CONFLICT, "LEARNING_REVIEW_NOT_DUE",
                        "当前保持度复习尚未到期");
            }
        }
        LearningGoal goal = resolveGoal(tenantId, userId, requestedGoalId, reviewPlan != null);
        if (assignment == null && goal != null && assignmentRepository != null) {
            assignment = assignmentRepository
                    .findByTenantIdAndLearnerUserIdAndLearningGoalIdOrderByCreatedAtDesc(
                            tenantId, userId, goal.getId()).stream().findFirst().orElse(null);
            validateAssignmentState(assignment);
        }
        if (assignment != null && goal != null) {
            if (!goal.getId().equals(assignment.getLearningGoalId())
                    || !goal.getLearnerProfileId().equals(assignment.getLearnerProfileId())) {
                throw new BusinessException(HttpStatus.CONFLICT, "LEARNING_ASSIGNMENT_CONTEXT_MISMATCH",
                        "课程作业与学习目标的画像或目标不一致");
            }
        }
        String profileId = options.learnerProfileId();
        if (goal != null) {
            if (reviewPlan != null && goal.getStatus() != LearningGoalStatus.COMPLETED) {
                throw new BusinessException(HttpStatus.CONFLICT, "LEARNING_REVIEW_GOAL_NOT_COMPLETED",
                        "保持度复习计划只能绑定已完成的学习目标");
            }
            if (reviewPlan != null && (!goal.getLearnerProfileId().equals(reviewPlan.getLearnerProfileId())
                    || !goal.getConceptKey().equalsIgnoreCase(reviewPlan.getConceptKey()))) {
                throw new BusinessException(HttpStatus.CONFLICT, "LEARNING_REVIEW_CONTEXT_MISMATCH",
                        "复习计划与学习目标的画像或知识点不一致");
            }
            String expectedProfileId = reviewPlan == null ? goal.getLearnerProfileId()
                    : reviewPlan.getLearnerProfileId();
            if (profileId != null && !profileId.isBlank() && !profileId.trim().equals(expectedProfileId)) {
                throw new BusinessException(HttpStatus.CONFLICT, "LEARNING_GOAL_PROFILE_MISMATCH",
                        "学习目标不属于指定的学习者画像");
            }
            profileId = expectedProfileId;
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
                goal == null ? null : goal.getId(), assignment == null ? null : assignment.getId(),
                reviewPlan == null ? null : reviewPlan.getId(),
                goal == null ? null : goal.getTitle(),
                goal == null ? 0.0 : goal.getBaselineMastery(),
                goal == null ? 0.0 : goal.getTargetMastery(),
                clean(subject), clean(gradeLevel), clean(curriculumVersion), conceptKey,
                minDifficulty, maxDifficulty, pedagogicalMode, masterySummary(tenantId, profile.getId()));
    }

    private LearningAssignment resolveAssignment(String tenantId, String userId,
                                                 String assignmentId, String requestedGoalId) {
        String normalizedId = clean(assignmentId);
        if (normalizedId == null) return null;
        if (assignmentRepository == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "LEARNING_ASSIGNMENT_UNAVAILABLE",
                    "当前运行环境未启用课程作业存储");
        }
        LearningAssignment assignment = assignmentRepository.findByTenantIdAndId(tenantId, normalizedId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "LEARNING_ASSIGNMENT_NOT_FOUND", "课程作业不存在"));
        if (!userId.equals(assignment.getLearnerUserId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "LEARNING_ASSIGNMENT_LEARNER_ONLY",
                    "只有作业学习者可以使用该作业创建教育 Run");
        }
        validateAssignmentState(assignment);
        if (requestedGoalId != null && !requestedGoalId.isBlank()
                && !requestedGoalId.trim().equals(assignment.getLearningGoalId())) {
            throw new BusinessException(HttpStatus.CONFLICT, "LEARNING_ASSIGNMENT_GOAL_MISMATCH",
                    "课程作业与请求中的学习目标不一致");
        }
        if (assignment.getLearningGoalId() == null || assignment.getLearningGoalId().isBlank()) {
            throw new BusinessException(HttpStatus.CONFLICT, "LEARNING_ASSIGNMENT_GOAL_REQUIRED",
                    "接受课程作业后才能创建绑定作业的教育 Run");
        }
        return assignment;
    }

    private void validateAssignmentState(LearningAssignment assignment) {
        if (assignment == null) return;
        if (assignment.getStatus() == LearningAssignmentStatus.ASSIGNED) {
            throw new BusinessException(HttpStatus.CONFLICT, "LEARNING_ASSIGNMENT_NOT_ACCEPTED",
                    "课程作业尚未被学习者接受，不能创建教育 Run");
        }
        if (assignment.getStatus() == LearningAssignmentStatus.CANCELLED) {
            throw new BusinessException(HttpStatus.CONFLICT, "LEARNING_ASSIGNMENT_CANCELLED",
                    "已取消的课程作业不能创建教育 Run");
        }
    }

    private LearningGoal resolveGoal(String tenantId, String userId, String goalId, boolean allowCompleted) {
        if (goalId == null || goalId.isBlank()) return null;
        if (goalRepository == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "LEARNING_GOAL_UNAVAILABLE",
                    "当前运行环境未启用学习目标存储");
        }
        LearningGoal goal = goalRepository.findByIdAndTenantIdAndUserId(goalId.trim(), tenantId, userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "LEARNING_GOAL_NOT_FOUND", "学习目标不存在"));
        if (goal.getStatus() != LearningGoalStatus.ACTIVE
                && !(allowCompleted && goal.getStatus() == LearningGoalStatus.COMPLETED)) {
            throw new BusinessException(HttpStatus.CONFLICT, "LEARNING_GOAL_NOT_ACTIVE",
                    allowCompleted ? "只有已完成且绑定复习计划的学习目标可以创建复习 Run"
                            : "只有进行中的学习目标可以绑定新的教育 Run");
        }
        return goal;
    }

    private LearningReviewPlan resolveReviewPlan(String tenantId, String userId, String reviewPlanId) {
        if (reviewPlanId == null || reviewPlanId.isBlank()) return null;
        if (reviewPlanService == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "LEARNING_REVIEW_UNAVAILABLE",
                    "当前运行环境未启用保持度复习计划");
        }
        LearningReviewPlan plan = reviewPlanService.getById(tenantId, userId, reviewPlanId.trim());
        if (plan.getStatus() != LearningReviewPlanStatus.ACTIVE) {
            throw new BusinessException(HttpStatus.CONFLICT, "LEARNING_REVIEW_PLAN_NOT_ACTIVE",
                    "当前保持度复习计划不可执行");
        }
        return plan;
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
