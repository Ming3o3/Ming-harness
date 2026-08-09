package org.mingharness.education;

import org.junit.jupiter.api.Test;
import org.mingharness.common.BusinessException;
import org.mingharness.conversation.ConversationService;
import org.mingharness.conversation.api.ConversationDetail;
import org.mingharness.conversation.api.ConversationMessageView;
import org.mingharness.conversation.api.ConversationSummary;
import org.mingharness.education.api.EducationRunOptions;
import org.mingharness.education.api.ExecuteLearningActionRequest;
import org.mingharness.education.api.LearningRecommendationView;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EducationActionServiceTests {

    @Test
    void shouldTurnRecommendationIntoBoundConversationRun() {
        LearningGoalService goals = mock(LearningGoalService.class);
        LearningRecommendationService recommendations = mock(LearningRecommendationService.class);
        LearnerProfileRepository profiles = mock(LearnerProfileRepository.class);
        ConversationService conversations = mock(ConversationService.class);
        LearnerProfile profile = new LearnerProfile("tenant-a", "student-1", "数学",
                "高中一年级", "人教A版", "掌握函数", "zh-CN");
        LearningGoal goal = new LearningGoal("tenant-a", "student-1", profile.getId(),
                "掌握函数", "函数", 0.2, 0.8);
        LearningRecommendationView recommendation = new LearningRecommendationView(
                goal.getId(), goal.getTitle(), "ACTIVE", "函数", 0.2, 0.2, 0.8,
                0.0, 0, 0, null, "DIAGNOSE", "先做一次基线诊断",
                "请先完成基线诊断。", "需要先建立基线。");
        ConversationDetail created = detail("conversation-1");
        ConversationDetail sent = detail("conversation-1");

        when(goals.get("tenant-a", "student-1", goal.getId())).thenReturn(goal);
        when(recommendations.recommend("tenant-a", "student-1", goal.getId())).thenReturn(recommendation);
        when(profiles.findByIdAndTenantIdAndUserId(profile.getId(), "tenant-a", "student-1"))
                .thenReturn(Optional.of(profile));
        when(conversations.create(eq("tenant-a"), eq("student-1"), any())).thenReturn(created);
        when(conversations.send(eq("conversation-1"), eq("tenant-a"), eq("student-1"),
                any(), eq("action-1"), eq("education.read,education.write"))).thenReturn(sent);

        EducationActionService service = new EducationActionService(goals, recommendations, profiles, conversations);
        ConversationDetail result = service.execute("tenant-a", "student-1", goal.getId(),
                new ExecuteLearningActionRequest(null, null, 4),
                "education.read,education.write", "action-1");

        assertEquals(sent, result);
        var messageCaptor = org.mockito.ArgumentCaptor.forClass(
                org.mingharness.conversation.api.SendConversationMessageRequest.class);
        verify(conversations).send(eq("conversation-1"), eq("tenant-a"), eq("student-1"),
                messageCaptor.capture(), eq("action-1"), eq("education.read,education.write"));
        EducationRunOptions education = messageCaptor.getValue().education();
        assertEquals(goal.getId(), education.learningGoalId());
        assertEquals(profile.getId(), education.learnerProfileId());
        assertEquals("DIAGNOSE", education.pedagogicalMode());
        assertEquals(4, messageCaptor.getValue().effectiveMaxTurns());
        verify(conversations).create(eq("tenant-a"), eq("student-1"), any());
    }

    @Test
    void shouldRejectExecutingCompletedGoalAction() {
        LearningGoalService goals = mock(LearningGoalService.class);
        LearningRecommendationService recommendations = mock(LearningRecommendationService.class);
        LearnerProfileRepository profiles = mock(LearnerProfileRepository.class);
        ConversationService conversations = mock(ConversationService.class);
        LearningGoal goal = new LearningGoal("tenant-a", "student-1", "profile-1",
                "掌握函数", "函数", 0.2, 0.8);
        goal.changeStatus(LearningGoalStatus.COMPLETED);
        when(goals.get("tenant-a", "student-1", goal.getId())).thenReturn(goal);

        EducationActionService service = new EducationActionService(goals, recommendations, profiles, conversations);
        BusinessException exception = assertThrows(BusinessException.class, () -> service.execute(
                "tenant-a", "student-1", goal.getId(), null,
                "education.read,education.write", "action-2"));

        assertEquals("LEARNING_REVIEW_UNAVAILABLE", exception.getCode());
        verify(recommendations, never()).recommend(any(), any(), any());
        verify(conversations, never()).send(any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldBindCompletedGoalActionToDueReviewPlan() {
        LearningGoalService goals = mock(LearningGoalService.class);
        LearningRecommendationService recommendations = mock(LearningRecommendationService.class);
        LearnerProfileRepository profiles = mock(LearnerProfileRepository.class);
        ConversationService conversations = mock(ConversationService.class);
        LearningReviewPlanService reviewPlans = mock(LearningReviewPlanService.class);
        LearnerProfile profile = new LearnerProfile("tenant-a", "student-1", "数学",
                "高中一年级", "人教A版", "掌握函数", "zh-CN");
        LearningGoal goal = new LearningGoal("tenant-a", "student-1", profile.getId(),
                "掌握函数", "函数", 0.2, 0.8);
        goal.changeStatus(LearningGoalStatus.COMPLETED);
        LearningReviewPlan plan = new LearningReviewPlan("tenant-a", "student-1", goal.getId(),
                profile.getId(), goal.getConceptKey(), java.time.Instant.now().minusSeconds(1));
        LearningRecommendationView recommendation = new LearningRecommendationView(
                goal.getId(), goal.getTitle(), "COMPLETED", "函数", 0.82, 0.2, 0.8,
                1.0, 2, 2, java.time.Instant.now(), "REVIEW", "巩固并迁移应用",
                "请完成保持度复习。", "目标已达标，需要验证长期保持。");
        ConversationDetail detail = detail("conversation-review");
        when(goals.get("tenant-a", "student-1", goal.getId())).thenReturn(goal);
        when(reviewPlans.ensureForCompletedGoal(goal)).thenReturn(plan);
        when(recommendations.recommend("tenant-a", "student-1", goal.getId())).thenReturn(recommendation);
        when(profiles.findByIdAndTenantIdAndUserId(profile.getId(), "tenant-a", "student-1"))
                .thenReturn(Optional.of(profile));
        when(conversations.create(eq("tenant-a"), eq("student-1"), any())).thenReturn(detail);
        when(conversations.send(eq("conversation-review"), eq("tenant-a"), eq("student-1"),
                any(), eq("review-1"), eq("education.read,education.write"))).thenReturn(detail);

        EducationActionService service = new EducationActionService(goals, recommendations, profiles,
                conversations, reviewPlans);
        service.execute("tenant-a", "student-1", goal.getId(), null,
                "education.read,education.write", "review-1");

        var messageCaptor = org.mockito.ArgumentCaptor.forClass(
                org.mingharness.conversation.api.SendConversationMessageRequest.class);
        verify(conversations).send(eq("conversation-review"), eq("tenant-a"), eq("student-1"),
                messageCaptor.capture(), eq("review-1"), eq("education.read,education.write"));
        assertEquals(plan.getId(), messageCaptor.getValue().education().reviewPlanId());
        assertEquals(goal.getId(), messageCaptor.getValue().education().learningGoalId());
    }

    private ConversationDetail detail(String conversationId) {
        ConversationSummary summary = new ConversationSummary(conversationId, "tenant-a", "student-1",
                "学习目标", null, Instant.now(), Instant.now(), 0, null, null);
        List<ConversationMessageView> messages = List.of();
        return new ConversationDetail(summary, messages);
    }
}
