package org.mingharness.education;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.mingharness.conversation.api.ConversationDetail;
import org.mingharness.education.api.EducationSourceRequest;
import org.mingharness.education.api.EducationSourceView;
import org.mingharness.education.api.AssessmentAttemptView;
import org.mingharness.education.api.DeferLearningTaskRequest;
import org.mingharness.education.api.ExecuteLearningActionRequest;
import org.mingharness.education.api.LearnerMasteryView;
import org.mingharness.education.api.LearnerProfileRequest;
import org.mingharness.education.api.LearnerProfileView;
import org.mingharness.education.api.LearningGoalRequest;
import org.mingharness.education.api.LearningGoalStatusRequest;
import org.mingharness.education.api.LearningGoalView;
import org.mingharness.education.api.LearningRecommendationView;
import org.mingharness.education.api.LearningReviewPlanView;
import org.mingharness.education.api.LearningTaskStartView;
import org.mingharness.education.api.LearningTaskNotificationView;
import org.mingharness.education.api.LearningTaskView;
import org.mingharness.education.api.LearningAssignmentAcceptView;
import org.mingharness.education.api.LearningAssignmentStartView;
import org.mingharness.education.api.LearningAssignmentRequest;
import org.mingharness.education.api.LearningAssignmentView;
import org.mingharness.education.api.LearningAssignmentReviewRequest;
import org.mingharness.education.api.LearningAssignmentProgressView;
import org.mingharness.education.api.LearningAssignmentNotificationView;
import org.mingharness.education.api.LearningAssignmentFeedbackRequest;
import org.mingharness.education.api.LearningAssignmentFeedbackView;
import org.mingharness.education.api.EducationMetricsView;
import org.mingharness.education.api.ManualAssessmentSubmissionRequest;
import org.mingharness.education.api.MasteryUpdateRequest;
import org.mingharness.security.HarnessIdentity;
import org.mingharness.security.HarnessIdentityContext;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 教育知识源和学习者状态接口；正文仍由 /api/context 管理。 */
@RestController
@RequestMapping("/api/education")
public class EducationController {

    private final EducationKnowledgeService knowledgeService;
    private final EducationLearnerService learnerService;
    private final LearningGoalService learningGoalService;
    private final EducationAssessmentService assessmentService;
    private final LearningRecommendationService recommendationService;
    private final EducationActionService actionService;
    private final LearningTaskService taskService;
    private final LearningTaskNotificationService notificationService;
    private final LearningAssignmentService assignmentService;
    private final EducationMetricsService metricsService;
    private final LearningAssignmentProgressService assignmentProgressService;
    private final LearningAssignmentNotificationService assignmentNotificationService;
    private final LearningAssignmentEvidenceService assignmentEvidenceService;
    private final LearningAssignmentFeedbackService assignmentFeedbackService;
    private final LearningAssignmentStartService assignmentStartService;
    private final LearningAssignmentReviewService assignmentReviewService;

    public EducationController(EducationKnowledgeService knowledgeService,
                                EducationLearnerService learnerService,
                               LearningGoalService learningGoalService,
                               EducationAssessmentService assessmentService,
                               LearningRecommendationService recommendationService,
                               EducationActionService actionService,
                               LearningTaskService taskService,
                               LearningTaskNotificationService notificationService,
                               LearningAssignmentService assignmentService,
                               EducationMetricsService metricsService,
                               LearningAssignmentProgressService assignmentProgressService,
                               LearningAssignmentNotificationService assignmentNotificationService,
                               LearningAssignmentEvidenceService assignmentEvidenceService,
                               LearningAssignmentFeedbackService assignmentFeedbackService,
                               LearningAssignmentStartService assignmentStartService,
                               LearningAssignmentReviewService assignmentReviewService) {
        this.knowledgeService = knowledgeService;
        this.learnerService = learnerService;
        this.learningGoalService = learningGoalService;
        this.assessmentService = assessmentService;
        this.recommendationService = recommendationService;
        this.actionService = actionService;
        this.taskService = taskService;
        this.notificationService = notificationService;
        this.assignmentService = assignmentService;
        this.metricsService = metricsService;
        this.assignmentProgressService = assignmentProgressService;
        this.assignmentNotificationService = assignmentNotificationService;
        this.assignmentEvidenceService = assignmentEvidenceService;
        this.assignmentFeedbackService = assignmentFeedbackService;
        this.assignmentStartService = assignmentStartService;
        this.assignmentReviewService = assignmentReviewService;
    }

    @PostMapping("/sources")
    @ResponseStatus(HttpStatus.CREATED)
    public EducationSourceView upsertSource(@Valid @RequestBody EducationSourceRequest request) {
        HarnessIdentity identity = identity();
        return EducationSourceView.from(knowledgeService.upsertSource(identity.tenantId(), identity.userId(), request));
    }

