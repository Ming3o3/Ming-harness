package org.mingharness.education;

import org.junit.jupiter.api.Test;
import org.mingharness.conversation.ConversationService;
import org.mingharness.conversation.api.ConversationDetail;
import org.mingharness.conversation.api.ConversationSummary;
import org.mingharness.education.api.DeferLearningTaskRequest;
import org.mingharness.education.api.ExecuteLearningActionRequest;
import org.mingharness.education.api.LearningRecommendationView;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LearningTaskServiceTests {

    @Test
    void shouldMaterializeEachDueReviewOccurrenceOnlyOnce() {
        LearningTaskRepository tasks = mock(LearningTaskRepository.class);
        LearningReviewPlanRepository plans = mock(LearningReviewPlanRepository.class);
        LearningReviewPlanService planService = mock(LearningReviewPlanService.class);
        LearningGoalRepository goals = mock(LearningGoalRepository.class);
        LearningRecommendationService recommendations = mock(LearningRecommendationService.class);
        EducationActionService actions = mock(EducationActionService.class);
        ConversationService conversations = mock(ConversationService.class);
        Instant now = Instant.now();
        LearningGoal goal = new LearningGoal("tenant-a", "student-1", "profile-1",
                "掌握函数", "函数", 0.2, 0.8);
        goal.changeStatus(LearningGoalStatus.COMPLETED);
        LearningReviewPlan plan = new LearningReviewPlan("tenant-a", "student-1", goal.getId(),
                "profile-1", "函数", now.minusSeconds(1));

        when(plans.findDueByStatus(eq(LearningReviewPlanStatus.ACTIVE), eq(now), any())).thenReturn(List.of(plan));
        when(plans.findByIdForUpdate(plan.getId())).thenReturn(Optional.of(plan));
        when(goals.findByIdAndTenantIdAndUserId(goal.getId(), "tenant-a", "student-1"))
                .thenReturn(Optional.of(goal));
        when(tasks.findByTenantIdAndUserIdAndReviewPlanIdAndReviewSequence(
                "tenant-a", "student-1", plan.getId(), 0)).thenReturn(Optional.empty());
        when(recommendations.recommend("tenant-a", "student-1", goal.getId()))
                .thenReturn(recommendation(goal, plan));
        when(tasks.save(any(LearningTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LearningTaskService service = new LearningTaskService(tasks, plans, planService, goals,
                recommendations, actions, conversations);
        assertEquals(1, service.materializeDueTasks(now, 100));

        ArgumentCaptor<LearningTask> captor = ArgumentCaptor.forClass(LearningTask.class);
        verify(tasks).save(captor.capture());
        LearningTask materialized = captor.getValue();
        assertEquals(LearningTaskStatus.OPEN, materialized.getStatus());
        assertEquals(plan.getId(), materialized.getReviewPlanId());
        assertEquals(0, materialized.getReviewSequence());
        assertEquals("保持度复习", materialized.getTitle());
    }

    @Test
    void shouldStartTaskAndBindConversationAndRun() {
        LearningTaskRepository tasks = mock(LearningTaskRepository.class);
        LearningReviewPlanRepository plans = mock(LearningReviewPlanRepository.class);
        LearningReviewPlanService planService = mock(LearningReviewPlanService.class);
        LearningGoalRepository goals = mock(LearningGoalRepository.class);
        LearningRecommendationService recommendations = mock(LearningRecommendationService.class);
        EducationActionService actions = mock(EducationActionService.class);
        ConversationService conversations = mock(ConversationService.class);
        LearningTask task = new LearningTask("tenant-a", "student-1", LearningTaskType.REVIEW,
                "goal-1", "plan-1", 0, "保持度复习", "请完成复习题", Instant.now().minusSeconds(1));
        LearningReviewPlan plan = new LearningReviewPlan("tenant-a", "student-1", "goal-1",
                "profile-1", "函数", Instant.now().minusSeconds(1));
        ConversationDetail detail = new ConversationDetail(
                new ConversationSummary("conversation-1", "tenant-a", "student-1", "函数复习",
                        null, Instant.now(), Instant.now(), 1, "请复习", "run-1"), List.of());
        when(tasks.findByTenantIdAndUserIdAndId("tenant-a", "student-1", task.getId()))
                .thenReturn(Optional.of(task));
        when(planService.getById("tenant-a", "student-1", "plan-1")).thenReturn(plan);
        when(actions.execute(eq("tenant-a"), eq("student-1"), eq("goal-1"), any(), any(), any()))
                .thenReturn(detail);
        when(tasks.save(any(LearningTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LearningTaskService service = new LearningTaskService(tasks, plans, planService, goals,
                recommendations, actions, conversations);
        var result = service.start("tenant-a", "student-1", task.getId(),
                new ExecuteLearningActionRequest(null, null, 1), "education.read,education.write", null);

        assertEquals("conversation-1", result.task().conversationId());
        assertEquals("run-1", result.task().runId());
        assertEquals(LearningTaskStatus.IN_PROGRESS.name(), result.task().status());
        verify(actions).execute(eq("tenant-a"), eq("student-1"), eq("goal-1"), any(), any(), any());
    }

    @Test
    void shouldDeferTaskAndAdvancePlanWithoutRecordingReview() {
        LearningTaskRepository tasks = mock(LearningTaskRepository.class);
        LearningReviewPlanRepository plans = mock(LearningReviewPlanRepository.class);
        LearningReviewPlanService planService = mock(LearningReviewPlanService.class);
        LearningGoalRepository goals = mock(LearningGoalRepository.class);
        LearningRecommendationService recommendations = mock(LearningRecommendationService.class);
        EducationActionService actions = mock(EducationActionService.class);
        ConversationService conversations = mock(ConversationService.class);
        LearningTask task = new LearningTask("tenant-a", "student-1", LearningTaskType.REVIEW,
                "goal-1", "plan-1", 0, "保持度复习", "请完成复习题", Instant.now().minusSeconds(1));
        LearningReviewPlan plan = new LearningReviewPlan("tenant-a", "student-1", "goal-1",
                "profile-1", "函数", task.getScheduledAt());
        when(tasks.findByTenantIdAndUserIdAndId("tenant-a", "student-1", task.getId()))
                .thenReturn(Optional.of(task));
        when(planService.getById("tenant-a", "student-1", "plan-1")).thenReturn(plan);
        when(tasks.save(any(LearningTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LearningTaskService service = new LearningTaskService(tasks, plans, planService, goals,
                recommendations, actions, conversations);
        LearningTask deferred = service.defer("tenant-a", "student-1", task.getId(),
                new DeferLearningTaskRequest(2));

        assertEquals(LearningTaskStatus.DEFERRED, deferred.getStatus());
        assertEquals(1, deferred.getDeferCount());
        assertNotNull(plan.getNextReviewAt());
        verify(plans).save(plan);
    }

    @Test
    void shouldCompleteTaskWhenReviewEvidenceIsRecorded() {
        LearningTaskRepository tasks = mock(LearningTaskRepository.class);
        LearningReviewPlanRepository plans = mock(LearningReviewPlanRepository.class);
        LearningReviewPlanService planService = mock(LearningReviewPlanService.class);
        LearningGoalRepository goals = mock(LearningGoalRepository.class);
        LearningRecommendationService recommendations = mock(LearningRecommendationService.class);
        EducationActionService actions = mock(EducationActionService.class);
        ConversationService conversations = mock(ConversationService.class);
        LearningTask task = new LearningTask("tenant-a", "student-1", LearningTaskType.REVIEW,
                "goal-1", "plan-1", 0, "保持度复习", "请完成复习题", Instant.now());
        task.start("conversation-1", "run-1", Instant.now());
        when(tasks.findFirstByTenantIdAndUserIdAndRunIdAndStatusIn(
                eq("tenant-a"), eq("student-1"), eq("run-1"), any())).thenReturn(Optional.of(task));
        when(tasks.save(any(LearningTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LearningTaskCompletionService completionService = new LearningTaskCompletionService(tasks);
        completionService.completeForReview("tenant-a", "student-1", "run-1", true, Instant.now());

        assertEquals(LearningTaskStatus.COMPLETED, task.getStatus());
        assertEquals(Boolean.TRUE, task.getOutcomeCorrect());
        verify(tasks).save(task);
    }

    private LearningRecommendationView recommendation(LearningGoal goal, LearningReviewPlan plan) {
        return new LearningRecommendationView(goal.getId(), goal.getTitle(), goal.getStatus().name(),
                goal.getConceptKey(), 0.8, goal.getBaselineMastery(), goal.getTargetMastery(), 1.0,
                2, 2, Instant.now(), "REVIEW", "保持度复习", "请完成复习题", "计划到期");
    }
}
