package org.mingharness.education;

import org.mingharness.common.BusinessException;
import org.mingharness.education.api.LearningRecommendationView;
import org.mingharness.feedback.RunFeedback;
import org.mingharness.feedback.RunFeedbackRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 根据可追踪测评事实生成确定性的下一步学习动作，避免推荐变成不可解释的黑盒。 */
@Service
public class LearningRecommendationService {

    private final LearningGoalRepository goalRepository;
    private final AssessmentAttemptRepository attemptRepository;
    private final LearnerMasteryRepository masteryRepository;
    private final RunFeedbackRepository feedbackRepository;

    /** 兼容只使用教育状态的组件测试和旧扩展调用方。 */
    public LearningRecommendationService(LearningGoalRepository goalRepository,
                                         AssessmentAttemptRepository attemptRepository,
                                         LearnerMasteryRepository masteryRepository) {
        this(goalRepository, attemptRepository, masteryRepository, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public LearningRecommendationService(LearningGoalRepository goalRepository,
                                         AssessmentAttemptRepository attemptRepository,
                                         LearnerMasteryRepository masteryRepository,
                                         RunFeedbackRepository feedbackRepository) {
        this.goalRepository = goalRepository;
        this.attemptRepository = attemptRepository;
        this.masteryRepository = masteryRepository;
        this.feedbackRepository = feedbackRepository;
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
        boolean latestNegativeFeedback = latest != null && feedbackRepository != null
                && feedbackRepository.findByRunIdAndUserId(latest.getRunId(), userId)
                .map(RunFeedback::getRating)
                .map("NEGATIVE"::equalsIgnoreCase)
                .orElse(false);

        String actionType;
        String actionTitle;
        String prompt;
        String rationale;
        if (goal.getStatus() == LearningGoalStatus.COMPLETED || gap <= 0.0001) {
            actionType = "REVIEW";
            actionTitle = "巩固并迁移应用";
            prompt = "请围绕“" + goal.getConceptKey() + "”设计一道迁移题，要求我解释解题依据并说明它与已学内容的联系。";
            rationale = "当前掌握度已达到目标，下一步应验证跨题型迁移和保持度。";
        } else if (attempts.isEmpty()) {
            actionType = "DIAGNOSE";
            actionTitle = "先做一次基线诊断";
            prompt = "请先围绕“" + goal.getConceptKey() + "”给我安排一组短小的基线诊断题，不要直接给出答案，并根据作答定位薄弱点。";
            rationale = "目标还没有形成测评记录，先建立可比较的基线才能选择合适教学策略。";
        } else if (latestNegativeFeedback) {
            actionType = "EXPLAIN";
            actionTitle = "根据反馈调整教学方式";
            prompt = "我对上一轮“" + goal.getConceptKey()
                    + "”学习结果反馈为需要调整。请换一种讲解方式，先确认我卡住的原因，再安排一道低难度检查题。";
            rationale = "上一轮学习结果收到负向反馈，下一步先调整表达和节奏，再重新检查理解。";
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
                attempts.size(), correct, latest == null ? null : latest.getCreatedAt(), actionType,
                actionTitle, prompt, rationale);
    }

    private double clamp(double value) {
        if (!Double.isFinite(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
    }
}
