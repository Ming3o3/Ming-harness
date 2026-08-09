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

    public EducationActionService(LearningGoalService goalService,
                                  LearningRecommendationService recommendationService,
                                  LearnerProfileRepository profileRepository,
                                  ConversationService conversationService) {
        this.goalService = goalService;
        this.recommendationService = recommendationService;
        this.profileRepository = profileRepository;
        this.conversationService = conversationService;
    }

    @Transactional
    public ConversationDetail execute(String tenantId, String userId, String goalId,
                                      ExecuteLearningActionRequest request,
                                      String permissions, String idempotencyKey) {
        LearningGoal goal = goalService.get(tenantId, userId, goalId);
        if (goal.getStatus() != LearningGoalStatus.ACTIVE) {
            throw new BusinessException(HttpStatus.CONFLICT, "LEARNING_GOAL_ACTION_NOT_AVAILABLE",
                    "已完成或已暂停的学习目标不能继续写入形成性测评，请先创建新的复习目标");
        }
        LearningRecommendationView recommendation = recommendationService.recommend(tenantId, userId, goalId);
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
                true, profile.getId(), goal.getId(), profile.getSubject(), profile.getGradeLevel(),
                profile.getCurriculumVersion(), goal.getConceptKey(), null, null,
                pedagogicalMode(recommendation.nextActionType()));
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
            case "PRACTICE", "ASSESS" -> "PRACTICE";
            default -> "AUTO";
        };
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
