package org.mingharness.education;

import org.mingharness.common.BusinessException;
import org.mingharness.education.api.EducationCourseLearnerResultView;
import org.mingharness.education.api.EducationCourseResultView;
import org.mingharness.education.api.EducationCourseView;
import org.mingharness.education.api.LearningAssignmentProgressView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 在课程结课时固化课程和学习者结果，避免后续保持度复习改写历史结业事实。 */
@Service
public class EducationCourseResultService {

    private final EducationCourseResultRepository resultRepository;
    private final EducationCourseLearnerResultRepository learnerResultRepository;
    private final EducationCourseService courseService;
    private final LearningAssignmentRepository assignmentRepository;
    private final EducationEnrollmentRepository enrollmentRepository;
    private final LearningAssignmentSubmissionRepository submissionRepository;
    private final LearningAssignmentProgressService assignmentProgressService;

    public EducationCourseResultService(EducationCourseResultRepository resultRepository,
                                        EducationCourseLearnerResultRepository learnerResultRepository,
                                        EducationCourseService courseService,
                                        LearningAssignmentRepository assignmentRepository,
                                        EducationEnrollmentRepository enrollmentRepository,
                                        LearningAssignmentSubmissionRepository submissionRepository,
                                        LearningAssignmentProgressService assignmentProgressService) {
        this.resultRepository = resultRepository;
        this.learnerResultRepository = learnerResultRepository;
        this.courseService = courseService;
        this.assignmentRepository = assignmentRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.submissionRepository = submissionRepository;
        this.assignmentProgressService = assignmentProgressService;
    }

