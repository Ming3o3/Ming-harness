package org.mingharness.education;

import org.mingharness.common.BusinessException;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.education.api.LearningAssignmentReviewRequest;
import org.mingharness.education.api.LearningAssignmentView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

/** 将学习者达标事实转换为教师可确认的业务结果。 */
@Service
public class LearningAssignmentReviewService {

    private final LearningAssignmentRepository assignmentRepository;
    private final LearningGoalRepository goalRepository;
    private final LearningTaskRepository taskRepository;
    private final LearningAssignmentNotificationService notificationService;
    private final SensitiveDataSanitizer sanitizer;

    public LearningAssignmentReviewService(LearningAssignmentRepository assignmentRepository,
                                           LearningAssignmentNotificationService notificationService,
                                           SensitiveDataSanitizer sanitizer) {
        this(assignmentRepository, null, notificationService, sanitizer);
    }

    public LearningAssignmentReviewService(LearningAssignmentRepository assignmentRepository,
                                           LearningGoalRepository goalRepository,
                                           LearningAssignmentNotificationService notificationService,
                                           SensitiveDataSanitizer sanitizer) {
        this(assignmentRepository, goalRepository, null, notificationService, sanitizer);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public LearningAssignmentReviewService(LearningAssignmentRepository assignmentRepository,
                                           LearningGoalRepository goalRepository,
                                           LearningTaskRepository taskRepository,
                                           LearningAssignmentNotificationService notificationService,
                                           SensitiveDataSanitizer sanitizer) {
        this.assignmentRepository = assignmentRepository;
        this.goalRepository = goalRepository;
        this.taskRepository = taskRepository;
        this.notificationService = notificationService;
        this.sanitizer = sanitizer;
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
        try {
            if ("VERIFY".equals(decision)) {
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
