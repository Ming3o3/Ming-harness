package org.mingharness.education;

import org.mingharness.common.BusinessException;
import org.mingharness.conversation.ConversationService;
import org.mingharness.conversation.api.ConversationDetail;
import org.mingharness.education.api.DeferLearningTaskRequest;
import org.mingharness.education.api.ExecuteLearningActionRequest;
import org.mingharness.education.api.LearningRecommendationView;
import org.mingharness.education.api.LearningTaskStartView;
import org.mingharness.education.api.LearningTaskView;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;

/**
 * 将长期复习计划实例化为学习者可见的业务任务。
 *
 * <p>任务生成使用复习次数作为幂等序号，并在计划行上加锁；因此调度器、页面首次加载
 * 和多实例部署可以同时触发物化而不会产生重复复习任务。</p>
 */
@Service
public class LearningTaskService {

    private static final Collection<LearningTaskStatus> LISTABLE_STATUSES = List.of(
            LearningTaskStatus.OPEN, LearningTaskStatus.IN_PROGRESS,
            LearningTaskStatus.AWAITING_EVIDENCE, LearningTaskStatus.DEFERRED,
            LearningTaskStatus.FAILED, LearningTaskStatus.COMPLETED);

    private final LearningTaskRepository taskRepository;
    private final LearningReviewPlanRepository reviewPlanRepository;
    private final LearningReviewPlanService reviewPlanService;
    private final LearningGoalRepository goalRepository;
    private final LearningRecommendationService recommendationService;
    private final EducationActionService actionService;
    private final ConversationService conversationService;

    public LearningTaskService(LearningTaskRepository taskRepository,
                               LearningReviewPlanRepository reviewPlanRepository,
                               LearningReviewPlanService reviewPlanService,
                               LearningGoalRepository goalRepository,
                               LearningRecommendationService recommendationService,
                               EducationActionService actionService,
                               ConversationService conversationService) {
        this.taskRepository = taskRepository;
        this.reviewPlanRepository = reviewPlanRepository;
        this.reviewPlanService = reviewPlanService;
        this.goalRepository = goalRepository;
        this.recommendationService = recommendationService;
        this.actionService = actionService;
        this.conversationService = conversationService;
    }

    /** 定时器和查询入口都调用此方法，保证刚到期的任务无需等待下一次页面刷新。 */
    @Transactional
    public int materializeDueTasks(Instant reference, int limit) {
        Instant now = reference == null ? Instant.now() : reference;
        int boundedLimit = Math.max(1, Math.min(500, limit));
        List<LearningReviewPlan> candidates = reviewPlanRepository.findDueByStatus(
                LearningReviewPlanStatus.ACTIVE, now, PageRequest.of(0, boundedLimit));
        int materialized = 0;
        for (LearningReviewPlan candidate : candidates) {
            LearningReviewPlan plan = reviewPlanRepository.findByIdForUpdate(candidate.getId()).orElse(null);
            if (plan == null || !plan.isDue(now)) continue;
            LearningGoal goal = goalRepository.findByIdAndTenantIdAndUserId(
                    plan.getLearningGoalId(), plan.getTenantId(), plan.getUserId()).orElse(null);
            if (goal == null || goal.getStatus() != LearningGoalStatus.COMPLETED) continue;

            int sequence = plan.getReviewCount();
            LearningTask existing = taskRepository
                    .findByTenantIdAndUserIdAndReviewPlanIdAndReviewSequence(
                            plan.getTenantId(), plan.getUserId(), plan.getId(), sequence)
                    .orElse(null);
            if (existing != null) {
                if (existing.getStatus() == LearningTaskStatus.DEFERRED
                        && !existing.getScheduledAt().isAfter(now)) {
                    existing.makeAvailable(now);
                    taskRepository.save(existing);
                }
                continue;
            }

            LearningRecommendationView recommendation = recommendationService.recommend(
                    plan.getTenantId(), plan.getUserId(), goal.getId());
            LearningTask task = new LearningTask(
                    plan.getTenantId(), plan.getUserId(), LearningTaskType.REVIEW,
                    goal.getId(), plan.getId(), sequence,
                    recommendation.nextActionTitle(), recommendation.nextActionPrompt(),
                    plan.getNextReviewAt());
            taskRepository.save(task);
            materialized++;
        }
        return materialized;
    }

    @Transactional
    public List<LearningTask> list(String tenantId, String userId, LearningTaskStatus status) {
        materializeDueTasks(Instant.now(), 100);
        if (status == null) {
            return taskRepository.findByTenantIdAndUserIdAndStatusInOrderByScheduledAtAsc(
                    tenantId, userId, LISTABLE_STATUSES);
        }
        return taskRepository.findByTenantIdAndUserIdAndStatusInOrderByScheduledAtAsc(
                tenantId, userId, List.of(status));
    }

