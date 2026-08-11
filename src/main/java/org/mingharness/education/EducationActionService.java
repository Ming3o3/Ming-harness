package org.mingharness.education;

import org.mingharness.common.BusinessException;
import org.mingharness.conversation.ConversationService;
import org.mingharness.conversation.api.ConversationDetail;
import org.mingharness.conversation.api.CreateConversationRequest;
import org.mingharness.conversation.api.SendConversationMessageRequest;
import org.mingharness.education.api.EducationRunOptions;
import org.mingharness.education.api.ExecuteLearningActionRequest;
import org.mingharness.education.api.LearningRecommendationView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/** 将推荐动作提交为真实聊天消息和教育 Run，避免推荐停留在不可执行的文本层。 */
@Service
public class EducationActionService {

    private final LearningGoalService goalService;
    private final LearningRecommendationService recommendationService;
    private final LearnerProfileRepository profileRepository;
    private final ConversationService conversationService;
    private final LearningReviewPlanService reviewPlanService;

    public EducationActionService(LearningGoalService goalService,
                                  LearningRecommendationService recommendationService,
                                  LearnerProfileRepository profileRepository,
                                  ConversationService conversationService) {
        this(goalService, recommendationService, profileRepository, conversationService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public EducationActionService(LearningGoalService goalService,
                                  LearningRecommendationService recommendationService,
                                  LearnerProfileRepository profileRepository,
                                  ConversationService conversationService,
                                  LearningReviewPlanService reviewPlanService) {
        this.goalService = goalService;
        this.recommendationService = recommendationService;
        this.profileRepository = profileRepository;
        this.conversationService = conversationService;
        this.reviewPlanService = reviewPlanService;
    }

    @Transactional
    public ConversationDetail execute(String tenantId, String userId, String goalId,
                                      ExecuteLearningActionRequest request,
                                      String permissions, String idempotencyKey) {
        LearningGoal goal = goalService.get(tenantId, userId, goalId);
        LearningReviewPlan reviewPlan = null;
        if (goal.getStatus() == LearningGoalStatus.COMPLETED) {
            if (reviewPlanService == null) {
                throw new BusinessException(HttpStatus.CONFLICT, "LEARNING_REVIEW_UNAVAILABLE",
                        "当前运行环境未启用保持度复习计划");
            }
            reviewPlan = reviewPlanService.ensureForCompletedGoal(goal);
            if (!reviewPlan.isDue(java.time.Instant.now())) {
                throw new BusinessException(HttpStatus.CONFLICT, "LEARNING_REVIEW_NOT_DUE",
                        "下一次保持度复习尚未到期");
            }
        } else if (goal.getStatus() != LearningGoalStatus.ACTIVE) {
            throw new BusinessException(HttpStatus.CONFLICT, "LEARNING_GOAL_ACTION_NOT_AVAILABLE",
                    "已暂停或已归档的学习目标不能继续执行学习动作");
        }
        LearningRecommendationView recommendation = recommendationService.recommend(tenantId, userId, goalId);
        if ("WAIT".equalsIgnoreCase(recommendation.nextActionType())) {
            throw new BusinessException(HttpStatus.CONFLICT, "LEARNING_REVIEW_NOT_DUE",
                    "下一次保持度复习尚未到期");
        }
        LearnerProfile profile = profileRepository.findByIdAndTenantIdAndUserId(
                        goal.getLearnerProfileId(), tenantId, userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "LEARNER_PROFILE_NOT_FOUND", "学习目标绑定的学习者画像不存在"));

        String conversationId = normalize(request == null ? null : request.conversationId());
        if (conversationId == null) {
            ConversationDetail created = conversationService.create(tenantId, userId,
                    new CreateConversationRequest("学习目标：" + goal.getTitle()));
            conversationId = created.conversation().id();
        }

        EducationRunOptions education = new EducationRunOptions(
                true, profile.getId(), goal.getId(),
                normalize(request == null ? null : request.learningAssignmentId()),
                reviewPlan == null ? null : reviewPlan.getId(),
                profile.getSubject(), profile.getGradeLevel(),
                profile.getCurriculumVersion(), goal.getConceptKey(), null, null,
                pedagogicalMode(recommendation.nextActionType()),
                normalize(request == null ? null : request.courseId()));
        SendConversationMessageRequest message = new SendConversationMessageRequest(
                recommendation.nextActionPrompt(), normalize(request == null ? null : request.modelName()),
                request == null ? 1_000 : request.effectiveMaxTurns(), List.of(), education);
        return conversationService.send(conversationId, tenantId, userId, message,
                normalize(idempotencyKey), permissions);
    }

    private String pedagogicalMode(String actionType) {
        String normalized = actionType == null ? "" : actionType.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "DIAGNOSE" -> "DIAGNOSE";
            case "EXPLAIN" -> "EXPLAIN";
            case "PRACTICE", "ASSESS", "REVIEW" -> "PRACTICE";
            default -> "AUTO";
        };
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
