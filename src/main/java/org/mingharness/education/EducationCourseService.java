package org.mingharness.education;

import org.mingharness.common.BusinessException;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.education.api.EducationCourseRequest;
import org.mingharness.education.api.EducationCourseView;
import org.mingharness.education.api.EducationEnrollmentRequest;
import org.mingharness.education.api.EducationEnrollmentView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * 管理课程实例和名单边界。课程是批量布置与教师汇总的稳定聚合根，
 * 但不直接持有作业集合，避免课程状态机和作业状态机互相污染。
 */
@Service
public class EducationCourseService {

    private final EducationCourseRepository courseRepository;
    private final EducationEnrollmentRepository enrollmentRepository;
    private final SensitiveDataSanitizer sanitizer;

    public EducationCourseService(EducationCourseRepository courseRepository,
                                  EducationEnrollmentRepository enrollmentRepository,
                                  SensitiveDataSanitizer sanitizer) {
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.sanitizer = sanitizer;
    }

    @Transactional
    public EducationCourseView create(String tenantId, String ownerUserId,
                                      EducationCourseRequest request) {
        String code = clean(request.code());
        if (courseRepository.findByTenantIdAndCode(tenantId, code).isPresent()) {
            throw new BusinessException(HttpStatus.CONFLICT, "EDUCATION_COURSE_CODE_EXISTS",
                    "该课程代码已存在");
        }
        EducationCourse saved = courseRepository.save(new EducationCourse(
                tenantId, ownerUserId, code, clean(request.title()), clean(request.subject()),
                clean(request.gradeLevel()), clean(request.curriculumVersion())));
        return view(saved);
    }

    @Transactional(readOnly = true)
    public List<EducationCourseView> list(String tenantId, String userId) {
        LinkedHashMap<String, EducationCourse> merged = new LinkedHashMap<>();
        courseRepository.findByTenantIdAndOwnerUserIdOrderByUpdatedAtDesc(tenantId, userId)
                .forEach(course -> merged.put(course.getId(), course));
        List<String> enrolledCourseIds = enrollmentRepository
                .findByTenantIdAndLearnerUserIdAndStatus(tenantId, userId, EducationEnrollmentStatus.ACTIVE)
                .stream().map(EducationEnrollment::getCourseId).toList();
        if (!enrolledCourseIds.isEmpty()) {
            courseRepository.findByTenantIdAndIdInOrderByUpdatedAtDesc(tenantId, enrolledCourseIds)
                    .forEach(course -> merged.put(course.getId(), course));
        }
        return new ArrayList<>(merged.values()).stream()
                .sorted(Comparator.comparing(EducationCourse::getUpdatedAt).reversed())
                .map(this::view)
                .toList();
    }