    @Transactional
    public EducationCourseResultView capture(String tenantId, String teacherUserId,
                                             EducationCourse course) {
        if (course == null || course.getStatus() != EducationCourseStatus.COMPLETED) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "EDUCATION_COURSE_RESULT_CAPTURE_NOT_ALLOWED",
                    "只有已结课课程才能形成结果快照");
        }
        if (course.getCompletedAt() == null || course.getCompletedByUserId() == null
                || course.getCompletedByUserId().isBlank()) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "EDUCATION_COURSE_COMPLETION_FACT_INCOMPLETE",
                    "课程缺少完整的结课时间或完成者事实，无法形成结果快照");
        }
        EducationCourseResult existing = resultRepository
                .findByTenantIdAndCourseId(tenantId, course.getId()).orElse(null);
        if (existing != null) return view(existing, course, teacherUserId);

        List<EducationEnrollment> activeEnrollments = enrollmentRepository
                .findByTenantIdAndCourseIdAndStatus(
                        tenantId, course.getId(), EducationEnrollmentStatus.ACTIVE);
        Set<String> activeLearnerIds = activeEnrollments.stream()
                .map(EducationEnrollment::getLearnerUserId).collect(java.util.stream.Collectors.toSet());
        Map<String, LearnerAccumulator> byLearner = new LinkedHashMap<>();
        activeLearnerIds.forEach(id -> byLearner.put(id, new LearnerAccumulator(id)));

        List<LearningAssignment> effectiveAssignments = assignmentRepository
                .findByTenantIdAndCourseIdOrderByCreatedAtDesc(tenantId, course.getId()).stream()
                // 结课快照与实时进度使用同一份活跃名单边界。被移除成员的
                // 历史作业和学习证据仍保留，但不再进入当前班级的结课结果。
                .filter(item -> activeLearnerIds.contains(item.getLearnerUserId()))
                .filter(item -> item.getStatus() != LearningAssignmentStatus.CANCELLED)
                .toList();
        long assignmentCompleted = 0;
        long assignmentVerified = 0;
        long submissionCovered = 0;
        double masteryProgressSum = 0.0;
        double masteryGainSum = 0.0;
        long masteryCount = 0;
        Set<String> coveredLearnerIds = new java.util.HashSet<>();
        for (LearningAssignment assignment : effectiveAssignments) {
            if (assignment.getStatus() == LearningAssignmentStatus.COMPLETED) assignmentCompleted++;
            if (assignment.getReviewStatus() == LearningAssignmentReviewStatus.VERIFIED) assignmentVerified++;
            boolean hasSubmission = submissionRepository.existsByTenantIdAndLearningAssignmentId(
                    tenantId, assignment.getId());
            if (hasSubmission) submissionCovered++;
            LearningAssignmentProgressView progress = assignmentProgressService.get(
                    tenantId, teacherUserId, assignment.getId());
            masteryProgressSum += progress.masteryProgress();
            masteryGainSum += progress.masteryGain();
            masteryCount++;
            LearnerAccumulator accumulator = byLearner.get(assignment.getLearnerUserId());
            if (accumulator != null) {
                accumulator.accept(assignment, progress, hasSubmission);
                coveredLearnerIds.add(assignment.getLearnerUserId());
            }
        }
        EducationCourseResult saved = resultRepository.save(new EducationCourseResult(
                tenantId, course.getId(), activeLearnerIds.size(), coveredLearnerIds.size(),
                effectiveAssignments.size(), assignmentCompleted, assignmentVerified, submissionCovered,
                average(masteryProgressSum, masteryCount), average(masteryGainSum, masteryCount),
                course.getCompletedAt(), course.getCompletedByUserId()));
        learnerResultRepository.saveAll(byLearner.values().stream()
                .map(accumulator -> accumulator.toEntity(tenantId, saved.getId(), course.getId()))
                .toList());
        return view(saved, course, teacherUserId);
    }

    @Transactional(readOnly = true)
    public EducationCourseResultView get(String tenantId, String userId, String courseId) {
        EducationCourseView course = courseService.getForParticipant(tenantId, userId, courseId);
        EducationCourseResult result = resultRepository.findByTenantIdAndCourseId(
                        tenantId, course.id())
                .orElseThrow(() -> new BusinessException(HttpStatus.CONFLICT,
                        "EDUCATION_COURSE_RESULT_NOT_AVAILABLE", "课程尚未形成结课结果快照"));
        List<EducationCourseLearnerResult> learners = learnerResultRepository
                .findByTenantIdAndCourseResultIdOrderByLearnerUserIdAsc(tenantId, result.getId());
        if (!course.ownerUserId().equals(userId)) {
            learners = learners.stream().filter(item -> item.getLearnerUserId().equals(userId)).toList();
        }
        return EducationCourseResultView.from(result, course, learners);
    }

    /**
     * Export the teacher-visible immutable snapshot as an Excel-compatible CSV.
     * The export is deliberately owner-only: a learner can read their own result
     * through the JSON endpoint but must not receive the class roster.
     */
    @Transactional(readOnly = true)
    public String exportCsv(String tenantId, String userId, String courseId) {
        EducationCourseView course = courseService.getForParticipant(tenantId, userId, courseId);
        if (!course.ownerUserId().equals(userId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "EDUCATION_COURSE_OWNER_ONLY",
                    "只有课程教师可以导出全班结课结果");
        }
        EducationCourseResultView snapshot = get(tenantId, userId, courseId);
        StringBuilder csv = new StringBuilder("\uFEFF");
        appendRow(csv, "record_type", "course_id", "course_code", "course_title", "completed_at",
                "completed_by_user_id", "active_learner_total", "learners_with_assignments",
                "effective_assignment_total", "assignment_completed", "assignment_verified",
                "submission_covered", "average_mastery_progress", "average_mastery_gain",
                "learner_user_id", "learner_effective_assignment_total", "learner_assignment_completed",
                "learner_assignment_verified", "learner_submission_covered",
                "learner_average_mastery_progress", "learner_average_mastery_gain", "last_activity_at");
        appendRow(csv, "COURSE", snapshot.courseId(), snapshot.courseCode(), snapshot.courseTitle(),
                snapshot.completedAt(), snapshot.completedByUserId(), snapshot.activeLearnerTotal(),
                snapshot.learnersWithAssignments(), snapshot.effectiveAssignmentTotal(),
                snapshot.assignmentCompleted(), snapshot.assignmentVerified(), snapshot.submissionCovered(),
                snapshot.averageMasteryProgress(), snapshot.averageMasteryGain(), null, null, null, null,
                null, null, null, null);
        for (EducationCourseLearnerResultView learner : snapshot.learners()) {
            appendRow(csv, "LEARNER", snapshot.courseId(), snapshot.courseCode(), snapshot.courseTitle(),
                    snapshot.completedAt(), snapshot.completedByUserId(), null, null, null, null, null,
                    null, null, null, learner.learnerUserId(), learner.effectiveAssignmentTotal(),
                    learner.assignmentCompleted(), learner.assignmentVerified(), learner.submissionCovered(),
                    learner.averageMasteryProgress(), learner.averageMasteryGain(), learner.lastActivityAt());
        }
        return csv.toString();
    }

    private void appendRow(StringBuilder csv, Object... values) {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) csv.append(',');
            Object value = values[i];
            String text = value == null ? "" : String.valueOf(value);
            // Prevent spreadsheet formula injection for user-controlled text fields.
            if (value instanceof String && !text.isEmpty()
                    && "=+-@".indexOf(text.charAt(0)) >= 0) {
                text = "'" + text;
            }
            csv.append('"').append(text.replace("\"", "\"\"")).append('"');
        }
        csv.append("\r\n");
    }

    private EducationCourseResultView view(EducationCourseResult result,
                                           EducationCourse course, String userId) {
        EducationCourseView courseView = EducationCourseView.from(course,
                enrollmentRepository.countByTenantIdAndCourseIdAndStatus(
                        course.getTenantId(), course.getId(), EducationEnrollmentStatus.ACTIVE));
        List<EducationCourseLearnerResult> learners = learnerResultRepository
                .findByTenantIdAndCourseResultIdOrderByLearnerUserIdAsc(
                        result.getTenantId(), result.getId());
        if (!course.getOwnerUserId().equals(userId)) {
            learners = learners.stream().filter(item -> item.getLearnerUserId().equals(userId)).toList();
        }
        return EducationCourseResultView.from(result, courseView, learners);
    }

    private double average(double sum, long count) {
        return count <= 0 ? 0.0 : sum / count;
    }

    private static final class LearnerAccumulator {
        private final String learnerUserId;
        private long effectiveAssignmentTotal;
        private long assignmentCompleted;
        private long assignmentVerified;
        private long submissionCovered;
        private double masteryProgressSum;
        private double masteryGainSum;
        private Instant lastActivityAt;

        private LearnerAccumulator(String learnerUserId) {
            this.learnerUserId = learnerUserId;
        }

        private void accept(LearningAssignment assignment, LearningAssignmentProgressView progress,
                            boolean hasSubmission) {
            effectiveAssignmentTotal++;
            if (assignment.getStatus() == LearningAssignmentStatus.COMPLETED) assignmentCompleted++;
            if (assignment.getReviewStatus() == LearningAssignmentReviewStatus.VERIFIED) assignmentVerified++;
            if (hasSubmission) submissionCovered++;
            masteryProgressSum += progress.masteryProgress();
            masteryGainSum += progress.masteryGain();
            lastActivityAt = latest(lastActivityAt, assignment.getUpdatedAt(),
                    progress.lastAssessmentAt(), progress.lastFeedbackAt());
        }

        private EducationCourseLearnerResult toEntity(String tenantId, String courseResultId,
                                                       String courseId) {
            return new EducationCourseLearnerResult(tenantId, courseResultId, courseId,
                    learnerUserId, effectiveAssignmentTotal, assignmentCompleted, assignmentVerified,
                    submissionCovered, average(masteryProgressSum, effectiveAssignmentTotal),
                    average(masteryGainSum, effectiveAssignmentTotal), lastActivityAt);
        }

        private Instant latest(Instant current, Instant... values) {
            Instant result = current;
            for (Instant value : values) {
                if (value != null && (result == null || value.isAfter(result))) result = value;
            }
            return result;
        }

        private double average(double sum, long count) {
            return count <= 0 ? 0.0 : sum / count;
        }
    }
}