    @GetMapping("/sources")
    public List<EducationSourceView> listSources() {
        HarnessIdentity identity = identity();
        return knowledgeService.listSources(identity.tenantId(), identity.userId()).stream()
                .map(EducationSourceView::from).toList();
    }

    @DeleteMapping("/sources/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSource(@PathVariable String documentId) {
        HarnessIdentity identity = identity();
        knowledgeService.deleteSource(identity.tenantId(), identity.userId(), documentId);
    }

    @PostMapping("/profiles")
    @ResponseStatus(HttpStatus.CREATED)
    public LearnerProfileView upsertProfile(@Valid @RequestBody LearnerProfileRequest request) {
        HarnessIdentity identity = identity();
        return LearnerProfileView.from(learnerService.upsertProfile(identity.tenantId(), identity.userId(), request));
    }

    @GetMapping("/profiles")
    public List<LearnerProfileView> listProfiles() {
        HarnessIdentity identity = identity();
        return learnerService.listProfiles(identity.tenantId(), identity.userId()).stream()
                .map(LearnerProfileView::from).toList();
    }

    @GetMapping("/profiles/active")
    public LearnerProfileView activeProfile() {
        HarnessIdentity identity = identity();
        return LearnerProfileView.from(learnerService.activeProfile(identity.tenantId(), identity.userId()));
    }

    @PostMapping("/profiles/{profileId}/mastery")
    public LearnerMasteryView updateMastery(@PathVariable String profileId,
                                            @Valid @RequestBody MasteryUpdateRequest request) {
        HarnessIdentity identity = identity();
        return LearnerMasteryView.from(learnerService.updateMastery(identity.tenantId(), identity.userId(),
                profileId, request));
    }

    @GetMapping("/profiles/{profileId}/mastery")
    public List<LearnerMasteryView> listMastery(@PathVariable String profileId) {
        HarnessIdentity identity = identity();
        return learnerService.listMastery(identity.tenantId(), identity.userId(), profileId).stream()
                .map(LearnerMasteryView::from).toList();
    }

    @PostMapping("/goals")
    @ResponseStatus(HttpStatus.CREATED)
    public LearningGoalView createGoal(@Valid @RequestBody LearningGoalRequest request) {
        HarnessIdentity identity = identity();
        return LearningGoalView.from(learningGoalService.create(identity.tenantId(), identity.userId(), request));
    }

    @GetMapping("/goals")
    public List<LearningGoalView> listGoals() {
        HarnessIdentity identity = identity();
        return learningGoalService.list(identity.tenantId(), identity.userId()).stream()
                .map(LearningGoalView::from).toList();
    }

    @GetMapping("/goals/{goalId}")
    public LearningGoalView getGoal(@PathVariable String goalId) {
        HarnessIdentity identity = identity();
        return LearningGoalView.from(learningGoalService.get(identity.tenantId(), identity.userId(), goalId));
    }

    @PostMapping("/goals/{goalId}/status")
    public LearningGoalView changeGoalStatus(@PathVariable String goalId,
                                             @Valid @RequestBody LearningGoalStatusRequest request) {
        HarnessIdentity identity = identity();
        return LearningGoalView.from(learningGoalService.changeStatus(identity.tenantId(), identity.userId(),
                goalId, request.status()));
    }

    @GetMapping("/goals/{goalId}/assessments")
    public List<AssessmentAttemptView> listAssessments(@PathVariable String goalId) {
        HarnessIdentity identity = identity();
        return assessmentService.listByGoal(identity.tenantId(), identity.userId(), goalId).stream()
                .map(AssessmentAttemptView::from).toList();
    }

    @PostMapping("/goals/{goalId}/assessments")
    @ResponseStatus(HttpStatus.CREATED)
    public AssessmentAttemptView submitAssessment(@PathVariable String goalId,
                                                   @Valid @RequestBody ManualAssessmentSubmissionRequest request) {
        HarnessIdentity identity = identity();
        LearningGoal goal = learningGoalService.get(identity.tenantId(), identity.userId(), goalId);
        AssessmentAttempt attempt = assessmentService.recordForGoal(identity.tenantId(), identity.userId(),
                goalId, request.runId(), request.stepId(), goal.getLearnerProfileId(), request.conceptKey(),
                Boolean.TRUE.equals(request.correct()), request.effectiveObservedMastery(),
                "MANUAL_REVIEW", request.evidenceText(), request.feedback());
        return AssessmentAttemptView.from(attempt);
    }

    @GetMapping("/goals/{goalId}/recommendation")
    public LearningRecommendationView recommendation(@PathVariable String goalId) {
        HarnessIdentity identity = identity();
        return recommendationService.recommend(identity.tenantId(), identity.userId(), goalId);
    }

