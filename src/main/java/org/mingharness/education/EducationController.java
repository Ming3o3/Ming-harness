package org.mingharness.education;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.mingharness.conversation.api.ConversationDetail;
import org.mingharness.education.api.EducationSourceRequest;
import org.mingharness.education.api.EducationSourceView;
import org.mingharness.education.api.AssessmentAttemptView;
import org.mingharness.education.api.ExecuteLearningActionRequest;
import org.mingharness.education.api.LearnerMasteryView;
import org.mingharness.education.api.LearnerProfileRequest;
import org.mingharness.education.api.LearnerProfileView;
import org.mingharness.education.api.LearningGoalRequest;
import org.mingharness.education.api.LearningGoalStatusRequest;
import org.mingharness.education.api.LearningGoalView;
import org.mingharness.education.api.LearningRecommendationView;
import org.mingharness.education.api.LearningReviewPlanView;
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

    public EducationController(EducationKnowledgeService knowledgeService,
                                EducationLearnerService learnerService,
                               LearningGoalService learningGoalService,
                               EducationAssessmentService assessmentService,
                               LearningRecommendationService recommendationService,
                               EducationActionService actionService) {
        this.knowledgeService = knowledgeService;
        this.learnerService = learnerService;
        this.learningGoalService = learningGoalService;
        this.assessmentService = assessmentService;
        this.recommendationService = recommendationService;
        this.actionService = actionService;
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

    @PostMapping("/goals/{goalId}/next-action")
    public ConversationDetail executeNextAction(@PathVariable String goalId,
                                                 @Valid @RequestBody(required = false)
                                                 ExecuteLearningActionRequest request,
                                                 HttpServletRequest httpRequest) {
        HarnessIdentity identity = identity();
        String permissions = identity.usesTrustedPermissions()
                ? identity.permissionsCsv() : httpRequest.getHeader("X-Permissions");
        return actionService.execute(identity.tenantId(), identity.userId(), goalId, request,
                permissions, httpRequest.getHeader("Idempotency-Key"));
    }

    private HarnessIdentity identity() {
        return HarnessIdentityContext.require();
    }
}
