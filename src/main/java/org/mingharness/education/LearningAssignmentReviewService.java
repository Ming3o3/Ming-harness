package org.mingharness.education;

import org.mingharness.common.BusinessException;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.education.api.LearningAssignmentReviewRequest;
import org.mingharness.education.api.LearningAssignmentView;
import org.mingharness.education.api.LearningAssignmentEvaluationView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

/** 将学习者达标事实转换为教师可确认的业务结果。 */
@Service
public class LearningAssignmentReviewService {

    private final LearningAssignmentRepository assignmentRepository;
    private final LearningGoalRepository goalRepository;
    private final LearningTaskRepository taskRepository;
    private final LearningAssignmentSubmissionRepository submissionRepository;
    private final LearningAssignmentNotificationService notificationService;
    private final SensitiveDataSanitizer sanitizer;
    private final LearningAssignmentEvaluationRepository evaluationRepository;

    public LearningAssignmentReviewService(LearningAssignmentRepository assignmentRepository,
                                           LearningAssignmentNotificationService notificationService,
                                           SensitiveDataSanitizer sanitizer) {
        this(assignmentRepository, null, null, notificationService, sanitizer, null, null);
    }

    public LearningAssignmentReviewService(LearningAssignmentRepository assignmentRepository,
                                           LearningGoalRepository goalRepository,
                                           LearningAssignmentNotificationService notificationService,
                                           SensitiveDataSanitizer sanitizer) {
        this(assignmentRepository, goalRepository, null, notificationService, sanitizer, null, null);
    }

    public LearningAssignmentReviewService(LearningAssignmentRepository assignmentRepository,
                                           LearningGoalRepository goalRepository,
                                           LearningTaskRepository taskRepository,
                                           LearningAssignmentNotificationService notificationService,
                                           SensitiveDataSanitizer sanitizer) {
        this(assignmentRepository, goalRepository, taskRepository, notificationService, sanitizer, null, null);
    }

