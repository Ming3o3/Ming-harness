package org.mingharness.education;

import org.mingharness.common.BusinessException;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.education.api.EducationCourseCompletionRequest;
import org.mingharness.education.api.EducationCourseView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** 将逐份作业结果收敛为不可逆的课程结课事实，补齐课程级业务出口。 */
@Service
public class EducationCourseCompletionService {

    private final EducationCourseRepository courseRepository;
    private final LearningAssignmentRepository assignmentRepository;
    private final EducationEnrollmentRepository enrollmentRepository;
    private final SensitiveDataSanitizer sanitizer;

    public EducationCourseCompletionService(EducationCourseRepository courseRepository,
                                            LearningAssignmentRepository assignmentRepository,
                                            EducationEnrollmentRepository enrollmentRepository,
                                            SensitiveDataSanitizer sanitizer) {
        this.courseRepository = courseRepository;
        this.assignmentRepository = assignmentRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.sanitizer = sanitizer;
    }

    @Transactional
    public EducationCourseView complete(String tenantId, String teacherUserId, String courseId,
                                       EducationCourseCompletionRequest request) {
        EducationCourse course = courseRepository.findByTenantIdAndId(tenantId, clean(courseId))
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "EDUCATION_COURSE_NOT_FOUND", "课程实例不存在"));
        if (!course.getOwnerUserId().equals(teacherUserId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "EDUCATION_COURSE_OWNER_ONLY",
                    "只有课程教师可以结课");
        }
        if (course.getStatus() == EducationCourseStatus.ARCHIVED) {
            return view(course);
        }
        if (course.getStatus() == EducationCourseStatus.COMPLETED) {
            return view(course);
        }

        List<LearningAssignment> assignments = assignmentRepository
                .findByTenantIdAndCourseIdOrderByCreatedAtDesc(tenantId, course.getId());
        List<LearningAssignment> effective = assignments.stream()
                .filter(item -> item.getStatus() != LearningAssignmentStatus.CANCELLED)
                .toList();
        if (effective.isEmpty()) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "EDUCATION_COURSE_ASSIGNMENTS_EMPTY",
                    "课程没有可结课的有效作业");
        }
        long blocked = effective.stream()
                .filter(item -> item.getStatus() != LearningAssignmentStatus.COMPLETED
                        || item.getReviewStatus() != LearningAssignmentReviewStatus.VERIFIED)
                .count();
        if (blocked > 0) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "EDUCATION_COURSE_NOT_READY_TO_COMPLETE",
                    "仍有 " + blocked + " 份作业未完成教师确认，暂不能结课");
        }
        String note = cleanNullable(request == null ? null : request.note());
        course.complete(teacherUserId, note, Instant.now());
        return view(courseRepository.save(course));
    }

    private EducationCourseView view(EducationCourse course) {
        return EducationCourseView.from(course,
                enrollmentRepository.countByTenantIdAndCourseIdAndStatus(
                        course.getTenantId(), course.getId(), EducationEnrollmentStatus.ACTIVE));
    }

    private String clean(String value) {
        return sanitizer.sanitize(value == null ? "" : value.trim());
    }

    private String cleanNullable(String value) {
        String cleaned = clean(value);
        return cleaned.isBlank() ? null : cleaned;
    }
}
