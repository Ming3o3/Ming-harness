package org.mingharness.education;

import org.mingharness.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** 管理目标完成后的保持度计划，并保证复习计划只属于原目标和画像。 */
@Service
public class LearningReviewPlanService {

    private final LearningReviewPlanRepository repository;

    public LearningReviewPlanService(LearningReviewPlanRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public LearningReviewPlan ensureForCompletedGoal(LearningGoal goal) {
        if (goal == null || goal.getStatus() != LearningGoalStatus.COMPLETED) {
            throw new BusinessException(HttpStatus.CONFLICT, "LEARNING_REVIEW_GOAL_NOT_COMPLETED",
                    "只有已完成的学习目标才能建立保持度复习计划");
        }
        LearningReviewPlan existing = repository.findByTenantIdAndUserIdAndLearningGoalId(
                goal.getTenantId(), goal.getUserId(), goal.getId()).orElse(null);
        if (existing == null) {
            return repository.save(new LearningReviewPlan(
                    goal.getTenantId(), goal.getUserId(), goal.getId(), goal.getLearnerProfileId(),
                    goal.getConceptKey(), goal.getCompletedAt() == null
                            ? Instant.now() : goal.getCompletedAt()));
        }
        if (goal.getRevisionCount() > 0 && existing.getReviewCount() > 0) {
            existing.restartFromCompletion(goal.getCompletedAt());
            return repository.save(existing);
        }
        return existing;
    }

    @Transactional(readOnly = true)
    public LearningReviewPlan find(String tenantId, String userId, String goalId) {
        return repository.findByTenantIdAndUserIdAndLearningGoalId(tenantId, userId, goalId).orElse(null);
    }

    @Transactional(readOnly = true)
    public LearningReviewPlan getById(String tenantId, String userId, String reviewPlanId) {
        return repository.findByIdAndTenantIdAndUserId(reviewPlanId, tenantId, userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "LEARNING_REVIEW_PLAN_NOT_FOUND", "保持度复习计划不存在"));
    }

    @Transactional
    public LearningReviewPlan recordReview(String tenantId, String userId, String reviewPlanId,
                                           boolean correct, Instant reviewedAt) {
        LearningReviewPlan plan = getById(tenantId, userId, reviewPlanId);
        if (plan.getStatus() != LearningReviewPlanStatus.ACTIVE) {
            throw new BusinessException(HttpStatus.CONFLICT, "LEARNING_REVIEW_PLAN_NOT_ACTIVE",
                    "当前保持度复习计划不可写入");
        }
        plan.recordReview(correct, reviewedAt);
        return repository.save(plan);
    }
}
