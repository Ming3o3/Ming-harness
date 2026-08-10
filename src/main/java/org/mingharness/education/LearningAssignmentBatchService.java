package org.mingharness.education;

import org.mingharness.common.BusinessException;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.education.api.EducationCourseAssignmentBatchView;
import org.mingharness.education.api.EducationCourseAssignmentRequest;
import org.mingharness.education.api.LearningAssignmentRequest;
import org.mingharness.education.api.LearningAssignmentView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;

/** 将课程活跃名单展开为独立作业，并以持久化批次键防止提交重试重复分配。 */
@Service
public class LearningAssignmentBatchService {

    private final EducationCourseService courseService;
    private final EducationEnrollmentRepository enrollmentRepository;
    private final LearningAssignmentRepository assignmentRepository;
    private final LearningAssignmentService assignmentService;
    private final SensitiveDataSanitizer sanitizer;

    public LearningAssignmentBatchService(EducationCourseService courseService,
                                          EducationEnrollmentRepository enrollmentRepository,
                                          LearningAssignmentRepository assignmentRepository,
                                          LearningAssignmentService assignmentService,
                                          SensitiveDataSanitizer sanitizer) {
        this.courseService = courseService;
        this.enrollmentRepository = enrollmentRepository;
        this.assignmentRepository = assignmentRepository;
        this.assignmentService = assignmentService;
        this.sanitizer = sanitizer;
    }

    @Transactional
    public EducationCourseAssignmentBatchView assign(String tenantId, String teacherUserId,
                                                      String courseId,
                                                      EducationCourseAssignmentRequest request,
                                                      String idempotencyKey) {
        String batchId = clean(idempotencyKey);
        if (batchId.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ASSIGNMENT_BATCH_IDEMPOTENCY_REQUIRED",
                    "批量布置必须提供 Idempotency-Key");
        }
        EducationCourse course = courseService.requireOwnerCourse(tenantId, teacherUserId, courseId);
        if (!course.isActive()) {
            if (course.getStatus() == EducationCourseStatus.COMPLETED) {
                throw new BusinessException(HttpStatus.CONFLICT, "EDUCATION_COURSE_COMPLETED",
                        "已结课课程不能再布置作业");
            }
            throw new BusinessException(HttpStatus.CONFLICT, "EDUCATION_COURSE_ARCHIVED",
                    "已归档课程不能再布置作业");
        }
        String requestHash = requestHash(course, request);
        List<LearningAssignment> existing = assignmentRepository
                .findByTenantIdAndCourseIdAndBatchIdOrderByCreatedAtAsc(tenantId, course.getId(), batchId);
        if (!existing.isEmpty()) {
            String existingHash = existing.get(0).getBatchRequestHash();
            if (existingHash != null && !existingHash.equals(requestHash)) {
                throw new BusinessException(HttpStatus.CONFLICT,
                        "ASSIGNMENT_BATCH_KEY_REUSED_WITH_DIFFERENT_REQUEST",
                        "同一幂等键已经用于另一份课程作业请求");
            }
            return view(course.getId(), batchId, true, existing);
        }

        List<EducationEnrollment> learners = enrollmentRepository.findByTenantIdAndCourseIdAndStatus(
                tenantId, course.getId(), EducationEnrollmentStatus.ACTIVE);
        if (learners.isEmpty()) {
            throw new BusinessException(HttpStatus.CONFLICT, "EDUCATION_COURSE_ROSTER_EMPTY",
                    "课程没有活跃学习者，不能批量布置作业");
        }
        List<LearningAssignment> created = learners.stream()
                .map(enrollment -> assignmentService.create(tenantId, teacherUserId,
                        new LearningAssignmentRequest(enrollment.getLearnerUserId(),
                                clean(request.title()), clean(request.instructions()), course.getSubject(),
                                course.getGradeLevel(), course.getCurriculumVersion(), clean(request.conceptKey()),
                                request.targetMastery(), request.dueAt(), course.getId()),
                        batchId, requestHash))
                .toList();
        return view(course.getId(), batchId, false, created);
    }

    private EducationCourseAssignmentBatchView view(String courseId, String batchId, boolean reused,
                                                    List<LearningAssignment> assignments) {
        Instant createdAt = assignments.isEmpty() ? Instant.now() : assignments.get(0).getCreatedAt();
        return new EducationCourseAssignmentBatchView(courseId, batchId, reused, assignments.size(), createdAt,
                assignments.stream().map(LearningAssignmentView::from).toList());
    }

    private String clean(String value) {
        return sanitizer.sanitize(value == null ? "" : value.trim());
    }

    private String requestHash(EducationCourse course, EducationCourseAssignmentRequest request) {
        String canonical = String.join("\n",
                course.getId(),
                clean(request.title()),
                clean(request.instructions()),
                clean(request.conceptKey()),
                Double.toString(request.effectiveTargetMastery()),
                request.dueAt() == null ? "" : request.dueAt().toString());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境缺少 SHA-256", exception);
        }
    }
}
