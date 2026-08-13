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
import org.mingharness.education.api.LearningAssignmentEvaluationView;
import org.mingharness.education.api.LearningAssignmentEvaluationRequest;
import org.mingharness.education.api.LearningAssignmentEvaluationConsensusView;
import org.mingharness.education.api.LearningAssignmentSubmissionRequest;
import org.mingharness.education.api.LearningAssignmentSubmissionView;
import org.mingharness.education.api.EducationMetricsView;
import org.mingharness.education.api.EducationCourseRequest;
import org.mingharness.education.api.EducationCourseCompletionRequest;
import org.mingharness.education.api.EducationCourseView;
import org.mingharness.education.api.EducationEnrollmentRequest;
import org.mingharness.education.api.EducationEnrollmentView;
import org.mingharness.education.api.EducationCourseAssignmentRequest;
import org.mingharness.education.api.EducationCourseAssignmentBatchView;
import org.mingharness.education.api.EducationCourseProgressView;
import org.mingharness.education.api.EducationCourseResultView;
import org.mingharness.education.api.ManualAssessmentSubmissionRequest;
import org.mingharness.education.api.MasteryUpdateRequest;
import org.mingharness.security.HarnessIdentity;
import org.mingharness.security.HarnessIdentityContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
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
    private final EducationCourseService courseService;
    private final LearningAssignmentBatchService assignmentBatchService;
    private final EducationCourseProgressService courseProgressService;
    private final EducationCourseCompletionService courseCompletionService;
    private final LearningAssignmentSubmissionService submissionService;
    private final EducationCourseResultService courseResultService;
    private final LearningAssignmentIndependentEvaluationService independentEvaluationService;

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
                               LearningAssignmentReviewService assignmentReviewService,
                               EducationCourseService courseService,
                               LearningAssignmentBatchService assignmentBatchService,
                               EducationCourseProgressService courseProgressService,
                               EducationCourseCompletionService courseCompletionService,
                               LearningAssignmentSubmissionService submissionService,
                               EducationCourseResultService courseResultService,
                               LearningAssignmentIndependentEvaluationService independentEvaluationService) {
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
        this.courseService = courseService;
        this.assignmentBatchService = assignmentBatchService;
        this.courseProgressService = courseProgressService;
        this.courseCompletionService = courseCompletionService;
        this.submissionService = submissionService;
        this.courseResultService = courseResultService;
        this.independentEvaluationService = independentEvaluationService;
    }

    @PostMapping("/sources")
    @ResponseStatus(HttpStatus.CREATED)
    public EducationSourceView upsertSource(@Valid @RequestBody EducationSourceRequest request) {
        HarnessIdentity identity = identity();
        requireEducationOperator(identity);
        return EducationSourceView.from(knowledgeService.upsertSource(identity.tenantId(), identity.userId(), request,
                identity.hasPermission("education.assign")));
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
        requireEducationOperator(identity);
        knowledgeService.deleteSource(identity.tenantId(), identity.userId(), documentId);
    }

    @PostMapping("/profiles")
    @ResponseStatus(HttpStatus.CREATED)
    public LearnerProfileView upsertProfile(@Valid @RequestBody LearnerProfileRequest request) {
        HarnessIdentity identity = identity();
        requireEducationOperator(identity);
        return LearnerProfileView.from(learnerService.upsertProfile(identity.tenantId(), identity.userId(), request));
    }

    @GetMapping("/profiles")
    public List<LearnerProfileView> listProfiles() {
        HarnessIdentity identity = identity();
        return learnerService.listProfiles(identity.tenantId(), identity.userId()).stream()
                .map(LearnerProfileView::from).toList();
    }

    @DeleteMapping("/profiles/{profileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProfile(@PathVariable String profileId) {
        HarnessIdentity identity = identity();
        requireEducationOperator(identity);
        learnerService.deleteProfile(identity.tenantId(), identity.userId(), profileId);
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
        requireEducationOperator(identity);
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
        requireEducationOperator(identity);
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
        requireEducationOperator(identity);
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
        requireEducationOperator(identity);
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
        return identity.hasPermission("ops.read")
                ? metricsService.summarizeForGovernance(identity.tenantId())
                : metricsService.summarize(identity.tenantId(), identity.userId());
    }

    @PostMapping("/courses")
    @ResponseStatus(HttpStatus.CREATED)
    public EducationCourseView createCourse(@Valid @RequestBody EducationCourseRequest request) {
        HarnessIdentity identity = identity();
        requireEducationOperator(identity);
        return courseService.create(identity.tenantId(), identity.userId(), request);
    }

    @GetMapping("/courses")
    public List<EducationCourseView> listCourses() {
        HarnessIdentity identity = identity();
        if (identity.hasPermission("ops.read")) {
            return courseService.listForGovernance(identity.tenantId());
        }
        return courseService.list(identity.tenantId(), identity.userId());
    }

    @GetMapping("/courses/{courseId}")
    public EducationCourseView getCourse(@PathVariable String courseId) {
        HarnessIdentity identity = identity();
        return identity.hasPermission("ops.read")
                ? courseService.getForGovernance(identity.tenantId(), courseId)
                : courseService.getForParticipant(identity.tenantId(), identity.userId(), courseId);
    }

    @PostMapping("/courses/{courseId}/enrollments")
    @ResponseStatus(HttpStatus.CREATED)
    public EducationEnrollmentView enrollLearner(@PathVariable String courseId,
                                                 @Valid @RequestBody EducationEnrollmentRequest request) {
        HarnessIdentity identity = identity();
        requireEducationOperator(identity);
        return courseService.enroll(identity.tenantId(), identity.userId(), courseId, request);
    }

    @GetMapping("/courses/{courseId}/enrollments")
    public List<EducationEnrollmentView> listCourseRoster(@PathVariable String courseId) {
        HarnessIdentity identity = identity();
        return identity.hasPermission("ops.read")
                ? courseService.rosterForGovernance(identity.tenantId(), courseId)
                : courseService.roster(identity.tenantId(), identity.userId(), courseId);
    }

    @PostMapping("/courses/{courseId}/enrollments/{learnerUserId}/remove")
    public EducationEnrollmentView removeLearner(@PathVariable String courseId,
                                                 @PathVariable String learnerUserId) {
        HarnessIdentity identity = identity();
        requireEducationOperator(identity);
        return courseService.removeEnrollment(identity.tenantId(), identity.userId(), courseId, learnerUserId);
    }

    @PostMapping("/courses/{courseId}/archive")
    public EducationCourseView archiveCourse(@PathVariable String courseId) {
        HarnessIdentity identity = identity();
        requireEducationOperator(identity);
        return courseService.archive(identity.tenantId(), identity.userId(), courseId);
    }

    @PostMapping("/courses/{courseId}/complete")
    public EducationCourseView completeCourse(
            @PathVariable String courseId,
            @Valid @RequestBody(required = false) EducationCourseCompletionRequest request) {
        HarnessIdentity identity = identity();
        requireEducationOperator(identity);
        return courseCompletionService.complete(identity.tenantId(), identity.userId(), courseId, request);
    }

    @GetMapping("/courses/{courseId}/result")
    public EducationCourseResultView courseResult(@PathVariable String courseId) {
        HarnessIdentity identity = identity();
        return identity.hasPermission("ops.read")
                ? courseResultService.getForGovernance(identity.tenantId(), courseId)
                : courseResultService.get(identity.tenantId(), identity.userId(), courseId);
    }

    @GetMapping(value = "/courses/{courseId}/result.csv", produces = "text/csv")
    public ResponseEntity<byte[]> exportCourseResult(@PathVariable String courseId) {
        HarnessIdentity identity = identity();
        String csv = identity.hasPermission("ops.read")
                ? courseResultService.exportCsvForGovernance(identity.tenantId(), courseId)
                : courseResultService.exportCsv(identity.tenantId(), identity.userId(), courseId);
        String safeCourseId = courseId == null ? "course" : courseId.replaceAll("[^A-Za-z0-9._-]", "_");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"course-result-" + safeCourseId + ".csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    @PostMapping("/courses/{courseId}/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    public EducationCourseAssignmentBatchView assignCourse(
            @PathVariable String courseId,
            @Valid @RequestBody EducationCourseAssignmentRequest request,
            HttpServletRequest httpRequest) {
        HarnessIdentity identity = identity();
        requireEducationOperator(identity);
        return assignmentBatchService.assign(identity.tenantId(), identity.userId(), courseId,
                request, httpRequest.getHeader("Idempotency-Key"));
    }

    @GetMapping("/courses/{courseId}/progress")
    public EducationCourseProgressView courseProgress(
            @PathVariable String courseId,
            @RequestParam(defaultValue = "500") int limit) {
        HarnessIdentity identity = identity();
        return identity.hasPermission("ops.read")
                ? courseProgressService.getForGovernance(identity.tenantId(), courseId, limit)
                : courseProgressService.get(identity.tenantId(), identity.userId(), courseId, limit);
    }

    @PostMapping("/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    public LearningAssignmentView createAssignment(@Valid @RequestBody LearningAssignmentRequest request) {
        HarnessIdentity identity = identity();
        requireEducationOperator(identity);
        return LearningAssignmentView.from(assignmentService.create(
                identity.tenantId(), identity.userId(), request));
    }

    @GetMapping("/assignments")
    public List<LearningAssignmentView> listAssignments() {
        HarnessIdentity identity = identity();
        List<LearningAssignment> assignments = identity.hasPermission("ops.read")
                ? assignmentService.listForGovernance(identity.tenantId())
                : assignmentService.list(identity.tenantId(), identity.userId());
        return assignments.stream()
                .map(LearningAssignmentView::from).toList();
    }

    @GetMapping("/assignments/{assignmentId}")
    public LearningAssignmentView getAssignment(@PathVariable String assignmentId) {
        HarnessIdentity identity = identity();
        return LearningAssignmentView.from(identity.hasPermission("ops.read")
                ? assignmentService.getForGovernance(identity.tenantId(), assignmentId)
                : assignmentService.getForParticipant(identity.tenantId(), identity.userId(), assignmentId));
    }

    @GetMapping("/assignments/{assignmentId}/progress")
    public LearningAssignmentProgressView assignmentProgress(@PathVariable String assignmentId) {
        HarnessIdentity identity = identity();
        return identity.hasPermission("ops.read")
                ? assignmentProgressService.getForGovernance(identity.tenantId(), assignmentId)
                : assignmentProgressService.get(identity.tenantId(), identity.userId(), assignmentId);
    }

    @GetMapping("/assignments/{assignmentId}/evidence")
    public List<AssessmentAttemptView> assignmentEvidence(@PathVariable String assignmentId) {
        HarnessIdentity identity = identity();
        return identity.hasPermission("ops.read")
                ? assignmentEvidenceService.listForGovernance(identity.tenantId(), assignmentId)
                : assignmentEvidenceService.list(identity.tenantId(), identity.userId(), assignmentId);
    }

    @GetMapping("/assignments/{assignmentId}/submissions")
    public List<LearningAssignmentSubmissionView> assignmentSubmissions(@PathVariable String assignmentId) {
        HarnessIdentity identity = identity();
        return identity.hasPermission("ops.read")
                ? submissionService.listForGovernance(identity.tenantId(), assignmentId)
                : submissionService.list(identity.tenantId(), identity.userId(), assignmentId);
    }

    @PostMapping("/assignments/{assignmentId}/submissions")
    @ResponseStatus(HttpStatus.CREATED)
    public LearningAssignmentSubmissionView submitAssignment(
            @PathVariable String assignmentId,
            @Valid @RequestBody LearningAssignmentSubmissionRequest request) {
        HarnessIdentity identity = identity();
        requireEducationOperator(identity);
        return submissionService.submit(identity.tenantId(), identity.userId(), assignmentId, request);
    }

    @GetMapping("/assignments/{assignmentId}/feedback")
    public List<LearningAssignmentFeedbackView> assignmentFeedback(@PathVariable String assignmentId) {
        HarnessIdentity identity = identity();
        return identity.hasPermission("ops.read")
                ? assignmentFeedbackService.listForGovernance(identity.tenantId(), assignmentId)
                : assignmentFeedbackService.list(identity.tenantId(), identity.userId(), assignmentId);
    }

    @PostMapping("/assignments/{assignmentId}/feedback")
    public LearningAssignmentFeedbackView createAssignmentFeedback(
            @PathVariable String assignmentId,
            @Valid @RequestBody LearningAssignmentFeedbackRequest request) {
        HarnessIdentity identity = identity();
        requireEducationOperator(identity);
        return assignmentFeedbackService.create(identity.tenantId(), identity.userId(), assignmentId, request);
    }

    @PostMapping("/assignments/{assignmentId}/feedback/{feedbackId}/acknowledge")
    public LearningAssignmentFeedbackView acknowledgeAssignmentFeedback(
            @PathVariable String assignmentId, @PathVariable String feedbackId) {
        HarnessIdentity identity = identity();
        requireEducationOperator(identity);
        return assignmentFeedbackService.acknowledge(
                identity.tenantId(), identity.userId(), assignmentId, feedbackId);
    }

    @PostMapping("/assignments/{assignmentId}/accept")
    public LearningAssignmentAcceptView acceptAssignment(@PathVariable String assignmentId) {
        HarnessIdentity identity = identity();
        requireEducationOperator(identity);
        return assignmentService.accept(identity.tenantId(), identity.userId(), assignmentId);
    }

    /** 作业入口的主动作：接受（如有需要）后立即提交第一步教育 Run。 */
    @PostMapping("/assignments/{assignmentId}/start")
    public LearningAssignmentStartView startAssignment(
            @PathVariable String assignmentId,
            @Valid @RequestBody(required = false) ExecuteLearningActionRequest request,
            HttpServletRequest httpRequest) {
        HarnessIdentity identity = identity();
        requireEducationOperator(identity);
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
        requireEducationOperator(identity);
        return assignmentReviewService.review(identity.tenantId(), identity.userId(), assignmentId, request);
    }

    @GetMapping("/assignments/{assignmentId}/evaluations")
    public List<LearningAssignmentEvaluationView> assignmentEvaluations(@PathVariable String assignmentId) {
        HarnessIdentity identity = identity();
        return identity.hasPermission("ops.read")
                ? assignmentReviewService.evaluationsForGovernance(identity.tenantId(), assignmentId)
                : assignmentReviewService.evaluations(identity.tenantId(), identity.userId(), assignmentId);
    }

    @GetMapping("/evaluation-queue")
    public List<LearningAssignmentView> independentEvaluationQueue() {
        HarnessIdentity identity = identity();
        return independentEvaluationService.queue(identity.tenantId(), identity.userId());
    }

    @PostMapping("/assignments/{assignmentId}/evaluations")
    @ResponseStatus(HttpStatus.CREATED)
    public LearningAssignmentEvaluationView submitIndependentEvaluation(
            @PathVariable String assignmentId,
            @Valid @RequestBody LearningAssignmentEvaluationRequest request) {
        HarnessIdentity identity = identity();
        requireEducationOperator(identity);
        return independentEvaluationService.evaluate(identity.tenantId(), identity.userId(),
                assignmentId, request);
    }

    @GetMapping("/assignments/{assignmentId}/evaluations/consensus")
    public LearningAssignmentEvaluationConsensusView evaluationConsensus(
            @PathVariable String assignmentId) {
        HarnessIdentity identity = identity();
        return independentEvaluationService.consensus(identity.tenantId(), identity.userId(), assignmentId);
    }

    @PostMapping("/assignments/{assignmentId}/cancel")
    public LearningAssignmentView cancelAssignment(@PathVariable String assignmentId) {
        HarnessIdentity identity = identity();
        requireEducationOperator(identity);
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
        requireEducationOperator(identity);
        return assignmentNotificationService.markRead(identity.tenantId(), identity.userId(), notificationId);
    }

    @PostMapping("/assignment-notifications/read-all")
    public java.util.Map<String, Long> markAllAssignmentNotificationsRead() {
        HarnessIdentity identity = identity();
        requireEducationOperator(identity);
        return java.util.Map.of("markedRead", assignmentNotificationService.markAllRead(
                identity.tenantId(), identity.userId()));
    }

    @PostMapping("/tasks/{taskId}/start")
    public LearningTaskStartView startTask(@PathVariable String taskId,
                                           @Valid @RequestBody(required = false)
                                           ExecuteLearningActionRequest request,
                                           HttpServletRequest httpRequest) {
        HarnessIdentity identity = identity();
        requireEducationOperator(identity);
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
        requireEducationOperator(identity);
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
        requireEducationOperator(identity);
        return notificationService.markRead(identity.tenantId(), identity.userId(), notificationId);
    }

    @PostMapping("/notifications/read-all")
    public java.util.Map<String, Long> markAllNotificationsRead() {
        HarnessIdentity identity = identity();
        requireEducationOperator(identity);
        return java.util.Map.of("markedRead", notificationService.markAllRead(
                identity.tenantId(), identity.userId()));
    }

    @PostMapping("/goals/{goalId}/next-action")
    public ConversationDetail executeNextAction(@PathVariable String goalId,
                                                 @Valid @RequestBody(required = false)
                                                 ExecuteLearningActionRequest request,
                                                 HttpServletRequest httpRequest) {
        HarnessIdentity identity = identity();
        requireEducationOperator(identity);
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

    /** 管理员教育工作台是治理只读视图，课程和学习状态写入必须由教师或学生执行。 */
    private void requireEducationOperator(HarnessIdentity identity) {
        if (identity.hasPermission("ops.read")) {
            throw new org.mingharness.common.BusinessException(HttpStatus.FORBIDDEN,
                    "EDUCATION_ADMIN_READ_ONLY", "管理员教育工作台仅支持只读治理，课程操作请由教师执行");
        }
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
