package org.mingharness.education;

import org.mingharness.education.api.EducationCourseLearnerProgressView;
import org.mingharness.education.api.EducationCourseProgressView;
import org.mingharness.education.api.EducationCourseView;
import org.mingharness.education.api.LearningAssignmentProgressView;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 将课程名单和已有单份作业进度投影为教师可行动的班级结果。 */
@Service
public class EducationCourseProgressService {

    private static final int MAX_ASSIGNMENTS = 500;

    private final EducationCourseService courseService;
    private final EducationEnrollmentRepository enrollmentRepository;
    private final LearningAssignmentRepository assignmentRepository;
    private final LearningAssignmentProgressService assignmentProgressService;
    private final LearningAssignmentFeedbackRepository feedbackRepository;

    public EducationCourseProgressService(EducationCourseService courseService,
                                          EducationEnrollmentRepository enrollmentRepository,
                                          LearningAssignmentRepository assignmentRepository,
                                          LearningAssignmentProgressService assignmentProgressService,
                                          LearningAssignmentFeedbackRepository feedbackRepository) {
        this.courseService = courseService;
        this.enrollmentRepository = enrollmentRepository;
        this.assignmentRepository = assignmentRepository;
        this.assignmentProgressService = assignmentProgressService;
        this.feedbackRepository = feedbackRepository;
    }

    @Transactional(readOnly = true)
    public EducationCourseProgressView get(String tenantId, String teacherUserId, String courseId,
                                           int requestedLimit) {
        EducationCourse course = courseService.requireOwnerCourse(tenantId, teacherUserId, courseId);
        int limit = Math.max(1, Math.min(MAX_ASSIGNMENTS, requestedLimit <= 0 ? MAX_ASSIGNMENTS : requestedLimit));
        List<LearningAssignment> allAssignments = assignmentRepository
                .findByTenantIdAndCourseIdOrderByCreatedAtDesc(tenantId, course.getId());
        boolean truncated = allAssignments.size() > limit;
        List<LearningAssignment> assignments = allAssignments.stream().limit(limit).toList();
        List<EducationEnrollment> enrollments = enrollmentRepository
                .findByTenantIdAndCourseIdOrderByEnrolledAtAsc(tenantId, course.getId());

        Map<String, LearnerAccumulator> byLearner = new LinkedHashMap<>();
        enrollments.stream().filter(item -> item.getStatus() == EducationEnrollmentStatus.ACTIVE)
                .forEach(item -> byLearner.put(item.getLearnerUserId(), new LearnerAccumulator(item.getLearnerUserId())));

        Totals totals = new Totals();
        for (LearningAssignment assignment : assignments) {
            totals.accept(assignment);
            LearnerAccumulator accumulator = byLearner.computeIfAbsent(
                    assignment.getLearnerUserId(), LearnerAccumulator::new);
            LearningAssignmentProgressView progress = assignmentProgressService.get(
                    tenantId, teacherUserId, assignment.getId());
            long openInterventions = feedbackRepository.findByTenantIdAndLearningAssignmentIdOrderByCreatedAtDesc(
                            tenantId, assignment.getId(), PageRequest.of(0, 100)).stream()
                    .filter(feedback -> feedback.getStatus() == LearningAssignmentFeedbackStatus.OPEN
                            || feedback.getStatus() == LearningAssignmentFeedbackStatus.ACKNOWLEDGED)
                    .filter(feedback -> feedback.getAction() == LearningAssignmentFeedbackAction.REQUEST_EVIDENCE
                            || feedback.getAction() == LearningAssignmentFeedbackAction.RECOMMEND_RETRY)
                    .count();
            totals.openInterventions += openInterventions;
            accumulator.accept(assignment, progress, openInterventions);
        }

        EducationCourseProgressView result = new EducationCourseProgressView(
                EducationCourseView.from(course,
                        enrollments.stream().filter(item -> item.getStatus() == EducationEnrollmentStatus.ACTIVE).count()),
                totals.assignmentTotal, totals.assigned, totals.accepted, totals.awaitingEvidence,
                totals.retryRequired, totals.overdue, totals.completed, totals.cancelled,
                totals.reviewPending, totals.reviewVerified, totals.revisionRequired,
                totals.openInterventions, ratio(totals.completed, totals.assignmentTotal),
                ratio(totals.reviewVerified,
                        totals.reviewPending + totals.reviewVerified + totals.revisionRequired),
                byLearner.values().stream().map(LearnerAccumulator::view)
                        .sorted(Comparator.comparing(EducationCourseLearnerProgressView::learnerUserId))
                        .toList(),
                truncated);
        return result;
    }