    @GetMapping("/goals/{goalId}/review-plan")
    public LearningReviewPlanView reviewPlan(@PathVariable String goalId) {
        HarnessIdentity identity = identity();
        learningGoalService.get(identity.tenantId(), identity.userId(), goalId);
        return LearningReviewPlanView.from(
                recommendationService.reviewPlan(identity.tenantId(), identity.userId(), goalId));
    }

    @GetMapping("/tasks")
    public List<LearningTaskView> listTasks(@RequestParam(required = false) String status) {
        HarnessIdentity identity = identity();
        LearningTaskStatus requested = parseTaskStatus(status);
        return taskService.list(identity.tenantId(), identity.userId(), requested).stream()
                .map(LearningTaskView::from).toList();
    }

    @GetMapping("/metrics")
    public EducationMetricsView metrics() {
        HarnessIdentity identity = identity();
        return metricsService.summarize(identity.tenantId(), identity.userId());
    }

    @PostMapping("/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    public LearningAssignmentView createAssignment(@Valid @RequestBody LearningAssignmentRequest request) {
        HarnessIdentity identity = identity();
        return LearningAssignmentView.from(assignmentService.create(
                identity.tenantId(), identity.userId(), request));
    }

    @GetMapping("/assignments")
    public List<LearningAssignmentView> listAssignments() {
        HarnessIdentity identity = identity();
        return assignmentService.list(identity.tenantId(), identity.userId()).stream()
                .map(LearningAssignmentView::from).toList();
    }

    @GetMapping("/assignments/{assignmentId}")
    public LearningAssignmentView getAssignment(@PathVariable String assignmentId) {
        HarnessIdentity identity = identity();
        return LearningAssignmentView.from(assignmentService.getForParticipant(
                identity.tenantId(), identity.userId(), assignmentId));
    }

    @GetMapping("/assignments/{assignmentId}/progress")
    public LearningAssignmentProgressView assignmentProgress(@PathVariable String assignmentId) {
        HarnessIdentity identity = identity();
        return assignmentProgressService.get(identity.tenantId(), identity.userId(), assignmentId);
    }

    @GetMapping("/assignments/{assignmentId}/evidence")
    public List<AssessmentAttemptView> assignmentEvidence(@PathVariable String assignmentId) {
        HarnessIdentity identity = identity();
        return assignmentEvidenceService.list(identity.tenantId(), identity.userId(), assignmentId);
    }

    @GetMapping("/assignments/{assignmentId}/feedback")
    public List<LearningAssignmentFeedbackView> assignmentFeedback(@PathVariable String assignmentId) {
        HarnessIdentity identity = identity();
        return assignmentFeedbackService.list(identity.tenantId(), identity.userId(), assignmentId);
    }

    @PostMapping("/assignments/{assignmentId}/feedback")
    public LearningAssignmentFeedbackView createAssignmentFeedback(
            @PathVariable String assignmentId,
            @Valid @RequestBody LearningAssignmentFeedbackRequest request) {
        HarnessIdentity identity = identity();
        return assignmentFeedbackService.create(identity.tenantId(), identity.userId(), assignmentId, request);
    }

    @PostMapping("/assignments/{assignmentId}/feedback/{feedbackId}/acknowledge")
    public LearningAssignmentFeedbackView acknowledgeAssignmentFeedback(
            @PathVariable String assignmentId, @PathVariable String feedbackId) {
        HarnessIdentity identity = identity();
        return assignmentFeedbackService.acknowledge(
                identity.tenantId(), identity.userId(), assignmentId, feedbackId);
    }

    @PostMapping("/assignments/{assignmentId}/accept")
    public LearningAssignmentAcceptView acceptAssignment(@PathVariable String assignmentId) {
        HarnessIdentity identity = identity();
        return assignmentService.accept(identity.tenantId(), identity.userId(), assignmentId);
    }

    /** 作业入口的主动作：接受（如有需要）后立即提交第一步教育 Run。 */
    @PostMapping("/assignments/{assignmentId}/start")
    public LearningAssignmentStartView startAssignment(
            @PathVariable String assignmentId,
            @Valid @RequestBody(required = false) ExecuteLearningActionRequest request,
            HttpServletRequest httpRequest) {
        HarnessIdentity identity = identity();
        String permissions = identity.usesTrustedPermissions()
                ? identity.permissionsCsv() : httpRequest.getHeader("X-Permissions");
        return assignmentStartService.start(identity.tenantId(), identity.userId(), assignmentId,
                request, permissions, httpRequest.getHeader("Idempotency-Key"));
    }