    public LearningAssignmentReviewService(LearningAssignmentRepository assignmentRepository,
                                           LearningGoalRepository goalRepository,
                                           LearningTaskRepository taskRepository,
                                           LearningAssignmentNotificationService notificationService,
                                           SensitiveDataSanitizer sanitizer,
                                           LearningAssignmentSubmissionRepository submissionRepository) {
        this(assignmentRepository, goalRepository, taskRepository, notificationService, sanitizer,
                submissionRepository, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public LearningAssignmentReviewService(LearningAssignmentRepository assignmentRepository,
                                           LearningGoalRepository goalRepository,
                                           LearningTaskRepository taskRepository,
                                           LearningAssignmentNotificationService notificationService,
                                           SensitiveDataSanitizer sanitizer,
                                           LearningAssignmentSubmissionRepository submissionRepository,
                                           LearningAssignmentEvaluationRepository evaluationRepository) {
        this.assignmentRepository = assignmentRepository;
        this.goalRepository = goalRepository;
        this.taskRepository = taskRepository;
        this.submissionRepository = submissionRepository;
        this.notificationService = notificationService;
        this.sanitizer = sanitizer;
        this.evaluationRepository = evaluationRepository;
    }

    @Transactional
    public LearningAssignmentView review(String tenantId, String teacherUserId, String assignmentId,
                                         LearningAssignmentReviewRequest request) {
        LearningAssignment assignment = assignmentRepository.findByTenantIdAndId(tenantId, assignmentId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "LEARNING_ASSIGNMENT_NOT_FOUND", "课程作业不存在"));
        if (!teacherUserId.equals(assignment.getTeacherUserId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "LEARNING_ASSIGNMENT_TEACHER_ONLY",
                    "只有布置者可以确认课程作业结果");
        }
        String decision = clean(request == null ? null : request.decision()).toUpperCase(Locale.ROOT);
        if (!"VERIFY".equals(decision) && !"RETURN".equals(decision)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "LEARNING_ASSIGNMENT_REVIEW_DECISION_INVALID",
                    "教师确认决定只支持 VERIFY 或 RETURN");
        }
        if (assignment.getReviewStatus() == LearningAssignmentReviewStatus.VERIFIED) {
            return LearningAssignmentView.from(assignment);
        }
        if ("RETURN".equals(decision) && cleanNullable(request == null ? null : request.note()) == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "LEARNING_ASSIGNMENT_REVISION_NOTE_REQUIRED",
                    "退回返工必须填写教师说明");
        }
        if (assignment.getStatus() != LearningAssignmentStatus.COMPLETED
                || assignment.getReviewStatus() != LearningAssignmentReviewStatus.PENDING) {
            throw new BusinessException(HttpStatus.CONFLICT, "LEARNING_ASSIGNMENT_REVIEW_NOT_PENDING",
                    "当前作业不在待教师确认状态");
        }
        RubricScores rubric = rubric(request);
        try {
            if ("VERIFY".equals(decision)) {
                if (submissionRepository != null
                        && !submissionRepository.existsByTenantIdAndLearningAssignmentId(
                        tenantId, assignment.getId())) {
                    throw new BusinessException(HttpStatus.CONFLICT,
                            "ASSIGNMENT_SUBMISSION_REQUIRED_FOR_REVIEW",
                            "教师确认前必须先有学习者提交物");
                }
                assignment.verifyByTeacher(teacherUserId, cleanNullable(request.note()), Instant.now());
            } else {
                if (goalRepository == null) {
                    throw new BusinessException(HttpStatus.CONFLICT, "LEARNING_GOAL_REVISION_UNAVAILABLE",
                            "当前运行环境未启用学习目标返工回流");
                }
                LearningGoal goal = goalRepository.findByIdAndTenantIdAndUserId(
                                assignment.getLearningGoalId(), tenantId, assignment.getLearnerUserId())
                        .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                                "LEARNING_GOAL_NOT_FOUND", "作业绑定的学习目标不存在"));
                goal.requestRevision(Instant.now());
                goalRepository.save(goal);
                cancelStaleReviewTasks(tenantId, assignment.getLearnerUserId(), goal.getId());
                assignment.returnForRevision(teacherUserId, cleanNullable(request.note()), Instant.now());
            }
        } catch (IllegalStateException exception) {
            throw new BusinessException(HttpStatus.CONFLICT, "LEARNING_ASSIGNMENT_REVIEW_NOT_PENDING",
                    exception.getMessage());
        }
        LearningAssignment saved = assignmentRepository.save(assignment);
        if (evaluationRepository != null) {
            evaluationRepository.save(new LearningAssignmentEvaluation(
                    tenantId, assignment.getId(), assignment.getCourseId(), assignment.getLearnerUserId(),
                    teacherUserId,
                    "VERIFY".equals(decision) ? LearningAssignmentEvaluationDecision.VERIFY
                            : LearningAssignmentEvaluationDecision.RETURN,
                    rubric.contentCorrectnessScore(), rubric.evidenceQualityScore(),
                    rubric.transferReadinessScore(), cleanNullable(request.note()), Instant.now()));
        }
        notificationService.resolveForAssignmentState(
                tenantId, assignmentId, LearningAssignmentNotificationType.REVIEW_REQUIRED);
        if ("VERIFY".equals(decision)) {
            notificationService.ensureForTeacherReviewVerified(saved);
        } else {
            notificationService.ensureForTeacherRevisionRequired(saved);
        }
        notificationService.ensureForState(saved);
        return LearningAssignmentView.from(saved);
    }

    @Transactional(readOnly = true)
    public List<LearningAssignmentEvaluationView> evaluations(String tenantId, String userId,
                                                             String assignmentId) {
        LearningAssignment assignment = assignmentRepository.findByTenantIdAndId(tenantId, assignmentId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "LEARNING_ASSIGNMENT_NOT_FOUND", "课程作业不存在"));
        if (!userId.equals(assignment.getTeacherUserId())
                && !userId.equals(assignment.getLearnerUserId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "LEARNING_ASSIGNMENT_ACCESS_DENIED",
                    "无权查看该课程作业评价");
        }
        if (evaluationRepository == null) return List.of();
        return evaluationRepository.findByTenantIdAndLearningAssignmentIdOrderByCreatedAtDesc(
                        tenantId, assignmentId).stream()
                .map(LearningAssignmentEvaluationView::from).toList();
    }

    private RubricScores rubric(LearningAssignmentReviewRequest request) {
        if (request == null || request.contentCorrectnessScore() == null
                || request.evidenceQualityScore() == null
                || request.transferReadinessScore() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "LEARNING_ASSIGNMENT_RUBRIC_REQUIRED",
                    "教师审核必须填写内容正确性、证据质量和迁移准备度评分");
        }
        RubricScores rubric = new RubricScores(request.contentCorrectnessScore(),
                request.evidenceQualityScore(), request.transferReadinessScore());
        if (!rubric.isValid()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "LEARNING_ASSIGNMENT_RUBRIC_INVALID",
                    "教师量规评分必须都在 1 到 5 之间");
        }
        return rubric;
    }

    private record RubricScores(int contentCorrectnessScore, int evidenceQualityScore,
                                int transferReadinessScore) {
        private boolean isValid() {
            return contentCorrectnessScore >= 1 && contentCorrectnessScore <= 5
                    && evidenceQualityScore >= 1 && evidenceQualityScore <= 5
                    && transferReadinessScore >= 1 && transferReadinessScore <= 5;
        }
    }

    private String clean(String value) {
        return sanitizer.sanitize(value == null ? "" : value.trim());
    }

    private String cleanNullable(String value) {
        String cleaned = clean(value);
        return cleaned.isBlank() ? null : cleaned;
    }

    private void cancelStaleReviewTasks(String tenantId, String userId, String goalId) {
        if (taskRepository == null) return;
        taskRepository.findByTenantIdAndUserIdAndLearningGoalIdOrderByScheduledAtAsc(
                        tenantId, userId, goalId).stream()
                .filter(task -> task.getStatus() != LearningTaskStatus.COMPLETED
                        && task.getStatus() != LearningTaskStatus.CANCELLED)
                .forEach(task -> {
                    task.cancel();
                    taskRepository.save(task);
                });
    }
}
