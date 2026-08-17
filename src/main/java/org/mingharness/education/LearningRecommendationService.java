package org.mingharness.education;

import org.mingharness.common.BusinessException;
import org.mingharness.education.api.LearningRecommendationView;
import org.mingharness.feedback.RunFeedback;
import org.mingharness.feedback.RunFeedbackRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/** 根据可追踪测评事实生成确定性的下一步学习动作，避免推荐变成不可解释的黑盒。 */
@Service
public class LearningRecommendationService {

    private final LearningGoalRepository goalRepository;
    private final AssessmentAttemptRepository attemptRepository;
    private final LearnerMasteryRepository masteryRepository;
    private final RunFeedbackRepository feedbackRepository;
    private final LearningReviewPlanService reviewPlanService;
    private final LearnerProfileRepository profileRepository;
    private final EducationKnowledgeGraphService graphService;

    /** 兼容只使用教育状态的组件测试和旧扩展调用方。 */
    public LearningRecommendationService(LearningGoalRepository goalRepository,
                                         AssessmentAttemptRepository attemptRepository,
                                         LearnerMasteryRepository masteryRepository) {
        this(goalRepository, attemptRepository, masteryRepository, null, null, null, null);
    }

    /** 兼容已有反馈回流测试和旧扩展调用方。 */
    public LearningRecommendationService(LearningGoalRepository goalRepository,
                                         AssessmentAttemptRepository attemptRepository,
                                         LearnerMasteryRepository masteryRepository,
                                         RunFeedbackRepository feedbackRepository) {
        this(goalRepository, attemptRepository, masteryRepository, feedbackRepository, null, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public LearningRecommendationService(LearningGoalRepository goalRepository,
                                         AssessmentAttemptRepository attemptRepository,
                                         LearnerMasteryRepository masteryRepository,
                                         RunFeedbackRepository feedbackRepository,
                                         LearningReviewPlanService reviewPlanService,
                                         LearnerProfileRepository profileRepository,
                                         EducationKnowledgeGraphService graphService) {
        this.goalRepository = goalRepository;
        this.attemptRepository = attemptRepository;
        this.masteryRepository = masteryRepository;
        this.feedbackRepository = feedbackRepository;
        this.reviewPlanService = reviewPlanService;
        this.profileRepository = profileRepository;
        this.graphService = graphService;
    }

    /** 兼容已接入保持度复习、但尚未接入依赖图推荐的组件测试和扩展调用方。 */
    public LearningRecommendationService(LearningGoalRepository goalRepository,
                                         AssessmentAttemptRepository attemptRepository,
                                         LearnerMasteryRepository masteryRepository,
                                         RunFeedbackRepository feedbackRepository,
                                         LearningReviewPlanService reviewPlanService) {
        this(goalRepository, attemptRepository, masteryRepository, feedbackRepository, reviewPlanService,
                null, null);
    }

    @Transactional(readOnly = true)
    public LearningRecommendationView recommend(String tenantId, String userId, String goalId) {
        LearningGoal goal = goalRepository.findByIdAndTenantIdAndUserId(goalId, tenantId, userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "LEARNING_GOAL_NOT_FOUND", "学习目标不存在"));
        List<AssessmentAttempt> attempts = attemptRepository
                .findByTenantIdAndUserIdAndLearningGoalIdOrderByCreatedAtAsc(tenantId, userId, goalId);
        AssessmentAttempt latest = attempts.isEmpty() ? null : attempts.get(attempts.size() - 1);
        double current = masteryRepository
                .findByTenantIdAndLearnerProfileIdAndConceptKey(
                        tenantId, goal.getLearnerProfileId(), goal.getConceptKey())
                .map(LearnerMastery::getMasteryScore)
                .orElse(goal.getBaselineMastery());
        double gap = Math.max(0.0, goal.getTargetMastery() - current);
        double denominator = Math.max(0.0001, goal.getTargetMastery() - goal.getBaselineMastery());
        double progress = clamp((current - goal.getBaselineMastery()) / denominator);
        long correct = attempts.stream().filter(AssessmentAttempt::isCorrect).count();
        CodeDiagnosticCategory latestCodeDiagnostic = latestCodeDiagnosticCategory(latest);
        DependencyRecommendation dependency = dependencyRecommendation(tenantId, userId, goal);
        LearningReviewPlan reviewPlan = goal.getStatus() == LearningGoalStatus.COMPLETED
                && reviewPlanService != null ? reviewPlanService.find(tenantId, userId, goalId) : null;
        java.time.Instant now = java.time.Instant.now();
        boolean latestNegativeFeedback = latest != null && feedbackRepository != null
                && feedbackRepository.findByRunIdAndUserId(latest.getRunId(), userId)
                .map(RunFeedback::getRating)
                .map("NEGATIVE"::equalsIgnoreCase)
                .orElse(false);

        String actionType;
        String actionTitle;
        String prompt;
        String rationale;
        if (goal.isRevisionPending()) {
            actionType = "REVISION";
            actionTitle = "按教师反馈返工作业";
            prompt = "教师已将“" + goal.getConceptKey()
                    + "”退回返工。请先复述本次返工要求，再针对薄弱点重新讲解并安排一道形成性检查题，最后让我给出可核验的作答依据。";
            rationale = "教师退回后必须先完成返工并重新形成测评证据，不能直接沿用上一轮达标结论。";
        } else if (goal.getStatus() == LearningGoalStatus.COMPLETED || gap <= 0.0001) {
            if (reviewPlan != null && !reviewPlan.isDue(now)) {
                actionType = "WAIT";
                actionTitle = "等待下一次保持度复习";
                prompt = "本目标已完成。下一次保持度复习时间为 " + reviewPlan.getNextReviewAt()
                        + "，到期后再开始复习。";
                rationale = "本轮复习已完成，系统按间隔计划安排下一次复习，避免把一次达标当作长期保持。";
            } else {
                actionType = "REVIEW";
                actionTitle = reviewPlan != null && Boolean.FALSE.equals(reviewPlan.getLastReviewCorrect())
                        ? "修复保持度并重新迁移" : "巩固并迁移应用";
                prompt = reviewPlan != null && Boolean.FALSE.equals(reviewPlan.getLastReviewCorrect())
                        ? "请先回顾“" + goal.getConceptKey()
                        + "”上次复习暴露的薄弱点，再设计一道迁移题，要求我解释解题依据。"
                        : "请围绕“" + goal.getConceptKey() + "”设计一道迁移题，要求我解释解题依据并说明它与已学内容的联系。";
                rationale = "当前目标已达标，下一步验证跨题型迁移和长期保持度。";
            }
        } else if (attempts.isEmpty()) {
            if (dependency.hasGap()) {
                actionType = "PREREQUISITE_DIAGNOSE";
                actionTitle = "先诊断关键前置知识";
                prompt = prerequisitePrompt(goal, dependency, true);
                rationale = prerequisiteRationale(dependency,
                        "当前目标还没有测评记录，先沿依赖图检查关键前置知识，再建立目标基线。");
            } else {
                actionType = "DIAGNOSE";
                actionTitle = "先做一次基线诊断";
                prompt = "请先围绕“" + goal.getConceptKey() + "”给我安排一组短小的基线诊断题，不要直接给出答案，并根据作答定位薄弱点。";
                rationale = "目标还没有形成测评记录，先建立可比较的基线才能选择合适教学策略。";
            }
        } else if (latestNegativeFeedback) {
            actionType = "EXPLAIN";
            actionTitle = "根据反馈调整教学方式";
            prompt = "我对上一轮“" + goal.getConceptKey()
                    + "”学习结果反馈为需要调整。请换一种讲解方式，先确认我卡住的原因，再安排一道低难度检查题。";
            rationale = "上一轮学习结果收到负向反馈，下一步先调整表达和节奏，再重新检查理解。";
        } else if (latestCodeDiagnostic != CodeDiagnosticCategory.NONE) {
            actionType = "DIAGNOSE";
            actionTitle = "针对代码" + codeDiagnosticLabel(latestCodeDiagnostic) + "进行诊断";
            prompt = codeDiagnosticPrompt(goal, latestCodeDiagnostic, dependency);
            rationale = "上一轮代码评测只提供了低权重形成性线索（"
                    + codeDiagnosticLabel(latestCodeDiagnostic)
                    + "），下一步先通过解释、最小修改和独立测试确认真正原因，再更新掌握度。";
        } else if (dependency.hasCriticalGap()
                && (latest != null && !latest.isCorrect() || correct * 2 < attempts.size())) {
            actionType = "PREREQUISITE_REMEDIATION";
            actionTitle = "补强最薄弱的前置知识";
            prompt = prerequisitePrompt(goal, dependency, false);
            rationale = prerequisiteRationale(dependency,
                    "最近的学习证据显示目标仍不稳定，系统先修复依赖图上的关键缺口，避免直接重复目标题。");
        } else if (latest != null && !latest.isCorrect()) {
            actionType = "EXPLAIN";
            actionTitle = "针对最近错误重新讲解";
            prompt = "请针对我在“" + goal.getConceptKey() + "”上的最近错误，先解释错误原因，再给一个由易到难的纠正练习。";
            rationale = "最近一次测评未通过，优先修正误解，再进入下一轮练习。";
        } else if (correct * 2 < attempts.size()) {
            actionType = "PRACTICE";
            actionTitle = "补充基础练习";
            prompt = "请围绕“" + goal.getConceptKey() + "”安排三道分层练习，先检查关键前置知识，再逐步提高难度。";
            rationale = "历史正确率低于一半，继续测评前需要通过分层练习稳定基础。";
        } else {
            actionType = "ASSESS";
            actionTitle = "进行下一次形成性测评";
            prompt = "请围绕“" + goal.getConceptKey() + "”给我安排下一次形成性测评，只在我作答后反馈，并记录掌握度变化。";
            rationale = "掌握度仍有 " + String.format(java.util.Locale.ROOT, "%.0f%%", gap * 100)
                    + " 的目标差距，继续用测评确认是否达到目标。";
        }
        return new LearningRecommendationView(goal.getId(), goal.getTitle(), goal.getStatus().name(),
                goal.getConceptKey(), current, goal.getBaselineMastery(), goal.getTargetMastery(), progress,
                attempts.size(), correct, latest == null ? null : latest.getCreatedAt(),
                reviewPlan == null ? null : reviewPlan.getId(),
                reviewPlan == null ? null : reviewPlan.getStatus().name(),
                reviewPlan == null ? null : reviewPlan.getNextReviewAt(),
                reviewPlan == null ? 0 : reviewPlan.getIntervalDays(),
                reviewPlan == null ? 0 : reviewPlan.getReviewCount(),
                reviewPlan == null ? 0 : reviewPlan.getSuccessfulReviewCount(),
                actionType, actionTitle, prompt, rationale,
                dependency.priorityConcept(), dependency.priorityMastery(), dependency.priorityDeficit(),
                dependency.graphAvailable(), dependency.graphTruncated());
    }

    /**
     * 把当前掌握度快照投影到目标的依赖图，选择“缺口最大、且更基础”的前置知识。
     * 依赖图不可用时返回空结果，保持旧课程和旧测试的确定性行为。
     */
    private DependencyRecommendation dependencyRecommendation(String tenantId, String userId,
                                                              LearningGoal goal) {
        if (profileRepository == null || graphService == null || goal == null) {
            return DependencyRecommendation.empty();
        }
        LearnerProfile profile = profileRepository.findByIdAndTenantIdAndUserId(
                goal.getLearnerProfileId(), tenantId, userId).orElse(null);
        if (profile == null) return DependencyRecommendation.empty();
        Map<String, Double> masteryScores = new java.util.LinkedHashMap<>();
        Map<String, LearnerStateEvidence> masteryEvidence = new java.util.LinkedHashMap<>();
        masteryRepository.findByTenantIdAndLearnerProfileIdOrderByConceptKeyAsc(
                        tenantId, profile.getId())
                .forEach(item -> {
                    masteryScores.put(item.getConceptKey(), item.getMasteryScore());
                    masteryEvidence.put(item.getConceptKey(), new LearnerStateEvidence(
                            item.getMasteryScore(), item.getAttempts(), item.getCorrectAttempts()));
                });
        EducationRetrievalFilter filter = new EducationRetrievalFilter(
                profile.getSubject(), profile.getGradeLevel(), profile.getCurriculumVersion(),
                goal.getConceptKey(), null, null, masteryScores, null, masteryEvidence);
        EducationDependencyGraph graph = graphService.resolve(tenantId, filter);
        if (graph == null || graph.prerequisites().isEmpty()) {
            return new DependencyRecommendation(null, 0.0, 0.0, false,
                    graph != null && graph.truncated());
        }
        EducationDependencyPath priority = graph.prerequisites().stream()
                .filter(path -> path.deficit() > 0.05)
                .sorted(java.util.Comparator.comparingDouble(EducationDependencyPath::deficit).reversed()
                        .thenComparing(java.util.Comparator.comparingInt(EducationDependencyPath::depth).reversed())
                        .thenComparing(EducationDependencyPath::conceptKey,
                                String.CASE_INSENSITIVE_ORDER))
                .findFirst().orElse(null);
        if (priority == null) {
            return new DependencyRecommendation(null, 0.0, 0.0, true, graph.truncated());
        }
        return new DependencyRecommendation(priority.conceptKey(), priority.masteryScore(),
                priority.deficit(), true, graph.truncated());
    }

    private String prerequisitePrompt(LearningGoal goal, DependencyRecommendation dependency,
                                      boolean diagnosis) {
        String verb = diagnosis ? "先安排一道短小的诊断题" : "先用一个由易到难的练习补强";
        return "当前目标“" + goal.getConceptKey() + "”依赖前置知识“" + dependency.priorityConcept()
                + "”。系统估计该前置知识掌握度约为 " + percent(dependency.priorityMastery())
                + "，请" + verb + "，让我解释思路并给出可核验的作答依据，再回到目标知识点。";
    }

    private String prerequisiteRationale(DependencyRecommendation dependency, String prefix) {
        return prefix + "依赖图优先级为“" + dependency.priorityConcept() + "”，当前掌握度约 "
                + percent(dependency.priorityMastery()) + "，缺口约 " + percent(dependency.priorityDeficit()) + "。";
    }

    private CodeDiagnosticCategory latestCodeDiagnosticCategory(AssessmentAttempt attempt) {
        if (attempt == null || attempt.getQuestionType() == null
                || !attempt.getQuestionType().startsWith("CODE_")) {
            return CodeDiagnosticCategory.NONE;
        }
        String raw = attempt.getQuestionType().substring("CODE_".length());
        try {
            CodeDiagnosticCategory category = CodeDiagnosticCategory.valueOf(raw);
            return category == CodeDiagnosticCategory.NONE ? CodeDiagnosticCategory.NONE : category;
        } catch (IllegalArgumentException ignored) {
            return CodeDiagnosticCategory.UNKNOWN;
        }
    }

    private String codeDiagnosticPrompt(LearningGoal goal, CodeDiagnosticCategory category,
                                        DependencyRecommendation dependency) {
        String focus = switch (category) {
            case SYNTAX -> "定位语法规则和标点导致的解析失败";
            case STRUCTURE -> "检查缩进、括号或代码块结构";
            case IDENTIFIER -> "追踪变量、函数或类的声明与作用域";
            case TYPE -> "追踪每个表达式的类型，并解释类型转换是否成立";
            case DEPENDENCY -> "检查模块、包或导入关系，并说明运行环境需要什么依赖";
            case COMPILATION -> "逐条解释编译器诊断，并定位最小可修复位置";
            case TIMEOUT -> "检查循环终止条件和时间复杂度";
            case UNKNOWN -> "阅读评测诊断，提出可验证的错误原因";
            case NONE -> "复核代码思路";
        };
        String prerequisite = dependency.hasGap()
                ? "如果问题涉及前置知识“" + dependency.priorityConcept() + "”，先用一句话解释它再修改代码。"
                : "不要只把代码改到能通过；先用自己的话解释原因。";
        return "请围绕学习目标“" + goal.getConceptKey() + "”做一次代码错误诊断："
                + focus + "。先指出最小可疑位置，再给出最小修改，最后设计一个独立测试验证修改是否有效。"
                + prerequisite;
    }

    private String codeDiagnosticLabel(CodeDiagnosticCategory category) {
        return switch (category) {
            case SYNTAX -> "语法错误";
            case STRUCTURE -> "结构/缩进错误";
            case IDENTIFIER -> "标识符错误";
            case TYPE -> "类型错误";
            case DEPENDENCY -> "依赖/导入错误";
            case COMPILATION -> "编译错误";
            case TIMEOUT -> "超时风险";
            case UNKNOWN -> "未归类错误";
            case NONE -> "问题";
        };
    }

    private String percent(double value) {
        return String.format(java.util.Locale.ROOT, "%.0f%%", clamp(value) * 100.0);
    }

    private record DependencyRecommendation(String priorityConcept, double priorityMastery,
                                             double priorityDeficit, boolean graphAvailable,
                                             boolean graphTruncated) {
        private static DependencyRecommendation empty() {
            return new DependencyRecommendation(null, 0.0, 0.0, false, false);
        }

        private boolean hasGap() {
            return priorityConcept != null && priorityDeficit > 0.05;
        }

        private boolean hasCriticalGap() {
            return hasGap() && priorityDeficit >= 0.40;
        }
    }

    @Transactional(readOnly = true)
    public LearningReviewPlan reviewPlan(String tenantId, String userId, String goalId) {
        goalRepository.findByIdAndTenantIdAndUserId(goalId, tenantId, userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "LEARNING_GOAL_NOT_FOUND", "学习目标不存在"));
        return reviewPlanService == null ? null : reviewPlanService.find(tenantId, userId, goalId);
    }

    private double clamp(double value) {
        if (!Double.isFinite(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
    }
}
