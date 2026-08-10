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
    private final LearningAssignmentNotificationService notificationService;
    private final SensitiveDataSanitizer sanitizer;

    public LearningAssignmentReviewService(LearningAssignmentRepository assignmentRepository,
                                           LearningAssignmentNotificationService notificationService,
                                           SensitiveDataSanitizer sanitizer) {
        this.assignmentRepository = assignmentRepository;
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
        if (!"VERIFY".equals(decision)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "LEARNING_ASSIGNMENT_REVIEW_DECISION_INVALID",
                    "当前只支持 VERIFY 教师确认决定");
        }
        if (assignment.getReviewStatus() == LearningAssignmentReviewStatus.VERIFIED) {
            return LearningAssignmentView.from(assignment);
        }
        if (assignment.getStatus() != LearningAssignmentStatus.COMPLETED
                || assignment.getReviewStatus() != LearningAssignmentReviewStatus.PENDING) {
            throw new BusinessException(HttpStatus.CONFLICT, "LEARNING_ASSIGNMENT_REVIEW_NOT_PENDING",
                    "当前作业不在待教师确认状态");
        }
        try {
            assignment.verifyByTeacher(teacherUserId, cleanNullable(request.note()), Instant.now());
        } catch (IllegalStateException exception) {
            throw new BusinessException(HttpStatus.CONFLICT, "LEARNING_ASSIGNMENT_REVIEW_NOT_PENDING",
                    exception.getMessage());
        }
        LearningAssignment saved = assignmentRepository.save(assignment);
        notificationService.resolveForAssignmentState(
                tenantId, assignmentId, LearningAssignmentNotificationType.REVIEW_REQUIRED);
        notificationService.ensureForTeacherReviewVerified(saved);
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
}
