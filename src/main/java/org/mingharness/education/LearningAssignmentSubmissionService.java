package org.mingharness.education;

import org.mingharness.common.BusinessException;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.education.api.LearningAssignmentSubmissionRequest;
import org.mingharness.education.api.LearningAssignmentSubmissionView;
import org.mingharness.runtime.domain.Run;
import org.mingharness.runtime.domain.RunStatus;
import org.mingharness.runtime.repository.RunRepository;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** 记录学习者提交物并把它绑定到真实教育 Run，供教师确认和课程结课使用。 */
@Service
public class LearningAssignmentSubmissionService {

    private static final Logger log = LoggerFactory.getLogger(LearningAssignmentSubmissionService.class);

    private final LearningAssignmentService assignmentService;
    private final LearningAssignmentSubmissionRepository submissionRepository;
    private final RunRepository runRepository;
    private final LearningAssignmentNotificationService notificationService;
    private final SensitiveDataSanitizer sanitizer;
    private final EducationCodeEvaluator codeEvaluator;
    private final EducationCodeEvidenceService codeEvidenceService;

    public LearningAssignmentSubmissionService(LearningAssignmentService assignmentService,
                                              LearningAssignmentSubmissionRepository submissionRepository,
                                              RunRepository runRepository,
                                              LearningAssignmentNotificationService notificationService,
                                              SensitiveDataSanitizer sanitizer) {
        this(assignmentService, submissionRepository, runRepository, notificationService, sanitizer, null, null);
    }

    public LearningAssignmentSubmissionService(LearningAssignmentService assignmentService,
                                              LearningAssignmentSubmissionRepository submissionRepository,
                                              RunRepository runRepository,
                                              LearningAssignmentNotificationService notificationService,
                                              SensitiveDataSanitizer sanitizer,
                                              EducationCodeEvaluator codeEvaluator) {
        this(assignmentService, submissionRepository, runRepository, notificationService, sanitizer,
                codeEvaluator, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public LearningAssignmentSubmissionService(LearningAssignmentService assignmentService,
                                              LearningAssignmentSubmissionRepository submissionRepository,
                                              RunRepository runRepository,
                                              LearningAssignmentNotificationService notificationService,
                                              SensitiveDataSanitizer sanitizer,
                                              EducationCodeEvaluator codeEvaluator,
                                              EducationCodeEvidenceService codeEvidenceService) {
        this.assignmentService = assignmentService;
        this.submissionRepository = submissionRepository;
        this.runRepository = runRepository;
        this.notificationService = notificationService;
        this.sanitizer = sanitizer;
        this.codeEvaluator = codeEvaluator;
        this.codeEvidenceService = codeEvidenceService;
    }

    @Transactional
    public LearningAssignmentSubmissionView submit(String tenantId, String learnerUserId,
                                                   String assignmentId,
                                                   LearningAssignmentSubmissionRequest request) {
        LearningAssignment assignment = assignmentService.getForParticipant(
                tenantId, learnerUserId, assignmentId);
        if (!learnerUserId.equals(assignment.getLearnerUserId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "LEARNING_ASSIGNMENT_LEARNER_ONLY",
                    "只有被布置作业的学习者可以提交作业");
        }
        Run run = resolveRun(tenantId, learnerUserId, assignment,
                request == null ? null : request.runId());
        LearningAssignmentSubmission existing = submissionRepository
                .findByTenantIdAndLearningAssignmentIdAndRunId(
                        tenantId, assignment.getId(), run.getId()).orElse(null);
        if (existing != null) return LearningAssignmentSubmissionView.from(existing);
        boolean completedPendingReview = assignment.getStatus() == LearningAssignmentStatus.COMPLETED
                && assignment.getReviewStatus() == LearningAssignmentReviewStatus.PENDING;
        if (assignment.getStatus() != LearningAssignmentStatus.ACCEPTED
                && assignment.getStatus() != LearningAssignmentStatus.AWAITING_EVIDENCE
                && assignment.getStatus() != LearningAssignmentStatus.RETRY_REQUIRED
                && assignment.getStatus() != LearningAssignmentStatus.OVERDUE
                && !completedPendingReview) {
            throw new BusinessException(HttpStatus.CONFLICT, "ASSIGNMENT_SUBMISSION_NOT_OPEN",
                    "当前课程作业不在可提交状态");
        }
        LearningAssignmentSubmissionType submissionType = request == null
                ? LearningAssignmentSubmissionType.TEXT : request.effectiveSubmissionType();
        String requestedLanguage = normalizeLanguage(request == null ? null : request.programmingLanguage());
        String assignmentLanguage = normalizeLanguage(assignment.getProgrammingLanguage());
        if (submissionType == LearningAssignmentSubmissionType.CODE
                && requestedLanguage != null && assignmentLanguage != null
                && !assignmentLanguage.equals(requestedLanguage)) {
            throw new BusinessException(HttpStatus.CONFLICT, "ASSIGNMENT_SUBMISSION_LANGUAGE_MISMATCH",
                    "代码提交语言不能覆盖作业冻结的编程语言");
        }
        String effectiveLanguage = assignmentLanguage == null ? requestedLanguage : assignmentLanguage;
        if (submissionType == LearningAssignmentSubmissionType.CODE && effectiveLanguage == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ASSIGNMENT_SUBMISSION_LANGUAGE_REQUIRED",
                    "代码提交必须指定编程语言，或先在作业中配置编程语言");
        }
        String rawContent = request == null ? null : request.content();
        String content = submissionType == LearningAssignmentSubmissionType.CODE
                ? rawCode(rawContent) : clean(rawContent);
        if (content.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ASSIGNMENT_SUBMISSION_REQUIRED",
                    "作业提交内容不能为空");
        }
        EducationCodeEvaluationContext evaluationContext = submissionType == LearningAssignmentSubmissionType.CODE
                ? new EducationCodeEvaluationContext(run.educationConfiguration().programmingTestCases())
                : EducationCodeEvaluationContext.empty();
        EducationCodeEvaluationResult evaluation = submissionType == LearningAssignmentSubmissionType.CODE
                ? evaluateCodeSafely(effectiveLanguage, content, evaluationContext)
                : new EducationCodeEvaluationResult(CodeEvaluationStatus.NOT_REQUESTED, null, null, null, 0);
        CodeDiagnosticCategory diagnosticCategory = submissionType == LearningAssignmentSubmissionType.CODE
                ? CodeDiagnosticClassifier.classify(evaluation) : CodeDiagnosticCategory.NONE;
        LearningAssignmentSubmission saved = submissionRepository.save(
                new LearningAssignmentSubmission(tenantId, assignment.getId(), learnerUserId,
                        run.getId(), content, submissionType, effectiveLanguage,
                        evaluation.status(), evaluation.diagnostics(), diagnosticCategory,
                        evaluation.behaviorStatus(), evaluation.testCaseCount(),
                        evaluation.passedTestCaseCount(), evaluation.testPassRate(),
                        evaluation.durationMs(), Instant.now()));
        if (submissionType == LearningAssignmentSubmissionType.CODE && codeEvidenceService != null) {
            try {
                // 证据是提交后的派生事实；写入失败不能回滚学习者已经保存的提交物。
                codeEvidenceService.record(tenantId, learnerUserId, assignment, run, evaluation);
            } catch (RuntimeException exception) {
                log.warn("Unable to record code evaluation evidence for assignment {} and run {}",
                        assignment.getId(), run.getId(), exception);
            }
        }
        if (notificationService != null) notificationService.ensureForSubmission(saved, assignment);
        return LearningAssignmentSubmissionView.from(saved);
    }