    /** 管理员只读查看同租户全量课程，不能借此获得课程负责人写权限。 */
    @Transactional(readOnly = true)
    public List<EducationCourseView> listForGovernance(String tenantId) {
        return courseRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId).stream()
                .map(this::view)
                .toList();
    }

    @Transactional(readOnly = true)
    public EducationCourseView getForParticipant(String tenantId, String userId, String courseId) {
        EducationCourse course = find(tenantId, courseId);
        ensureParticipant(course, tenantId, userId);
        return view(course);
    }

    /** 管理员治理页只读读取同租户课程详情，不获得课程负责人写权限。 */
    @Transactional(readOnly = true)
    public EducationCourseView getForGovernance(String tenantId, String courseId) {
        return view(find(tenantId, courseId));
    }

    /** 供同租户管理员只读聚合查询复用课程实体，不暴露课程写权限。 */
    @Transactional(readOnly = true)
    public EducationCourse requireGovernanceCourse(String tenantId, String courseId) {
        return find(tenantId, courseId);
    }

    @Transactional
    public EducationEnrollmentView enroll(String tenantId, String ownerUserId, String courseId,
                                          EducationEnrollmentRequest request) {
        EducationCourse course = find(tenantId, courseId);
        ensureOwner(course, ownerUserId);
        ensureActive(course);
        String learnerUserId = clean(request.learnerUserId());
        EducationEnrollment enrollment = enrollmentRepository
                .findByTenantIdAndCourseIdAndLearnerUserId(tenantId, courseId, learnerUserId)
                .map(existing -> {
                    if (existing.getStatus() == EducationEnrollmentStatus.REMOVED) {
                        existing.reactivate(Instant.now());
                    }
                    return existing;
                })
                .orElseGet(() -> new EducationEnrollment(tenantId, courseId, learnerUserId, Instant.now()));
        return EducationEnrollmentView.from(enrollmentRepository.save(enrollment));
    }

    @Transactional
    public EducationEnrollmentView removeEnrollment(String tenantId, String ownerUserId,
                                                     String courseId, String learnerUserId) {
        EducationCourse course = find(tenantId, courseId);
        ensureOwner(course, ownerUserId);
        EducationEnrollment enrollment = enrollmentRepository
                .findByTenantIdAndCourseIdAndLearnerUserId(tenantId, courseId, clean(learnerUserId))
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "EDUCATION_ENROLLMENT_NOT_FOUND", "课程名单成员不存在"));
        enrollment.remove(Instant.now());
        return EducationEnrollmentView.from(enrollmentRepository.save(enrollment));
    }

    @Transactional(readOnly = true)
    public List<EducationEnrollmentView> roster(String tenantId, String ownerUserId, String courseId) {
        EducationCourse course = find(tenantId, courseId);
        ensureOwner(course, ownerUserId);
        return enrollmentRepository.findByTenantIdAndCourseIdOrderByEnrolledAtAsc(tenantId, courseId)
                .stream().map(EducationEnrollmentView::from).toList();
    }

    /** 管理员只读查看课程名单；名单写入仍只能由课程教师执行。 */
    @Transactional(readOnly = true)
    public List<EducationEnrollmentView> rosterForGovernance(String tenantId, String courseId) {
        find(tenantId, courseId);
        return enrollmentRepository.findByTenantIdAndCourseIdOrderByEnrolledAtAsc(tenantId, courseId)
                .stream().map(EducationEnrollmentView::from).toList();
    }

    @Transactional
    public EducationCourseView archive(String tenantId, String ownerUserId, String courseId) {
        EducationCourse course = find(tenantId, courseId);
        ensureOwner(course, ownerUserId);
        course.archive(Instant.now());
        return view(courseRepository.save(course));
    }

    /** 供作业服务校验课程归属和课程上下文，避免请求体覆盖课程定义。 */
    @Transactional(readOnly = true)
    public EducationCourse requireOwnerCourse(String tenantId, String ownerUserId, String courseId) {
        EducationCourse course = find(tenantId, courseId);
        ensureOwner(course, ownerUserId);
        return course;
    }

    /** 供批量布置和单人布置复用的成员资格检查。 */
    @Transactional(readOnly = true)
    public void requireActiveEnrollment(String tenantId, String courseId, String learnerUserId) {
        EducationCourse course = find(tenantId, courseId);
        ensureActive(course);
        if (!enrollmentRepository.existsByTenantIdAndCourseIdAndLearnerUserIdAndStatus(
                tenantId, courseId, clean(learnerUserId), EducationEnrollmentStatus.ACTIVE)) {
            throw new BusinessException(HttpStatus.CONFLICT, "EDUCATION_LEARNER_NOT_ENROLLED",
                    "学习者不是该课程的活跃名单成员");
        }
    }

    private EducationCourse find(String tenantId, String courseId) {
        return courseRepository.findByTenantIdAndId(tenantId, clean(courseId))
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "EDUCATION_COURSE_NOT_FOUND", "课程实例不存在"));
    }

    private EducationCourseView view(EducationCourse course) {
        return EducationCourseView.from(course,
                enrollmentRepository.countByTenantIdAndCourseIdAndStatus(
                        course.getTenantId(), course.getId(), EducationEnrollmentStatus.ACTIVE));
    }

    private void ensureParticipant(EducationCourse course, String tenantId, String userId) {
        if (course.getOwnerUserId().equals(userId)) return;
        if (!enrollmentRepository.existsByTenantIdAndCourseIdAndLearnerUserIdAndStatus(
                tenantId, course.getId(), userId, EducationEnrollmentStatus.ACTIVE)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "EDUCATION_COURSE_ACCESS_DENIED",
                    "无权访问该课程实例");
        }
    }

    private void ensureOwner(EducationCourse course, String userId) {
        if (!course.getOwnerUserId().equals(userId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "EDUCATION_COURSE_OWNER_ONLY",
                    "只有课程教师可以管理课程名单");
        }
    }

    private void ensureActive(EducationCourse course) {
        if (!course.isActive()) {
            if (course.getStatus() == EducationCourseStatus.COMPLETED) {
                throw new BusinessException(HttpStatus.CONFLICT, "EDUCATION_COURSE_COMPLETED",
                        "已结课课程不能再变更名单或布置作业");
            }
            throw new BusinessException(HttpStatus.CONFLICT, "EDUCATION_COURSE_ARCHIVED",
                    "已归档课程不能再变更名单或布置作业");
        }
    }

    private String clean(String value) {
        return sanitizer.sanitize(value == null ? "" : value.trim());
    }
}
