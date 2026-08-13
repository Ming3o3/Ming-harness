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
import java.util.Set;

/** 将课程名单和已有单份作业进度投影为教师可行动的班级结果。 */
@Service
public class EducationCourseProgressService {

    private static final int MAX_ASSIGNMENTS = 500;

    private final EducationCourseService courseService;
    private final EducationEnrollmentRepository enrollmentRepository;
    private final LearningAssignmentRepository assignmentRepository;
    private final LearningAssignmentProgressService assignmentProgressService;
    private final LearningAssignmentFeedbackRepository feedbackRepository;
    private final LearningAssignmentSubmissionRepository submissionRepository;

    public EducationCourseProgressService(EducationCourseService courseService,
                                          EducationEnrollmentRepository enrollmentRepository,
                                          LearningAssignmentRepository assignmentRepository,
                                          LearningAssignmentProgressService assignmentProgressService,
                                          LearningAssignmentFeedbackRepository feedbackRepository) {
        this(courseService, enrollmentRepository, assignmentRepository, assignmentProgressService,
                feedbackRepository, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public EducationCourseProgressService(EducationCourseService courseService,
                                          EducationEnrollmentRepository enrollmentRepository,
                                          LearningAssignmentRepository assignmentRepository,
                                          LearningAssignmentProgressService assignmentProgressService,
                                          LearningAssignmentFeedbackRepository feedbackRepository,
                                          LearningAssignmentSubmissionRepository submissionRepository) {
        this.courseService = courseService;
        this.enrollmentRepository = enrollmentRepository;
        this.assignmentRepository = assignmentRepository;
        this.assignmentProgressService = assignmentProgressService;
        this.feedbackRepository = feedbackRepository;
        this.submissionRepository = submissionRepository;
    }

    @Transactional(readOnly = true)
    public EducationCourseProgressView get(String tenantId, String teacherUserId, String courseId,
                                           int requestedLimit) {
        EducationCourse course = courseService.requireOwnerCourse(tenantId, teacherUserId, courseId);
        return getForCourse(tenantId, teacherUserId, course, requestedLimit);
    }

    /** 管理员治理页只读查看同租户课程进度，沿用教师口径但不获得写权限。 */
    @Transactional(readOnly = true)
    public EducationCourseProgressView getForGovernance(String tenantId, String courseId,
                                                        int requestedLimit) {
        EducationCourse course = courseService.requireGovernanceCourse(tenantId, courseId);
        return getForCourse(tenantId, course.getOwnerUserId(), course, requestedLimit);
    }

    private EducationCourseProgressView getForCourse(String tenantId, String teacherUserId,
                                                     EducationCourse course, int requestedLimit) {
        int limit = Math.max(1, Math.min(MAX_ASSIGNMENTS, requestedLimit <= 0 ? MAX_ASSIGNMENTS : requestedLimit));
        List<EducationEnrollment> enrollments = enrollmentRepository
                .findByTenantIdAndCourseIdOrderByEnrolledAtAsc(tenantId, course.getId());
        List<EducationEnrollment> activeEnrollments = enrollments.stream()
                .filter(item -> item.getStatus() == EducationEnrollmentStatus.ACTIVE)
                .toList();
        Set<String> activeLearnerIds = activeEnrollments.stream()
                .map(EducationEnrollment::getLearnerUserId).collect(java.util.stream.Collectors.toSet());
        // 名单移除不会删除学习证据或作业历史；但被移除学习者不再属于当前课程
        // 运营边界，不能继续阻塞活跃班级的干预队列、结课条件或统计口径。
        List<LearningAssignment> activeRosterAssignments = assignmentRepository
                .findByTenantIdAndCourseIdOrderByCreatedAtDesc(tenantId, course.getId()).stream()
                .filter(item -> activeLearnerIds.contains(item.getLearnerUserId()))
                .toList();
        boolean truncated = activeRosterAssignments.size() > limit;
        List<LearningAssignment> assignments = activeRosterAssignments.stream().limit(limit).toList();
        List<LearningAssignment> effectiveAssignments = activeRosterAssignments.stream()
                .filter(item -> item.getStatus() != LearningAssignmentStatus.CANCELLED)
                .toList();
        Set<String> learnersWithAssignments = effectiveAssignments.stream()
                .map(LearningAssignment::getLearnerUserId)
                .filter(activeLearnerIds::contains)
                .collect(java.util.stream.Collectors.toSet());
        long rosterCoverageBlockers = activeLearnerIds.size() - learnersWithAssignments.size();

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
            // 提交物只在作业已达到完成条件后成为教师的当前阻塞；
            // ASSIGNED/ACCEPTED 作业尚未产出作答，不能提前显示“补提交物”。
            boolean submissionMissing = submissionRepository != null
                    && assignment.getStatus() == LearningAssignmentStatus.COMPLETED
                    && !submissionRepository.existsByTenantIdAndLearningAssignmentId(
                    tenantId, assignment.getId());
            accumulator.accept(assignment, progress, openInterventions, submissionMissing);
        }

        long effectiveAssignmentTotal = effectiveAssignments.size();
        long completionBlockers = activeRosterAssignments.stream()
                .filter(item -> item.getStatus() != LearningAssignmentStatus.CANCELLED)
                .filter(item -> item.getStatus() != LearningAssignmentStatus.COMPLETED
                        || item.getReviewStatus() != LearningAssignmentReviewStatus.VERIFIED)
                .count();
        // 未完成作业还没有合法提交窗口；它们由 completionBlockers 负责提示。
        // 只有已完成作业缺少提交物时，教师才需要进入“补齐提交物”入口。
        long submissionBlockers = submissionRepository == null ? 0 : effectiveAssignments.stream()
                .filter(item -> item.getStatus() == LearningAssignmentStatus.COMPLETED)
                .filter(item -> !submissionRepository.existsByTenantIdAndLearningAssignmentId(
                        tenantId, item.getId()))
                .count();

        EducationCourseProgressView result = new EducationCourseProgressView(
                EducationCourseView.from(course,
                        enrollments.stream().filter(item -> item.getStatus() == EducationEnrollmentStatus.ACTIVE).count()),
                activeLearnerIds.size(), learnersWithAssignments.size(),
                ratio(learnersWithAssignments.size(), activeLearnerIds.size()), rosterCoverageBlockers,
                totals.assignmentTotal, totals.assigned, totals.accepted, totals.awaitingEvidence,
                totals.retryRequired, totals.overdue, totals.completed, totals.cancelled,
                totals.reviewPending, totals.reviewVerified, totals.revisionRequired,
                totals.openInterventions, ratio(totals.completed, totals.assignmentTotal),
                ratio(totals.reviewVerified,
                        totals.reviewPending + totals.reviewVerified + totals.revisionRequired),
                !activeLearnerIds.isEmpty() && effectiveAssignmentTotal > 0
                        && rosterCoverageBlockers == 0 && completionBlockers == 0
                        && submissionBlockers == 0,
                completionBlockers,
                submissionBlockers,
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
        private long submissionMissing;
        private long attentionCount;
        private double masteryProgress;
        private double masteryGain;
        private Instant lastActivityAt;

        private LearnerAccumulator(String learnerUserId) {
            this.learnerUserId = learnerUserId;
        }

        void accept(LearningAssignment assignment, LearningAssignmentProgressView progress,
                    long openInterventions, boolean submissionMissing) {
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
            if (submissionMissing) this.submissionMissing++;
            if (isAttentionAssignment(assignment, openInterventions, submissionMissing)) {
                attentionCount++;
            }
            masteryProgress += progress.masteryProgress();
            masteryGain += progress.masteryGain();
            lastActivityAt = latest(lastActivityAt, assignment.getUpdatedAt(), progress.lastAssessmentAt(),
                    progress.lastFeedbackAt());
        }

        EducationCourseLearnerProgressView view() {
            return new EducationCourseLearnerProgressView(learnerUserId, assignmentTotal, assigned, accepted,
                    awaitingEvidence, retryRequired, overdue, completed, cancelled, reviewPending,
                    reviewVerified, revisionRequired, openInterventionCount, submissionMissing, attentionCount,
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

        private boolean isAttentionAssignment(LearningAssignment assignment, long openInterventions,
                                               boolean submissionMissing) {
            return assignment.getStatus() == LearningAssignmentStatus.ASSIGNED
                    || assignment.getStatus() == LearningAssignmentStatus.AWAITING_EVIDENCE
                    || assignment.getStatus() == LearningAssignmentStatus.RETRY_REQUIRED
                    || assignment.getStatus() == LearningAssignmentStatus.OVERDUE
                    || assignment.getReviewStatus() == LearningAssignmentReviewStatus.PENDING
                    || assignment.getReviewStatus() == LearningAssignmentReviewStatus.REVISION_REQUIRED
                    || openInterventions > 0
                    || submissionMissing;
        }
    }
}
