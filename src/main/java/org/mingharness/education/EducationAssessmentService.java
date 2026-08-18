package org.mingharness.education;

import org.mingharness.common.BusinessException;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.education.api.MasteryUpdateRequest;
import org.mingharness.runtime.domain.Run;
import org.mingharness.runtime.domain.RunStatus;
import org.mingharness.runtime.repository.RunRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 将测评、掌握度变化和学习目标状态放在同一个可追踪事务中。 */
@Service
public class EducationAssessmentService {

    private final AssessmentAttemptRepository attemptRepository;
    private final RunRepository runRepository;
    private final LearningGoalRepository goalRepository;
    private final LearnerMasteryRepository masteryRepository;
    private final EducationLearnerService learnerService;
    private final LearningReviewPlanService reviewPlanService;
    private final LearningTaskCompletionService taskCompletionService;
    private final LearningAssignmentCompletionService assignmentCompletionService;
    private final LearningAssignmentFeedbackService feedbackService;
    private final SensitiveDataSanitizer sanitizer;

    public EducationAssessmentService(AssessmentAttemptRepository attemptRepository,
                                      RunRepository runRepository,
                                      LearningGoalRepository goalRepository,
                                      LearnerMasteryRepository masteryRepository,
                                      EducationLearnerService learnerService,
                                      SensitiveDataSanitizer sanitizer) {
        this(attemptRepository, runRepository, goalRepository, masteryRepository, learnerService,
                null, null, null, sanitizer, null);
    }

    /** 兼容已有组件测试和旧扩展调用方；保持度任务由 Spring 主构造器接入。 */
    public EducationAssessmentService(AssessmentAttemptRepository attemptRepository,
                                      RunRepository runRepository,
                                      LearningGoalRepository goalRepository,
                                      LearnerMasteryRepository masteryRepository,
                                      EducationLearnerService learnerService,
                                      LearningReviewPlanService reviewPlanService,
                                      SensitiveDataSanitizer sanitizer) {
        this(attemptRepository, runRepository, goalRepository, masteryRepository, learnerService,
                reviewPlanService, null, null, sanitizer, null);
    }

    public EducationAssessmentService(AssessmentAttemptRepository attemptRepository,
                                      RunRepository runRepository,
                                      LearningGoalRepository goalRepository,
                                      LearnerMasteryRepository masteryRepository,
                                      EducationLearnerService learnerService,
                                      LearningReviewPlanService reviewPlanService,
                                      LearningTaskCompletionService taskCompletionService,
                                      SensitiveDataSanitizer sanitizer) {
        this(attemptRepository, runRepository, goalRepository, masteryRepository, learnerService,
                reviewPlanService, taskCompletionService, null, sanitizer, null);
    }

