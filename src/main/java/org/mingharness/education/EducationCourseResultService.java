package org.mingharness.education;

import org.mingharness.common.BusinessException;
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
                                             EducationCourse course, Instant completedAt) {
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
                completedAt, teacherUserId));
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