    @Transactional(readOnly = true)
    public List<LearningAssignmentSubmissionView> list(String tenantId, String userId,
                                                       String assignmentId) {
        LearningAssignment assignment = assignmentService.getForParticipant(tenantId, userId, assignmentId);
        return listForAssignment(tenantId, assignment);
    }

    /** 管理员治理页只读查看同租户提交物。 */
    @Transactional(readOnly = true)
    public List<LearningAssignmentSubmissionView> listForGovernance(String tenantId, String assignmentId) {
        return listForAssignment(tenantId, assignmentService.getForGovernance(tenantId, assignmentId));
    }

    private List<LearningAssignmentSubmissionView> listForAssignment(String tenantId, LearningAssignment assignment) {
        return submissionRepository.findByTenantIdAndLearningAssignmentIdOrderBySubmittedAtDesc(
                        tenantId, assignment.getId()).stream()
                .map(LearningAssignmentSubmissionView::from).toList();
    }

    private Run resolveRun(String tenantId, String learnerUserId, LearningAssignment assignment,
                           String requestedRunId) {
        String runId = cleanNullable(requestedRunId);
        Run run = runId == null
                ? runRepository.findTopByTenantIdAndUserIdAndEducationLearningAssignmentIdOrderByCreatedAtDesc(
                        tenantId, learnerUserId, assignment.getId()).orElse(null)
                : runRepository.findById(runId).orElse(null);
        if (run == null || !tenantId.equals(run.getTenantId())
                || !learnerUserId.equals(run.getUserId())
                || !assignment.getId().equals(run.getEducationLearningAssignmentId())) {
            throw new BusinessException(HttpStatus.CONFLICT, "ASSIGNMENT_SUBMISSION_RUN_INVALID",
                    "作业提交必须绑定当前学习者本课程作业的教育 Run");
        }
        if (run.getStatus() != RunStatus.SUCCEEDED) {
            throw new BusinessException(HttpStatus.CONFLICT, "ASSIGNMENT_SUBMISSION_RUN_NOT_FINISHED",
                    "作业提交只能绑定已成功完成的教育 Run");
        }
        return run;
    }

    private String clean(String value) {
        return sanitizer.sanitize(value == null ? "" : value.trim());
    }

    private String cleanNullable(String value) {
        String cleaned = clean(value);
        return cleaned.isBlank() ? null : cleaned;
    }

    private String rawCode(String value) {
        String normalized = value == null ? "" : value.trim();
        if (sanitizer.containsSensitiveData(normalized)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ASSIGNMENT_SUBMISSION_SENSITIVE_DATA",
                    "代码提交疑似包含凭证或敏感配置，请删除后再提交");
        }
        return normalized;
    }

    private EducationCodeEvaluationResult evaluateCodeSafely(String language, String content,
                                                             EducationCodeEvaluationContext context) {
        if (codeEvaluator == null) {
            return EducationCodeEvaluationResult.unavailable("当前运行环境未配置代码评测沙箱。");
        }
        try {
            return context != null && context.testCaseSnapshot() != null
                    && !context.testCaseSnapshot().cases().isEmpty()
                    ? codeEvaluator.evaluate(language, content, context)
                    : codeEvaluator.evaluate(language, content);
        } catch (RuntimeException exception) {
            log.warn("Code evaluation adapter failed for language {}", language, exception);
            return new EducationCodeEvaluationResult(CodeEvaluationStatus.ERROR,
                    "代码评测服务暂时不可用，请稍后重试。", "", null, 0);
        }
    }

    private String normalizeLanguage(String value) {
        String normalized = cleanNullable(value);
        return normalized == null ? null : normalized.toUpperCase(java.util.Locale.ROOT);
    }
}