    @Transactional(readOnly = true)
    public LearningTask get(String tenantId, String userId, String taskId) {
        return taskRepository.findByTenantIdAndUserIdAndId(tenantId, userId, taskId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "LEARNING_TASK_NOT_FOUND", "学习任务不存在"));
    }

    /** 兼容旧的“按目标执行下一步”入口，但完成目标后的动作必须优先落到任务实例。 */
    @Transactional
    public LearningTask findStartableForGoal(String tenantId, String userId, String goalId) {
        materializeDueTasks(Instant.now(), 100);
        return taskRepository.findByTenantIdAndUserIdAndLearningGoalIdOrderByScheduledAtAsc(
                        tenantId, userId, goalId).stream()
                .filter(task -> task.getStatus() == LearningTaskStatus.OPEN
                        || task.getStatus() == LearningTaskStatus.IN_PROGRESS
                        || (task.getStatus() == LearningTaskStatus.DEFERRED
                        && !task.getScheduledAt().isAfter(Instant.now())))
                .findFirst().orElse(null);
    }

    @Transactional
    public LearningTaskStartView start(String tenantId, String userId, String taskId,
                                       ExecuteLearningActionRequest request,
                                       String permissions, String idempotencyKey) {
        LearningTask task = get(tenantId, userId, taskId);
        if (task.getStatus() == LearningTaskStatus.COMPLETED
                || task.getStatus() == LearningTaskStatus.CANCELLED) {
            if (task.getConversationId() == null) {
                throw new BusinessException(HttpStatus.CONFLICT, "LEARNING_TASK_NOT_RESTARTABLE",
                        "当前学习任务没有可恢复的会话");
            }
            return new LearningTaskStartView(LearningTaskView.from(task),
                    conversationService.detail(task.getConversationId(), tenantId, userId));
        }
        if (task.getStatus() == LearningTaskStatus.IN_PROGRESS
                || task.getStatus() == LearningTaskStatus.AWAITING_EVIDENCE) {
            if (task.getConversationId() == null) {
                throw new BusinessException(HttpStatus.CONFLICT, "LEARNING_TASK_SESSION_MISSING",
                        "进行中的学习任务缺少绑定会话");
            }
            return new LearningTaskStartView(LearningTaskView.from(task),
                    conversationService.detail(task.getConversationId(), tenantId, userId));
        }
        Instant now = Instant.now();
        if (task.getStatus() == LearningTaskStatus.FAILED) {
            task.retry(now);
            taskRepository.save(task);
        }
        if (task.getStatus() == LearningTaskStatus.DEFERRED && task.getScheduledAt().isAfter(now)) {
            throw new BusinessException(HttpStatus.CONFLICT, "LEARNING_TASK_NOT_DUE",
                    "延期后的学习任务尚未到期");
        }
        if (task.getStatus() == LearningTaskStatus.DEFERRED) {
            task.makeAvailable(now);
            taskRepository.save(task);
        }
        if (task.getStatus() != LearningTaskStatus.OPEN) {
            throw new BusinessException(HttpStatus.CONFLICT, "LEARNING_TASK_NOT_STARTABLE",
                    "当前学习任务不能开始");
        }
        LearningReviewPlan plan = reviewPlanService.getById(tenantId, userId, task.getReviewPlanId());
        if (!plan.isDue(now)) {
            throw new BusinessException(HttpStatus.CONFLICT, "LEARNING_TASK_NOT_DUE",
                    "当前保持度复习尚未到期");
        }
        ConversationDetail conversation = actionService.execute(
                tenantId, userId, task.getLearningGoalId(), request, permissions,
                idempotencyKey == null || idempotencyKey.isBlank()
                        ? "learning-task-" + task.getId() : idempotencyKey);
        String runId = conversation.conversation().activeRunId();
        if (runId == null || runId.isBlank()) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "LEARNING_TASK_RUN_MISSING",
                    "学习任务启动后没有关联教育 Run");
        }
        task.start(conversation.conversation().id(), runId, now);
        taskRepository.save(task);
        return new LearningTaskStartView(LearningTaskView.from(task), conversation);
    }

    @Transactional
    public LearningTask defer(String tenantId, String userId, String taskId,
                              DeferLearningTaskRequest request) {
        LearningTask task = get(tenantId, userId, taskId);
        if (task.getStatus() != LearningTaskStatus.OPEN) {
            throw new BusinessException(HttpStatus.CONFLICT, "LEARNING_TASK_NOT_DEFERABLE",
                    "只有待执行的学习任务可以延期");
        }
        Instant deferredAt = Instant.now();
        Instant until = deferredAt.plus((request == null ? 1 : request.effectiveDays()), ChronoUnit.DAYS);
        LearningReviewPlan plan = reviewPlanService.getById(tenantId, userId, task.getReviewPlanId());
        plan.deferUntil(until);
        task.deferUntil(until, deferredAt);
        reviewPlanRepository.save(plan);
        return taskRepository.save(task);
    }

}
