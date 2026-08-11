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
    private final LearningAssignmentSubmissionRepository submissionRepository;
    private final EducationEnrollmentRepository enrollmentRepository;
    private final SensitiveDataSanitizer sanitizer;
    private final EducationCourseResultService resultService;

    public EducationCourseCompletionService(EducationCourseRepository courseRepository,
                                            LearningAssignmentRepository assignmentRepository,
                                            EducationEnrollmentRepository enrollmentRepository,
                                            SensitiveDataSanitizer sanitizer) {
        this(courseRepository, assignmentRepository, null, enrollmentRepository, sanitizer, null);
    }

    public EducationCourseCompletionService(EducationCourseRepository courseRepository,
                                            LearningAssignmentRepository assignmentRepository,
                                            LearningAssignmentSubmissionRepository submissionRepository,
                                            EducationEnrollmentRepository enrollmentRepository,
                                            SensitiveDataSanitizer sanitizer) {
        this(courseRepository, assignmentRepository, submissionRepository, enrollmentRepository, sanitizer, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public EducationCourseCompletionService(EducationCourseRepository courseRepository,
                                            LearningAssignmentRepository assignmentRepository,
                                            LearningAssignmentSubmissionRepository submissionRepository,
                                            EducationEnrollmentRepository enrollmentRepository,
                                            SensitiveDataSanitizer sanitizer,
                                            EducationCourseResultService resultService) {
        this.courseRepository = courseRepository;
        this.assignmentRepository = assignmentRepository;
        this.submissionRepository = submissionRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.sanitizer = sanitizer;
        this.resultService = resultService;
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
            // 结课接口保持幂等，但不能把“课程已结课、结果缺失”的历史异常永久隐藏。
            // 生产构造会注入结果服务；重试时使用课程自身冻结的完成者和时间，避免
            // 后续调用者改写历史快照的归属。
            if (resultService != null) {
                resultService.capture(tenantId, course.getCompletedByUserId(), course);
            }
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
        List<EducationEnrollment> activeEnrollments = enrollmentRepository
                .findByTenantIdAndCourseIdAndStatus(tenantId, course.getId(), EducationEnrollmentStatus.ACTIVE);
        if (activeEnrollments.isEmpty()) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "EDUCATION_COURSE_ROSTER_EMPTY",
                    "课程没有活跃学习者，暂不能结课");
        }
        java.util.Set<String> activeLearnerIds = activeEnrollments.stream()
                .map(EducationEnrollment::getLearnerUserId).collect(java.util.stream.Collectors.toSet());
        java.util.Set<String> assignedLearnerIds = effective.stream()
                .map(LearningAssignment::getLearnerUserId)
                .filter(activeLearnerIds::contains)
                .collect(java.util.stream.Collectors.toSet());
        long missingLearners = activeLearnerIds.size() - assignedLearnerIds.size();
        if (missingLearners > 0) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "EDUCATION_COURSE_ROSTER_ASSIGNMENTS_REQUIRED",
                    "仍有 " + missingLearners + " 名活跃学习者没有课程作业，暂不能结课");
        }
        if (submissionRepository != null) {
            long missingSubmissions = effective.stream()
                    .filter(item -> !submissionRepository.existsByTenantIdAndLearningAssignmentId(
                            tenantId, item.getId()))
                    .count();
            if (missingSubmissions > 0) {
                throw new BusinessException(HttpStatus.CONFLICT,
                        "EDUCATION_COURSE_SUBMISSIONS_REQUIRED",
                        "仍有 " + missingSubmissions + " 份作业没有学习者提交物，暂不能结课");
            }
        }
        String note = cleanNullable(request == null ? null : request.note());
        course.complete(teacherUserId, note, Instant.now());
        EducationCourse saved = courseRepository.save(course);
        if (resultService != null) {
            resultService.capture(tenantId, teacherUserId, saved);
        }
        return view(saved);
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