    @PostMapping("/assignments/{assignmentId}/review")
    public LearningAssignmentView reviewAssignment(
            @PathVariable String assignmentId,
            @Valid @RequestBody LearningAssignmentReviewRequest request) {
        HarnessIdentity identity = identity();
        return assignmentReviewService.review(identity.tenantId(), identity.userId(), assignmentId, request);
    }

    @PostMapping("/assignments/{assignmentId}/cancel")
    public LearningAssignmentView cancelAssignment(@PathVariable String assignmentId) {
        HarnessIdentity identity = identity();
        return LearningAssignmentView.from(assignmentService.cancel(
                identity.tenantId(), identity.userId(), assignmentId));
    }

    @GetMapping("/assignment-notifications")
    public LearningAssignmentNotificationService.NotificationPage listAssignmentNotifications(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "50") int limit) {
        HarnessIdentity identity = identity();
        return assignmentNotificationService.list(identity.tenantId(), identity.userId(), unreadOnly, limit);
    }

    @PostMapping("/assignment-notifications/{notificationId}/read")
    public LearningAssignmentNotificationView markAssignmentNotificationRead(
            @PathVariable String notificationId) {
        HarnessIdentity identity = identity();
        return assignmentNotificationService.markRead(identity.tenantId(), identity.userId(), notificationId);
    }

    @PostMapping("/assignment-notifications/read-all")
    public java.util.Map<String, Long> markAllAssignmentNotificationsRead() {
        HarnessIdentity identity = identity();
        return java.util.Map.of("markedRead", assignmentNotificationService.markAllRead(
                identity.tenantId(), identity.userId()));
    }

    @PostMapping("/tasks/{taskId}/start")
    public LearningTaskStartView startTask(@PathVariable String taskId,
                                           @Valid @RequestBody(required = false)
                                           ExecuteLearningActionRequest request,
                                           HttpServletRequest httpRequest) {
        HarnessIdentity identity = identity();
        String permissions = identity.usesTrustedPermissions()
                ? identity.permissionsCsv() : httpRequest.getHeader("X-Permissions");
        return taskService.start(identity.tenantId(), identity.userId(), taskId, request,
                permissions, httpRequest.getHeader("Idempotency-Key"));
    }

    @PostMapping("/tasks/{taskId}/defer")
    public LearningTaskView deferTask(@PathVariable String taskId,
                                      @Valid @RequestBody(required = false)
                                      DeferLearningTaskRequest request) {
        HarnessIdentity identity = identity();
        return LearningTaskView.from(taskService.defer(identity.tenantId(), identity.userId(), taskId, request));
    }

    @GetMapping("/notifications")
    public LearningTaskNotificationService.NotificationPage listNotifications(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "50") int limit) {
        HarnessIdentity identity = identity();
        return notificationService.list(identity.tenantId(), identity.userId(), unreadOnly, limit);
    }

    @PostMapping("/notifications/{notificationId}/read")
    public LearningTaskNotificationView markNotificationRead(@PathVariable String notificationId) {
        HarnessIdentity identity = identity();
        return notificationService.markRead(identity.tenantId(), identity.userId(), notificationId);
    }

    @PostMapping("/notifications/read-all")
    public java.util.Map<String, Long> markAllNotificationsRead() {
        HarnessIdentity identity = identity();
        return java.util.Map.of("markedRead", notificationService.markAllRead(
                identity.tenantId(), identity.userId()));
    }

    @PostMapping("/goals/{goalId}/next-action")
    public ConversationDetail executeNextAction(@PathVariable String goalId,
                                                 @Valid @RequestBody(required = false)
                                                 ExecuteLearningActionRequest request,
                                                 HttpServletRequest httpRequest) {
        HarnessIdentity identity = identity();
        String permissions = identity.usesTrustedPermissions()
                ? identity.permissionsCsv() : httpRequest.getHeader("X-Permissions");
        LearningGoal goal = learningGoalService.get(identity.tenantId(), identity.userId(), goalId);
        if (goal.getStatus() == LearningGoalStatus.COMPLETED) {
            LearningTask task = taskService.findStartableForGoal(identity.tenantId(), identity.userId(), goalId);
            if (task != null) {
                return taskService.start(identity.tenantId(), identity.userId(), task.getId(), request,
                        permissions, httpRequest.getHeader("Idempotency-Key")).conversation();
            }
        }
        return actionService.execute(identity.tenantId(), identity.userId(), goalId, request,
                permissions, httpRequest.getHeader("Idempotency-Key"));
    }

    private HarnessIdentity identity() {
        return HarnessIdentityContext.require();
    }

    private LearningTaskStatus parseTaskStatus(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LearningTaskStatus.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new org.mingharness.common.BusinessException(HttpStatus.BAD_REQUEST,
                    "LEARNING_TASK_STATUS_INVALID", "学习任务状态不合法");
        }
    }
}
