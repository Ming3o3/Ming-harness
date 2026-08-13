package org.mingharness.education;

import org.mingharness.common.BusinessException;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.education.api.LearningAssignmentSubmissionRequest;
import org.mingharness.education.api.LearningAssignmentSubmissionView;
import org.mingharness.runtime.domain.Run;
import org.mingharness.runtime.domain.RunStatus;
import org.mingharness.runtime.repository.RunRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** 记录学习者提交物并把它绑定到真实教育 Run，供教师确认和课程结课使用。 */
@Service
public class LearningAssignmentSubmissionService {

    private final LearningAssignmentService assignmentService;
    private final LearningAssignmentSubmissionRepository submissionRepository;
    private final RunRepository runRepository;
    private final LearningAssignmentNotificationService notificationService;
    private final SensitiveDataSanitizer sanitizer;

    public LearningAssignmentSubmissionService(LearningAssignmentService assignmentService,
                                              LearningAssignmentSubmissionRepository submissionRepository,
                                              RunRepository runRepository,
                                              LearningAssignmentNotificationService notificationService,
                                              SensitiveDataSanitizer sanitizer) {
        this.assignmentService = assignmentService;
        this.submissionRepository = submissionRepository;
        this.runRepository = runRepository;
        this.notificationService = notificationService;
        this.sanitizer = sanitizer;
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
        String content = clean(request == null ? null : request.content());
        if (content.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ASSIGNMENT_SUBMISSION_REQUIRED",
                    "作业提交内容不能为空");
        }
        LearningAssignmentSubmission saved = submissionRepository.save(
                new LearningAssignmentSubmission(tenantId, assignment.getId(), learnerUserId,
                        run.getId(), content, Instant.now()));
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
}