    private double ratio(long numerator, long denominator) {
        return denominator <= 0 ? 0.0 : (double) numerator / denominator;
    }

    private static final class Totals {
        long assignmentTotal;
        long assigned;
        long accepted;
        long awaitingEvidence;
        long retryRequired;
        long overdue;
        long completed;
        long cancelled;
        long reviewPending;
        long reviewVerified;
        long revisionRequired;
        long openInterventions;

        void accept(LearningAssignment assignment) {
            assignmentTotal++;
            switch (assignment.getStatus()) {
                case ASSIGNED -> assigned++;
                case ACCEPTED -> accepted++;
                case AWAITING_EVIDENCE -> awaitingEvidence++;
                case RETRY_REQUIRED -> retryRequired++;
                case OVERDUE -> overdue++;
                case COMPLETED -> completed++;
                case CANCELLED -> cancelled++;
            }
            switch (assignment.getReviewStatus()) {
                case PENDING -> reviewPending++;
                case VERIFIED -> reviewVerified++;
                case REVISION_REQUIRED -> revisionRequired++;
                case NOT_REQUIRED -> { }
            }
        }
    }

    private static final class LearnerAccumulator {
        private final String learnerUserId;
        private long assignmentTotal;
        private long assigned;
        private long accepted;
        private long awaitingEvidence;
        private long retryRequired;
        private long overdue;
        private long completed;
        private long cancelled;
        private long reviewPending;
        private long reviewVerified;
        private long revisionRequired;
        private long openInterventionCount;
        private double masteryProgress;
        private double masteryGain;
        private Instant lastActivityAt;

        private LearnerAccumulator(String learnerUserId) {
            this.learnerUserId = learnerUserId;
        }

        void accept(LearningAssignment assignment, LearningAssignmentProgressView progress,
                    long openInterventions) {
            assignmentTotal++;
            switch (assignment.getStatus()) {
                case ASSIGNED -> assigned++;
                case ACCEPTED -> accepted++;
                case AWAITING_EVIDENCE -> awaitingEvidence++;
                case RETRY_REQUIRED -> retryRequired++;
                case OVERDUE -> overdue++;
                case COMPLETED -> completed++;
                case CANCELLED -> cancelled++;
            }
            switch (assignment.getReviewStatus()) {
                case PENDING -> reviewPending++;
                case VERIFIED -> reviewVerified++;
                case REVISION_REQUIRED -> revisionRequired++;
                case NOT_REQUIRED -> { }
            }
            openInterventionCount += openInterventions;
            masteryProgress += progress.masteryProgress();
            masteryGain += progress.masteryGain();
            lastActivityAt = latest(lastActivityAt, assignment.getUpdatedAt(), progress.lastAssessmentAt(),
                    progress.lastFeedbackAt());
        }

        EducationCourseLearnerProgressView view() {
            return new EducationCourseLearnerProgressView(learnerUserId, assignmentTotal, assigned, accepted,
                    awaitingEvidence, retryRequired, overdue, completed, cancelled, reviewPending,
                    reviewVerified, revisionRequired, openInterventionCount,
                    assignmentTotal == 0 ? 0.0 : masteryProgress / assignmentTotal,
                    assignmentTotal == 0 ? 0.0 : masteryGain / assignmentTotal, lastActivityAt);
        }

        private Instant latest(Instant... values) {
            Instant latest = null;
            for (Instant value : values) {
                if (value != null && (latest == null || value.isAfter(latest))) latest = value;
            }
            return latest;
        }
    }
}
