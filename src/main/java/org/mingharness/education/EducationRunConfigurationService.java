package org.mingharness.education;

import org.mingharness.common.BusinessException;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.education.api.EducationRunOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
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
    private final LearningAssignmentFeedbackRepository feedbackRepository;
    private final LearningReviewPlanService reviewPlanService;
    private final EducationCourseRepository courseRepository;
    private final EducationEnrollmentRepository enrollmentRepository;
    private final EducationKnowledgeService knowledgeService;
    private final SensitiveDataSanitizer sanitizer;

    /** 兼容旧组件测试和扩展调用方；未启用结构化学习目标解析。 */
    public EducationRunConfigurationService(LearnerProfileRepository profileRepository,
                                            LearnerMasteryRepository masteryRepository,
                                            SensitiveDataSanitizer sanitizer) {
        this(profileRepository, masteryRepository, null, null, null, null, null, null, null, sanitizer);
    }

    /** 兼容已启用学习目标但尚未使用保持度复习的测试和扩展调用方。 */
    public EducationRunConfigurationService(LearnerProfileRepository profileRepository,
                                            LearnerMasteryRepository masteryRepository,
                                            LearningGoalRepository goalRepository,
                                            SensitiveDataSanitizer sanitizer) {
        this(profileRepository, masteryRepository, goalRepository, null, null, null, null, null, null, sanitizer);
    }

    /** 兼容已接入保持度复习但尚未绑定课程作业的扩展调用方。 */
    public EducationRunConfigurationService(LearnerProfileRepository profileRepository,
                                            LearnerMasteryRepository masteryRepository,
                                            LearningGoalRepository goalRepository,
                                            LearningReviewPlanService reviewPlanService,
                                            SensitiveDataSanitizer sanitizer) {
        this(profileRepository, masteryRepository, goalRepository, reviewPlanService, null, null,
                null, null, null, sanitizer);
    }

    /** 兼容已绑定课程作业但尚未接入教师干预的扩展调用方。 */
    public EducationRunConfigurationService(LearnerProfileRepository profileRepository,
                                            LearnerMasteryRepository masteryRepository,
                                            LearningGoalRepository goalRepository,
                                            LearningReviewPlanService reviewPlanService,
                                            LearningAssignmentRepository assignmentRepository,
                                            SensitiveDataSanitizer sanitizer) {
        this(profileRepository, masteryRepository, goalRepository, reviewPlanService,
                assignmentRepository, null, null, null, null, sanitizer);
    }

    /** 兼容课程实例作为 Run 约束前的完整组件构造方式。 */
    public EducationRunConfigurationService(LearnerProfileRepository profileRepository,
                                            LearnerMasteryRepository masteryRepository,
                                            LearningGoalRepository goalRepository,
                                            LearningReviewPlanService reviewPlanService,
                                            LearningAssignmentRepository assignmentRepository,
                                            LearningAssignmentFeedbackRepository feedbackRepository,
                                            SensitiveDataSanitizer sanitizer) {
        this(profileRepository, masteryRepository, goalRepository, reviewPlanService,
                assignmentRepository, feedbackRepository, null, null, null, sanitizer);
    }

    /** 兼容课程实例已作为 Run 约束、但尚未接入知识源前置校验的扩展调用方。 */
    public EducationRunConfigurationService(LearnerProfileRepository profileRepository,
                                            LearnerMasteryRepository masteryRepository,
                                            LearningGoalRepository goalRepository,
                                            LearningReviewPlanService reviewPlanService,
                                            LearningAssignmentRepository assignmentRepository,
                                            LearningAssignmentFeedbackRepository feedbackRepository,
                                            EducationCourseRepository courseRepository,
                                            EducationEnrollmentRepository enrollmentRepository,
                                            SensitiveDataSanitizer sanitizer) {
        this(profileRepository, masteryRepository, goalRepository, reviewPlanService,
                assignmentRepository, feedbackRepository, courseRepository, enrollmentRepository,
                null, sanitizer);
    }

    @Autowired
    public EducationRunConfigurationService(LearnerProfileRepository profileRepository,
                                            LearnerMasteryRepository masteryRepository,
                                            LearningGoalRepository goalRepository,
                                            LearningReviewPlanService reviewPlanService,
                                            LearningAssignmentRepository assignmentRepository,
                                            LearningAssignmentFeedbackRepository feedbackRepository,
                                            EducationCourseRepository courseRepository,
                                            EducationEnrollmentRepository enrollmentRepository,
                                            EducationKnowledgeService knowledgeService,
                                            SensitiveDataSanitizer sanitizer) {
        this.profileRepository = profileRepository;
        this.masteryRepository = masteryRepository;
        this.goalRepository = goalRepository;
        this.assignmentRepository = assignmentRepository;
        this.feedbackRepository = feedbackRepository;
        this.reviewPlanService = reviewPlanService;
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.knowledgeService = knowledgeService;
        this.sanitizer = sanitizer;
    }

    @Transactional
    public EducationRunConfiguration resolve(String tenantId, String userId, EducationRunOptions options) {
        if (options == null || !options.isEnabled()) {
            return EducationRunConfiguration.disabled();
        }
        EducationRunConfiguration teacherCourseConfiguration = resolveTeacherCourseConfiguration(
                tenantId, userId, options);
        if (teacherCourseConfiguration != null) {
            return teacherCourseConfiguration;
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
            assignment = refreshOverdueState(assignment);
            validateAssignmentState(tenantId, userId, assignment);
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
        // 学习者画像是直接学习会话的权威课程约束。没有课程实例时，也不能让客户端
        // 借请求字段把同一画像切换到另一学科、年级或课程版本；否则检索和掌握度会
        // 落到两套不一致的课程语境中。
        String subject = clean(profile.getSubject());
        String gradeLevel = clean(profile.getGradeLevel());
        String curriculumVersion = clean(profile.getCurriculumVersion());
        if (!sameOrUnspecified(options.subject(), subject)
                || !sameOrUnspecified(options.gradeLevel(), gradeLevel)
                || !sameOrUnspecified(options.curriculumVersion(), curriculumVersion)) {
            throw new BusinessException(HttpStatus.CONFLICT, "EDUCATION_PROFILE_CONTEXT_MISMATCH",
                    "请求中的学科、年级或课程版本与学习者画像不一致");
        }
        EducationCourse course = resolveCourse(tenantId, userId, options.courseId(), assignment);
        if (course != null) {
            if (!sameContext(subject, course.getSubject())
                    || !sameContext(gradeLevel, course.getGradeLevel())
                    || !sameContext(curriculumVersion, course.getCurriculumVersion())) {
                throw new BusinessException(HttpStatus.CONFLICT, "EDUCATION_COURSE_CONTEXT_MISMATCH",
                        "学习者画像与所选课程的学科、年级或课程版本不一致");
            }
            // 课程实例是本次 Run 的权威课程约束，不能被客户端自由字段覆盖。
            subject = course.getSubject();
            gradeLevel = course.getGradeLevel();
            curriculumVersion = course.getCurriculumVersion();
        }
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
        if (goal != null && requestedConcept != null
                && !EducationRetrievalFilter.conceptsMatch(requestedConcept, goal.getConceptKey())) {
            throw new BusinessException(HttpStatus.CONFLICT, "LEARNING_GOAL_CONCEPT_MISMATCH",
                    "学习目标知识点与本次 Run 的目标知识点不一致");
        }
        String conceptKey = goal == null ? requestedConcept : goal.getConceptKey();
        String assignmentInstructions = assignment == null ? null : assignment.getInstructions();
        String teacherReviewNote = assignment != null
                && assignment.getReviewStatus() == LearningAssignmentReviewStatus.REVISION_REQUIRED
                ? clean(assignment.getTeacherReviewNote()) : null;
        LearningAssignmentFeedback intervention = latestOpenIntervention(
                tenantId, userId, assignment);
        if (intervention != null) {
            assignmentInstructions = (assignmentInstructions == null ? "" : assignmentInstructions)
                    + "\n教师当前干预（"
                    + intervention.getAction().name() + "）：" + intervention.getMessage();
        }
        String learnerStateSnapshot = masterySnapshot(tenantId, profile.getId());
        EducationRunConfiguration configuration = new EducationRunConfiguration(true, profile.getId(),
                goal == null ? null : goal.getId(), assignment == null ? null : assignment.getId(),
                assignment == null ? null : assignment.getTitle(),
                assignmentInstructions,
                teacherReviewNote,
                reviewPlan == null ? null : reviewPlan.getId(),
                goal == null ? null : goal.getTitle(),
                goal == null ? 0.0 : goal.getBaselineMastery(),
                goal == null ? 0.0 : goal.getTargetMastery(),
                clean(subject), clean(gradeLevel), clean(curriculumVersion), conceptKey,
                minDifficulty, maxDifficulty, pedagogicalMode, masterySummary(tenantId, profile.getId()),
                course == null ? null : course.getId(),
                course == null ? null : course.getCode(),
                course == null ? null : course.getTitle(),
                normalizeRetrievalStrategy(options.effectiveRetrievalStrategy()));
        requireKnowledgeSource(tenantId, userId, configuration.retrievalFilter());
        EducationDependencyGraph graph = knowledgeService == null
                ? EducationDependencyGraph.empty(configuration.conceptKey())
                : knowledgeService.resolveDependencyGraph(tenantId, configuration.retrievalFilter());
        return configuration.withLearnerStateSnapshot(learnerStateSnapshot)
                .withDependencyGraphSnapshot(EducationDependencyGraphSnapshotCodec.encode(graph));
    }

    /**
     * 教师课程助手不属于某个学习者，不应被迫创建虚假的学习者画像。它仍然必须
     * 绑定教师自己拥有的进行中课程，并沿用同一套课程资料、版本和知识点边界。
     */
    private EducationRunConfiguration resolveTeacherCourseConfiguration(String tenantId, String userId,
                                                                         EducationRunOptions options) {
        if (courseRepository == null || options.courseId() == null || options.courseId().isBlank()
                || hasLearnerBinding(options)) {
            return null;
        }
        EducationCourse course = courseRepository.findByTenantIdAndId(tenantId, clean(options.courseId()))
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "EDUCATION_COURSE_NOT_FOUND", "课程实例不存在"));
        if (!course.getOwnerUserId().equals(userId)) {
            return null;
        }
        if (!course.isActive()) {
            throw new BusinessException(HttpStatus.CONFLICT, "EDUCATION_COURSE_NOT_ACTIVE",
                    "只有进行中的课程实例可以创建教育 Run");
        }
        if (!sameOrUnspecified(options.subject(), course.getSubject())
                || !sameOrUnspecified(options.gradeLevel(), course.getGradeLevel())
                || !sameOrUnspecified(options.curriculumVersion(), course.getCurriculumVersion())) {
            throw new BusinessException(HttpStatus.CONFLICT, "EDUCATION_COURSE_CONTEXT_MISMATCH",
                    "请求中的学科、年级或课程版本与当前课程不一致");
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
        String conceptKey = clean(options.conceptKey());
        String retrievalStrategy = normalizeRetrievalStrategy(options.effectiveRetrievalStrategy());
        EducationRunConfiguration configuration = new EducationRunConfiguration(
                true, null, null, null, null, null, null, null, null,
                0.0, 0.0, clean(course.getSubject()), clean(course.getGradeLevel()),
                clean(course.getCurriculumVersion()), conceptKey, minDifficulty, maxDifficulty,
                pedagogicalMode, "", course.getId(), course.getCode(), course.getTitle(), retrievalStrategy);
        requireKnowledgeSource(tenantId, userId, configuration.retrievalFilter());
        EducationDependencyGraph graph = knowledgeService == null
                ? EducationDependencyGraph.empty(configuration.conceptKey())
                : knowledgeService.resolveDependencyGraph(tenantId, configuration.retrievalFilter());
        return configuration.withDependencyGraphSnapshot(EducationDependencyGraphSnapshotCodec.encode(graph));
    }

    private boolean hasLearnerBinding(EducationRunOptions options) {
        return hasText(options.learnerProfileId())
                || hasText(options.learningGoalId())
                || hasText(options.learningAssignmentId())
                || hasText(options.reviewPlanId());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String normalizeRetrievalStrategy(String value) {
        try {
            return EducationRetrievalStrategy.valueOf(value).name();
        } catch (IllegalArgumentException ignored) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "EDUCATION_RETRIEVAL_STRATEGY_INVALID",
                    "不支持的教育检索策略: " + value);
        }
    }

    /**
     * 教育 Run 不能在没有课程证据的情况下退化为通用聊天。
     * 旧的轻量构造器保留给组件测试和历史扩展调用方，Spring 运行时始终会注入知识服务。
     */
    private void requireKnowledgeSource(String tenantId, String userId, EducationRetrievalFilter filter) {
        if (knowledgeService == null || knowledgeService.hasVisibleMatchingSource(tenantId, userId, filter)) {
            return;
        }
        throw new BusinessException(HttpStatus.CONFLICT, "EDUCATION_KNOWLEDGE_SOURCE_REQUIRED",
                "当前课程约束下没有可检索的课程知识来源，请先绑定匹配课程版本、知识点和难度的资料");
    }

    private EducationCourse resolveCourse(String tenantId, String userId, String requestedCourseId,
                                           LearningAssignment assignment) {
        String requested = clean(requestedCourseId);
        String assignmentCourseId = assignment == null ? null : clean(assignment.getCourseId());
        if (requested != null && assignmentCourseId != null && !requested.equals(assignmentCourseId)) {
            throw new BusinessException(HttpStatus.CONFLICT, "EDUCATION_COURSE_ASSIGNMENT_MISMATCH",
                    "所选课程与课程作业不一致");
        }
        if (requested != null && assignment != null && assignmentCourseId == null) {
            throw new BusinessException(HttpStatus.CONFLICT, "EDUCATION_COURSE_ASSIGNMENT_MISMATCH",
                    "未绑定课程实例的课程作业不能放入指定课程会话");
        }
        String effectiveCourseId = assignmentCourseId == null ? requested : assignmentCourseId;
        if (effectiveCourseId == null) return null;
        if (courseRepository == null || enrollmentRepository == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "EDUCATION_COURSE_UNAVAILABLE",
                    "当前运行环境未启用课程实例存储");
        }
        EducationCourse course = courseRepository.findByTenantIdAndId(tenantId, effectiveCourseId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "EDUCATION_COURSE_NOT_FOUND", "课程实例不存在"));
        if (!course.isActive()) {
            throw new BusinessException(HttpStatus.CONFLICT, "EDUCATION_COURSE_NOT_ACTIVE",
                    "只有进行中的课程实例可以创建教育 Run");
        }
        if (!course.getOwnerUserId().equals(userId)
                && !enrollmentRepository.existsByTenantIdAndCourseIdAndLearnerUserIdAndStatus(
                tenantId, course.getId(), userId, EducationEnrollmentStatus.ACTIVE)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "EDUCATION_COURSE_ENROLLMENT_REQUIRED",
                    "学习者不是该课程的活跃名单成员");
        }
        return course;
    }

    private boolean sameContext(String left, String right) {
        return left != null && right != null && left.trim().equalsIgnoreCase(right.trim());
    }

    private LearningAssignmentFeedback latestOpenIntervention(String tenantId, String userId,
                                                              LearningAssignment assignment) {
        if (assignment == null || feedbackRepository == null) return null;
        return feedbackRepository.findByTenantIdAndLearningAssignmentIdOrderByCreatedAtDesc(
                        tenantId, assignment.getId(), PageRequest.of(0, 100)).stream()
                .filter(feedback -> userId.equals(feedback.getLearnerUserId()))
                .filter(feedback -> feedback.getStatus() == LearningAssignmentFeedbackStatus.OPEN
                        || feedback.getStatus() == LearningAssignmentFeedbackStatus.ACKNOWLEDGED)
                .filter(feedback -> feedback.getAction() == LearningAssignmentFeedbackAction.REQUEST_EVIDENCE
                        || feedback.getAction() == LearningAssignmentFeedbackAction.RECOMMEND_RETRY)
                .findFirst().orElse(null);
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
        assignment = refreshOverdueState(assignment);
        validateAssignmentState(tenantId, userId, assignment);
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

    private void validateAssignmentState(String tenantId, String userId, LearningAssignment assignment) {
        if (assignment == null) return;
        if (assignment.getStatus() == LearningAssignmentStatus.ASSIGNED) {
            throw new BusinessException(HttpStatus.CONFLICT, "LEARNING_ASSIGNMENT_NOT_ACCEPTED",
                    "课程作业尚未被学习者接受，不能创建教育 Run");
        }
        if (assignment.getStatus() == LearningAssignmentStatus.OVERDUE) {
            if (hasEffectiveIntervention(tenantId, userId, assignment)) return;
            throw new BusinessException(HttpStatus.CONFLICT, "LEARNING_ASSIGNMENT_OVERDUE",
                    "课程作业已逾期，必须先由教师重新安排截止时间或发起明确的重试干预");
        }
        if (assignment.getStatus() == LearningAssignmentStatus.CANCELLED) {
            throw new BusinessException(HttpStatus.CONFLICT, "LEARNING_ASSIGNMENT_CANCELLED",
                    "已取消的课程作业不能创建教育 Run");
        }
    }

    private LearningAssignment refreshOverdueState(LearningAssignment assignment) {
        if (assignment == null || !assignment.isOverdue(java.time.Instant.now())) return assignment;
        assignment.markOverdue(java.time.Instant.now());
        return assignmentRepository.save(assignment);
    }

    private boolean hasEffectiveIntervention(String tenantId, String userId,
                                             LearningAssignment assignment) {
        if (feedbackRepository == null || assignment == null) return false;
        return feedbackRepository.findByTenantIdAndLearningAssignmentIdOrderByCreatedAtDesc(
                        tenantId, assignment.getId(), PageRequest.of(0, 100)).stream()
                .filter(feedback -> userId.equals(feedback.getLearnerUserId()))
                .filter(feedback -> feedback.getStatus() == LearningAssignmentFeedbackStatus.OPEN
                        || feedback.getStatus() == LearningAssignmentFeedbackStatus.ACKNOWLEDGED)
                .anyMatch(feedback -> feedback.getAction() == LearningAssignmentFeedbackAction.REQUEST_EVIDENCE
                        || feedback.getAction() == LearningAssignmentFeedbackAction.RECOMMEND_RETRY);
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

    private String masterySnapshot(String tenantId, String profileId) {
        return LearnerStateSnapshotCodec.encode(masteryRepository
                .findByTenantIdAndLearnerProfileIdOrderByConceptKeyAsc(tenantId, profileId));
    }

    private boolean sameOrUnspecified(String requested, String expected) {
        String normalized = clean(requested);
        return normalized == null || sameContext(normalized, expected);
    }

    private String clean(String value) {
        String result = sanitizer.sanitize(value == null ? "" : value.trim());
        return result.isBlank() ? null : result;
    }

    private Integer bound(Integer value) {
        return value == null ? null : Math.max(1, Math.min(5, value));
    }
}