    public EducationAssessmentService(AssessmentAttemptRepository attemptRepository,
                                      RunRepository runRepository,
                                      LearningGoalRepository goalRepository,
                                      LearnerMasteryRepository masteryRepository,
                                      EducationLearnerService learnerService,
                                      LearningReviewPlanService reviewPlanService,
                                      LearningTaskCompletionService taskCompletionService,
                                      LearningAssignmentCompletionService assignmentCompletionService,
                                      SensitiveDataSanitizer sanitizer) {
        this(attemptRepository, runRepository, goalRepository, masteryRepository, learnerService,
                reviewPlanService, taskCompletionService, assignmentCompletionService, sanitizer, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public EducationAssessmentService(AssessmentAttemptRepository attemptRepository,
                                      RunRepository runRepository,
                                      LearningGoalRepository goalRepository,
                                      LearnerMasteryRepository masteryRepository,
                                      EducationLearnerService learnerService,
                                      LearningReviewPlanService reviewPlanService,
                                      LearningTaskCompletionService taskCompletionService,
                                      LearningAssignmentCompletionService assignmentCompletionService,
                                      SensitiveDataSanitizer sanitizer,
                                      LearningAssignmentFeedbackService feedbackService) {
        this.attemptRepository = attemptRepository;
        this.runRepository = runRepository;
        this.goalRepository = goalRepository;
        this.masteryRepository = masteryRepository;
        this.learnerService = learnerService;
        this.reviewPlanService = reviewPlanService;
        this.taskCompletionService = taskCompletionService;
        this.assignmentCompletionService = assignmentCompletionService;
        this.feedbackService = feedbackService;
        this.sanitizer = sanitizer;
    }

    @Transactional
    public AssessmentAttempt record(String tenantId, String userId, String runId, String stepId,
                                    String profileId, String conceptKey, boolean correct,
                                    double observedMastery, String feedback) {
        return recordInternal(tenantId, userId, runId, stepId, null, profileId, conceptKey,
                AssessmentObservation.legacy(correct, observedMastery), "MODEL_TOOL", null, null, feedback);
    }

    @Transactional
    public AssessmentAttempt record(String tenantId, String userId, String runId, String stepId,
                                    String profileId, String conceptKey, boolean correct,
                                    double observedMastery, String evidenceSource,
                                    String evidenceText, String feedback) {
        return recordInternal(tenantId, userId, runId, stepId, null, profileId, conceptKey,
                AssessmentObservation.legacy(correct, observedMastery), evidenceSource, evidenceText, null, feedback);
    }

    /**
     * 模型工具记录形成性评价时，必须提供本轮学习者输入中的逐字原话。
     * 这让服务端能区分“模型给出的一道题”与“学习者已经作出的回答”。
     */
    @Transactional
    public AssessmentAttempt record(String tenantId, String userId, String runId, String stepId,
                                    String profileId, String conceptKey, boolean correct,
                                    double observedMastery, String evidenceSource,
                                    String evidenceText, String learnerEvidenceQuote, String feedback) {
        return recordInternal(tenantId, userId, runId, stepId, null, profileId, conceptKey,
                AssessmentObservation.legacy(correct, observedMastery), evidenceSource, evidenceText,
                learnerEvidenceQuote, feedback);
    }

    /** 新版结构化测评入口：一个题目可以同时评价多个知识点。 */
    @Transactional
    public AssessmentAttempt record(String tenantId, String userId, String runId, String stepId,
                                    String profileId, String conceptKey, AssessmentObservation observation,
                                    String evidenceSource, String evidenceText,
                                    String learnerEvidenceQuote, String feedback) {
        return recordInternal(tenantId, userId, runId, stepId, null, profileId, conceptKey,
                observation, evidenceSource, evidenceText, learnerEvidenceQuote, feedback);
    }

    /** 由路径绑定的目标提交复核，防止请求体里的 Run 与 URL 目标交叉写入。 */
    @Transactional
    public AssessmentAttempt recordForGoal(String tenantId, String userId, String expectedGoalId,
                                           String runId, String stepId, String profileId,
                                           String conceptKey, boolean correct, double observedMastery,
                                           String evidenceSource, String evidenceText, String feedback) {
        return recordInternal(tenantId, userId, runId, stepId, expectedGoalId, profileId, conceptKey,
                AssessmentObservation.legacy(correct, observedMastery), evidenceSource, evidenceText, null, feedback);
    }

    @Transactional
    public AssessmentAttempt recordForGoal(String tenantId, String userId, String expectedGoalId,
                                           String runId, String stepId, String profileId,
                                           String conceptKey, AssessmentObservation observation,
                                           String evidenceSource, String evidenceText, String feedback) {
        return recordInternal(tenantId, userId, runId, stepId, expectedGoalId, profileId, conceptKey,
                observation, evidenceSource, evidenceText, null, feedback);
    }

    private AssessmentAttempt recordInternal(String tenantId, String userId, String runId, String stepId,
                                             String expectedGoalId, String profileId, String conceptKey,
                                             AssessmentObservation observation, String evidenceSource,
                                             String evidenceText, String learnerEvidenceQuote,
                                             String feedback) {
        Run run = runRepository.findById(runId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "RUN_NOT_FOUND", "测评所属 Run 不存在"));
        if (!tenantId.equals(run.getTenantId()) || !userId.equals(run.getUserId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "ASSESSMENT_ACCESS_DENIED",
                    "无权记录其他用户 Run 的测评");
        }
        String normalizedEvidenceSource = clean(evidenceSource).toUpperCase(java.util.Locale.ROOT);
        if (!"MODEL_TOOL".equals(normalizedEvidenceSource)
                && !"MANUAL_REVIEW".equals(normalizedEvidenceSource)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ASSESSMENT_EVIDENCE_SOURCE_INVALID",
                    "测评证据来源只支持 MODEL_TOOL 或 MANUAL_REVIEW");
        }
        String normalizedEvidenceText = cleanEvidence(evidenceText);
        if (normalizedEvidenceText == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ASSESSMENT_EVIDENCE_REQUIRED",
                    "更新学习者掌握度必须提供学生作答、推理过程或评分依据");
        }
        String normalizedLearnerEvidenceQuote = cleanEvidence(learnerEvidenceQuote);
        if ("MODEL_TOOL".equals(normalizedEvidenceSource)) {
            if (normalizedLearnerEvidenceQuote == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "ASSESSMENT_LEARNER_EVIDENCE_QUOTE_REQUIRED",
                        "模型评价必须逐字引用本轮学习者的作答或推理原话");
            }
            if (!matchesRunInput(run.getInput(), normalizedLearnerEvidenceQuote)) {
                throw new BusinessException(HttpStatus.CONFLICT,
                        "ASSESSMENT_LEARNER_EVIDENCE_QUOTE_MISMATCH",
                        "模型评价引用的学习者原话不属于本轮输入，不能更新掌握度");
            }
        }
        if ("MANUAL_REVIEW".equals(normalizedEvidenceSource)) {
            if (run.getStatus() != RunStatus.SUCCEEDED) {
                throw new BusinessException(HttpStatus.CONFLICT, "ASSESSMENT_RUN_NOT_FINISHED",
                        "人工复核只能提交已完成的教育 Run");
            }
            boolean stepExists = run.getSteps().stream()
                    .anyMatch(step -> java.util.Objects.equals(stepId, step.getId()));
            if (!stepExists) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "ASSESSMENT_STEP_NOT_FOUND",
                        "人工复核步骤不属于指定 Run");
            }
        }
        if (!run.isEducationMode() || run.getEducationLearningGoalId() == null) {
            throw new BusinessException(HttpStatus.CONFLICT, "LEARNING_GOAL_REQUIRED",
                    "形成性测评必须绑定进行中的学习目标");
        }
        if (!run.getEducationLearnerProfileId().equals(profileId)) {
            throw new BusinessException(HttpStatus.CONFLICT, "ASSESSMENT_PROFILE_MISMATCH",
                    "测评画像与 Run 冻结画像不一致");
        }

        LearningGoal goal = goalRepository.findByIdAndTenantIdAndUserId(
                        run.getEducationLearningGoalId(), tenantId, userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "LEARNING_GOAL_NOT_FOUND", "测评绑定的学习目标不存在"));
        if (expectedGoalId != null && !expectedGoalId.equals(goal.getId())) {
            throw new BusinessException(HttpStatus.CONFLICT, "ASSESSMENT_GOAL_MISMATCH",
                    "测评 Run 与请求路径中的学习目标不一致");
        }
        String reviewPlanId = run.getEducationReviewPlanId();
        AssessmentAttemptType attemptType = AssessmentAttemptType.FORMATIVE;
        if (reviewPlanId != null && !reviewPlanId.isBlank()) {
            if (reviewPlanService == null) {
                throw new BusinessException(HttpStatus.CONFLICT, "LEARNING_REVIEW_UNAVAILABLE",
                        "当前运行环境未启用保持度复习计划");
            }
            LearningReviewPlan plan = reviewPlanService.getById(tenantId, userId, reviewPlanId);
            if (!goal.getId().equals(plan.getLearningGoalId())
                    || !profileId.equals(plan.getLearnerProfileId())
                    || !goal.getConceptKey().equalsIgnoreCase(plan.getConceptKey())) {
                throw new BusinessException(HttpStatus.CONFLICT, "LEARNING_REVIEW_CONTEXT_MISMATCH",
                        "复习计划与目标、画像或知识点不一致");
            }
            if (goal.getStatus() != LearningGoalStatus.COMPLETED) {
                throw new BusinessException(HttpStatus.CONFLICT, "LEARNING_REVIEW_GOAL_NOT_COMPLETED",
                        "保持度复习只能用于已完成的学习目标");
            }
            attemptType = AssessmentAttemptType.REVIEW;
        } else if (goal.getStatus() != LearningGoalStatus.ACTIVE) {
            throw new BusinessException(HttpStatus.CONFLICT, "LEARNING_GOAL_NOT_ACTIVE",
                    "只有进行中的学习目标可以记录形成性测评");
        }
        String normalizedConcept = clean(conceptKey);
        if (!conceptMatchesGoal(normalizedConcept, goal)
                || !conceptMatchesGoal(run.getEducationConceptKey(), goal)) {
            throw new BusinessException(HttpStatus.CONFLICT, "ASSESSMENT_CONCEPT_MISMATCH",
                    "测评知识点与学习目标不一致");
        }
        // 即使请求使用了课程短标签或历史 Run 使用了目标标题，新写入的测评记录也必须
        // 回到目标的 canonical concept_key，避免掌握度被拆成两条记录。
        String assessmentConcept = clean(goal.getConceptKey());

        AssessmentObservation effectiveObservation = observation == null
                ? AssessmentObservation.legacy(false, 0.0) : observation;
        double boundedObserved = clamp(effectiveObservation.aggregateObservedMastery());
        // 结构化题目的总体正确性由知识点加权结果派生，避免模型把“部分正确”提交为整体正确。
        boolean effectiveCorrect = effectiveObservation.hasStructuredKnowledgePoints()
                ? boundedObserved >= 0.80 : effectiveObservation.correct();
        AssessmentObservation targetObservation = effectiveObservation.hasStructuredKnowledgePoints()
                ? new AssessmentObservation(effectiveCorrect, boundedObserved, effectiveObservation.difficultyLevel(),
                effectiveObservation.knowledgePoints(), effectiveObservation.hintUsed(),
                effectiveObservation.independent(), effectiveObservation.questionType())
                : effectiveObservation;
        double before = masteryRepository
                .findByTenantIdAndLearnerProfileIdAndConceptKey(tenantId, profileId, assessmentConcept)
                .map(LearnerMastery::getMasteryScore)
                .orElse(0.0);
        LearnerMastery updated = updateMasteryForObservation(tenantId, userId, profileId, assessmentConcept,
                targetObservation, boundedObserved, runId, normalizedEvidenceSource, attemptType.name(),
                normalizedEvidenceText);

        List<KnowledgePointAssessment> points = effectiveObservation.knowledgePoints();
        List<LearnerMastery> updatedKnowledgePoints = new ArrayList<>();
        if (effectiveObservation.hasStructuredKnowledgePoints()) {
            for (KnowledgePointAssessment point : deduplicate(points)) {
                String pointConcept = clean(point.conceptKey());
                if (pointConcept.isBlank()) continue;
                if (pointConcept.equalsIgnoreCase(assessmentConcept)) {
                    updatedKnowledgePoints.add(updated);
                    continue;
                }
                AssessmentObservation pointObservation = new AssessmentObservation(point.correct(), point.score(),
                        effectiveObservation.difficultyLevel(), List.of(), effectiveObservation.hintUsed(),
                        effectiveObservation.independent(), effectiveObservation.questionType());
                LearnerMastery pointMastery = updateMasteryForObservation(tenantId, userId, profileId, pointConcept,
                        pointObservation, point.score(), runId, normalizedEvidenceSource, attemptType.name(),
                        evidenceForConcept(normalizedEvidenceText, point));
                if (pointMastery != null) updatedKnowledgePoints.add(pointMastery);
            }
        }

        AssessmentAttempt attempt = new AssessmentAttempt(tenantId, userId, runId, stepId,
                goal.getId(), profileId, assessmentConcept, effectiveCorrect, boundedObserved, before,
                updated.getMasteryScore(), attemptType, reviewPlanId, normalizedEvidenceSource,
                normalizedEvidenceText,
                cleanFeedback(feedback), run.getEducationLearningAssignmentId(),
                EducationRetrievalEvidence.snapshot(run), normalizedLearnerEvidenceQuote);
        attempt.setStructuredEvidence(effectiveObservation.difficultyLevel(), encodeKnowledgePoints(points),
                effectiveObservation.hintUsed(), effectiveObservation.independent(), effectiveObservation.questionType());
        AssessmentAttempt saved = attemptRepository.save(attempt);
        if (attemptType == AssessmentAttemptType.FORMATIVE && assignmentCompletionService != null) {
            assignmentCompletionService.resumeAfterEvidenceForGoal(
                    tenantId, userId, goal.getId(), java.time.Instant.now());
        }
        if (attemptType == AssessmentAttemptType.FORMATIVE && feedbackService != null
                && run.getEducationLearningAssignmentId() != null
                && !run.getEducationLearningAssignmentId().isBlank()) {
            feedbackService.resolveForEvidence(tenantId, userId,
                    run.getEducationLearningAssignmentId(), java.time.Instant.now());
        }
        if (attemptType == AssessmentAttemptType.REVIEW) {
            if (taskCompletionService != null) {
                taskCompletionService.completeForReview(tenantId, userId, runId, effectiveCorrect, java.time.Instant.now());
            }
            reviewPlanService.recordReview(tenantId, userId, reviewPlanId, effectiveCorrect, java.time.Instant.now());
        } else if (updated.getMasteryScore() >= goal.getTargetMastery()
                && canCompleteGoal(goal, effectiveObservation, saved, updatedKnowledgePoints)) {
            goal.changeStatus(LearningGoalStatus.COMPLETED);
            if (reviewPlanService != null) {
                reviewPlanService.ensureForCompletedGoal(goal);
            }
            goalRepository.save(goal);
            if (assignmentCompletionService != null) {
                assignmentCompletionService.completeForGoal(tenantId, userId, goal.getId(),
                        java.time.Instant.now());
            }
        }
        return saved;
    }

    private LearnerMastery updateMasteryForObservation(String tenantId, String userId, String profileId,
                                                        String conceptKey, AssessmentObservation observation,
                                                        double observedMastery, String runId,
                                                        String evidenceSource, String assessmentType,
                                                        String evidenceText) {
        MasteryUpdateRequest request = new MasteryUpdateRequest(conceptKey, observedMastery,
                observation.correct(), null, null, observation.difficultyLevel(),
                observation.effectiveEvidenceWeight(), observation.hintUsed(), observation.independent());
        LearnerStateTransitionContext context = LearnerStateTransitionContext.observation(
                runId, evidenceSource, assessmentType, evidenceText, observation);
        if (observation.hasStructuredKnowledgePoints()) {
            LearnerMastery updated = learnerService.recordObservedMasteryWithoutGoalCompletion(
                    tenantId, userId, profileId, request, context);
            // 兼容只 mock 旧接口的组件测试和旧扩展实现。
            return updated == null
                    ? learnerService.recordObservedMastery(tenantId, userId, profileId, request)
                    : updated;
        }
        LearnerMastery updated = learnerService.recordObservedMastery(
                tenantId, userId, profileId, request, context);
        // 兼容只 mock 旧接口的组件测试和旧扩展实现。
        return updated == null
                ? learnerService.recordObservedMastery(tenantId, userId, profileId, request)
                : updated;
    }

    private String evidenceForConcept(String aggregateEvidence, KnowledgePointAssessment point) {
        String pointEvidence = point == null ? null : cleanEvidence(point.evidenceText());
        if (pointEvidence == null) return aggregateEvidence;
        if (aggregateEvidence == null || aggregateEvidence.isBlank()) return pointEvidence;
        return aggregateEvidence + "；知识点证据：" + pointEvidence;
    }

    private List<KnowledgePointAssessment> deduplicate(List<KnowledgePointAssessment> points) {
        Map<String, KnowledgePointAssessment> result = new LinkedHashMap<>();
        for (KnowledgePointAssessment point : points == null ? List.<KnowledgePointAssessment>of() : points) {
            if (point == null) continue;
            result.putIfAbsent(normalizeConcept(point.conceptKey()), point);
        }
        return new ArrayList<>(result.values());
    }

    private String encodeKnowledgePoints(List<KnowledgePointAssessment> points) {
        if (points == null || points.isEmpty()) return "[]";
        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        for (KnowledgePointAssessment point : deduplicate(points)) {
            if (!first) json.append(',');
            first = false;
            json.append("{\"conceptKey\":\"").append(jsonEscape(point.conceptKey()))
                    .append("\",\"correct\":").append(point.correct())
                    .append(",\"score\":").append(point.score())
                    .append(",\"weight\":").append(point.weight());
            if (point.evidenceText() != null) {
                json.append(",\"evidenceText\":\"").append(jsonEscape(point.evidenceText())).append('"');
            }
            json.append('}');
        }
        return json.append(']').toString();
    }

    private String jsonEscape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\")
                .replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    /** 结构化新流程要求多次、跨类型、至少一次独立证据后才完成目标。 */
    private boolean canCompleteGoal(LearningGoal goal, AssessmentObservation observation,
                                    AssessmentAttempt current, List<LearnerMastery> updatedKnowledgePoints) {
        if (!observation.hasStructuredKnowledgePoints()) return true;
        if (updatedKnowledgePoints == null || updatedKnowledgePoints.isEmpty()
                || updatedKnowledgePoints.stream().anyMatch(item -> item.getMasteryScore() < goal.getTargetMastery())) {
            return false;
        }
        List<AssessmentAttempt> history = attemptRepository
                .findByTenantIdAndUserIdAndLearningGoalIdOrderByCreatedAtAsc(
                        current.getTenantId(), current.getUserId(), goal.getId());
        List<AssessmentAttempt> all = new ArrayList<>(history == null ? List.of() : history);
        if (all.stream().noneMatch(item -> item.getId().equals(current.getId()))) all.add(current);
        long valid = all.stream().filter(item -> item.getAssessmentType() == AssessmentAttemptType.FORMATIVE).count();
        long independent = all.stream().filter(AssessmentAttempt::isIndependentEvidence).count();
        long questionTypes = all.stream().map(AssessmentAttempt::getQuestionType)
                .filter(type -> type != null && !type.isBlank()).distinct().count();
        long runs = all.stream().map(AssessmentAttempt::getRunId).distinct().count();
        if (valid < 3 || independent < 1 || (questionTypes < 2 && runs < 2)) return false;
        int size = all.size();
        return size < 2 || (all.get(size - 1).isCorrect() && all.get(size - 2).isCorrect());
    }

    @Transactional(readOnly = true)
    public List<AssessmentAttempt> listByGoal(String tenantId, String userId, String goalId) {
        goalRepository.findByIdAndTenantIdAndUserId(goalId, tenantId, userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "LEARNING_GOAL_NOT_FOUND", "学习目标不存在"));
        return attemptRepository.findByTenantIdAndUserIdAndLearningGoalIdOrderByCreatedAtAsc(
                tenantId, userId, goalId);
    }

    private String clean(String value) {
        return sanitizer.sanitize(value == null ? "" : value.trim());
    }

    /**
     * 新数据要求请求、目标和 Run 都使用同一个 canonical concept_key。历史数据可能把目标标题
     * 写入 Run 的知识点字段，或把课程短标签与详细目标文本混用；这里只允许精确匹配、足够具体
     * 的长短知识点关系，或候选值等于绑定目标标题，避免把“函数”和“二次函数”等任意混淆。
     */
    private boolean conceptMatchesGoal(String candidate, LearningGoal goal) {
        String normalizedCandidate = normalizeConcept(candidate);
        String normalizedGoal = normalizeConcept(goal == null ? null : goal.getConceptKey());
        if (normalizedCandidate == null || normalizedGoal == null) return false;
        if (normalizedGoal.equals(normalizedCandidate)) return true;
        // 复合学习目标会把多个可独立测评的知识点写在同一个 canonical key 中，
        // 例如“类与对象、封装、继承、多态”。测评工具可以只提交其中一个子知识点；
        // 这里先按中文/英文分隔符拆分，再对每个子项做精确或具体词组匹配。
        // 不能直接把整体字符串交给 conceptsMatch：两字知识点（如“多态”“继承”）
        // 会被通用模糊匹配的最小长度保护挡住。
        List<String> goalConcepts = splitConcepts(normalizedGoal);
        List<String> candidateConcepts = splitConcepts(normalizedCandidate);
        if (!goalConcepts.isEmpty() && !candidateConcepts.isEmpty()
                && candidateConcepts.stream().allMatch(candidateConcept -> goalConcepts.stream()
                .anyMatch(goalConcept -> goalConcept.equals(candidateConcept)
                        || EducationRetrievalFilter.conceptsMatch(goalConcept, candidateConcept)))) {
            return true;
        }
        // 课程标签可能是“二次函数”，学习目标知识点可能带教学语义，例如
        // “理解二次函数的概念及一般形式”。只允许足够具体的词组互相包含。
        if (EducationRetrievalFilter.conceptsMatch(normalizedGoal, normalizedCandidate)) return true;
        String normalizedTitle = normalizeConcept(goal.getTitle());
        return normalizedTitle != null
                && normalizedCandidate.equals(normalizedTitle)
                && normalizedTitle.contains(normalizedGoal);
    }

    private String normalizeConcept(String value) {
        String normalized = clean(value).toLowerCase(java.util.Locale.ROOT);
        if (normalized.isBlank()) return null;
        return normalized.replaceAll("\\s+", "");
    }

    private List<String> splitConcepts(String value) {
        if (value == null || value.isBlank()) return List.of();
        return java.util.Arrays.stream(value.split("[,，;；、\\n]+"))
                .map(this::normalizeConcept)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
    }

    private String cleanFeedback(String value) {
        String cleaned = clean(value);
        return cleaned.isBlank() ? null : cleaned;
    }

    private String cleanEvidence(String value) {
        String cleaned = clean(value);
        return cleaned.isBlank() ? null : cleaned;
    }

    /** 忽略大小写和空白比较，保留词语与数字本身，避免模型只凭泛化结论伪造证据。 */
    private boolean matchesRunInput(String learnerInput, String learnerEvidenceQuote) {
        String normalizedInput = normalizeEvidenceForMatch(learnerInput);
        String normalizedQuote = normalizeEvidenceForMatch(learnerEvidenceQuote);
        return !normalizedQuote.isBlank() && normalizedInput.contains(normalizedQuote);
    }

    private String normalizeEvidenceForMatch(String value) {
        if (value == null) return "";
        return value.toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", "");
    }

    private double clamp(double value) {
        if (!Double.isFinite(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
    }
}
