package org.mingharness.education;

import org.junit.jupiter.api.Test;
import org.mingharness.common.BusinessException;
import org.mingharness.education.api.EducationCourseView;
import org.mingharness.education.api.LearningAssignmentProgressView;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EducationCourseResultServiceTests {

    @Test
    void shouldCaptureImmutableCourseAndLearnerResultSnapshot() {
        EducationCourseResultRepository results = mock(EducationCourseResultRepository.class);
        EducationCourseLearnerResultRepository learnerResults = mock(EducationCourseLearnerResultRepository.class);
        EducationCourseService courses = mock(EducationCourseService.class);
        LearningAssignmentRepository assignments = mock(LearningAssignmentRepository.class);
        EducationEnrollmentRepository enrollments = mock(EducationEnrollmentRepository.class);
        LearningAssignmentSubmissionRepository submissions = mock(LearningAssignmentSubmissionRepository.class);
        LearningAssignmentProgressService progress = mock(LearningAssignmentProgressService.class);
        EducationCourse course = new EducationCourse("tenant-a", "teacher-1", "math-g1", "高一数学",
                "数学", "高中一年级", "人教A版");
        LearningAssignment assignment = new LearningAssignment("tenant-a", "teacher-1", "student-1",
                "函数作业", "完成练习", "数学", "高中一年级", "人教A版", "函数定义域", 0.8,
                Instant.now().plusSeconds(3600), course.getId(), "batch-1");
        assignment.accept("profile-1", "goal-1", Instant.now());
        assignment.complete(Instant.now());
        assignment.verifyByTeacher("teacher-1", "已核验", Instant.now());
        when(results.findByTenantIdAndCourseId("tenant-a", course.getId())).thenReturn(Optional.empty());
        when(assignments.findByTenantIdAndCourseIdOrderByCreatedAtDesc(
                "tenant-a", course.getId())).thenReturn(List.of(assignment));
        when(enrollments.findByTenantIdAndCourseIdAndStatus(
                "tenant-a", course.getId(), EducationEnrollmentStatus.ACTIVE)).thenReturn(List.of(
                new EducationEnrollment("tenant-a", course.getId(), "student-1", Instant.now())));
        when(submissions.existsByTenantIdAndLearningAssignmentId("tenant-a", assignment.getId()))
                .thenReturn(true);
        when(progress.get("tenant-a", "teacher-1", assignment.getId())).thenReturn(progress(assignment));
        when(results.save(any(EducationCourseResult.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(learnerResults.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(learnerResults.findByTenantIdAndCourseResultIdOrderByLearnerUserIdAsc(
                eq("tenant-a"), anyString())).thenReturn(List.of(new EducationCourseLearnerResult(
                "tenant-a", "result-1", course.getId(), "student-1", 1, 1, 1, 1,
                1.0, 0.2, Instant.now())));
        when(enrollments.countByTenantIdAndCourseIdAndStatus(
                "tenant-a", course.getId(), EducationEnrollmentStatus.ACTIVE)).thenReturn(1L);

        var result = new EducationCourseResultService(results, learnerResults, courses, assignments,
                enrollments, submissions, progress).capture(
                "tenant-a", "teacher-1", course, Instant.now());

        assertEquals(course.getId(), result.courseId());
        assertEquals(1, result.activeLearnerTotal());
        assertEquals(1, result.learnersWithAssignments());
        assertEquals(1, result.effectiveAssignmentTotal());
        assertEquals(1, result.assignmentCompleted());
        assertEquals(1, result.assignmentVerified());
        assertEquals(1, result.submissionCovered());
        assertEquals(1, result.learners().size());
        assertEquals("student-1", result.learners().get(0).learnerUserId());
    }

    @Test
    void shouldHideOtherLearnersFromLearnerResultQuery() {
        EducationCourseResultRepository results = mock(EducationCourseResultRepository.class);
        EducationCourseLearnerResultRepository learnerResults = mock(EducationCourseLearnerResultRepository.class);
        EducationCourseService courses = mock(EducationCourseService.class);
        LearningAssignmentRepository assignments = mock(LearningAssignmentRepository.class);
        EducationEnrollmentRepository enrollments = mock(EducationEnrollmentRepository.class);
        LearningAssignmentSubmissionRepository submissions = mock(LearningAssignmentSubmissionRepository.class);
        LearningAssignmentProgressService progress = mock(LearningAssignmentProgressService.class);
        EducationCourse course = new EducationCourse("tenant-a", "teacher-1", "math-g1", "高一数学",
                "数学", "高中一年级", "人教A版");
        EducationCourseResult result = new EducationCourseResult("tenant-a", course.getId(), 2, 2,
                2, 2, 2, 2, 1.0, 0.3, Instant.now(), "teacher-1");
        when(courses.getForParticipant("tenant-a", "student-1", course.getId()))
                .thenReturn(EducationCourseView.from(course, 2));
        when(results.findByTenantIdAndCourseId("tenant-a", course.getId()))
                .thenReturn(Optional.of(result));
        when(learnerResults.findByTenantIdAndCourseResultIdOrderByLearnerUserIdAsc(
                "tenant-a", result.getId())).thenReturn(List.of(
                new EducationCourseLearnerResult("tenant-a", result.getId(), course.getId(),
                        "student-1", 1, 1, 1, 1, 1.0, 0.3, Instant.now()),
                new EducationCourseLearnerResult("tenant-a", result.getId(), course.getId(),
                        "student-2", 1, 1, 1, 1, 1.0, 0.3, Instant.now())));

        var view = new EducationCourseResultService(results, learnerResults, courses, assignments,
                enrollments, submissions, progress).get("tenant-a", "student-1", course.getId());

        assertEquals(1, view.learners().size());
        assertEquals("student-1", view.learners().get(0).learnerUserId());
    }

    @Test
    void shouldExportTeacherResultAsCsvWithCourseAndLearnerRows() {
        EducationCourseResultRepository results = mock(EducationCourseResultRepository.class);
        EducationCourseLearnerResultRepository learnerResults = mock(EducationCourseLearnerResultRepository.class);
        EducationCourseService courses = mock(EducationCourseService.class);
        LearningAssignmentRepository assignments = mock(LearningAssignmentRepository.class);
        EducationEnrollmentRepository enrollments = mock(EducationEnrollmentRepository.class);
        LearningAssignmentSubmissionRepository submissions = mock(LearningAssignmentSubmissionRepository.class);
        LearningAssignmentProgressService progress = mock(LearningAssignmentProgressService.class);
        EducationCourse course = new EducationCourse("tenant-a", "teacher-1", "math-g1", "=高一数学",
                "数学", "高中一年级", "人教A版");
        EducationCourseResult result = new EducationCourseResult("tenant-a", course.getId(), 1, 1,
                1, 1, 1, 1, 1.0, 0.25, Instant.parse("2026-01-02T03:04:05Z"), "teacher-1");
        EducationCourseLearnerResult learner = new EducationCourseLearnerResult(
                "tenant-a", result.getId(), course.getId(), "student-1", 1, 1, 1, 1,
                1.0, 0.25, Instant.parse("2026-01-01T03:04:05Z"));
        when(courses.getForParticipant("tenant-a", "teacher-1", course.getId()))
                .thenReturn(EducationCourseView.from(course, 1));
        when(results.findByTenantIdAndCourseId("tenant-a", course.getId()))
                .thenReturn(Optional.of(result));
        when(learnerResults.findByTenantIdAndCourseResultIdOrderByLearnerUserIdAsc(
                "tenant-a", result.getId())).thenReturn(List.of(learner));

        var service = new EducationCourseResultService(results, learnerResults, courses, assignments,
                enrollments, submissions, progress);
        String csv = service.exportCsv("tenant-a", "teacher-1", course.getId());

        assertTrue(csv.startsWith("\uFEFF\"record_type\""));
        assertTrue(csv.contains("\"COURSE\""));
        assertTrue(csv.contains("\"LEARNER\""));
        assertTrue(csv.contains("\"student-1\""));
        assertTrue(csv.contains("\"2026-01-02T03:04:05Z\""));
        assertTrue(csv.contains("\"'=高一数学\""));
    }

    @Test
    void shouldRejectLearnerCsvExport() {
        EducationCourseResultRepository results = mock(EducationCourseResultRepository.class);
        EducationCourseLearnerResultRepository learnerResults = mock(EducationCourseLearnerResultRepository.class);
        EducationCourseService courses = mock(EducationCourseService.class);
        LearningAssignmentRepository assignments = mock(LearningAssignmentRepository.class);
        EducationEnrollmentRepository enrollments = mock(EducationEnrollmentRepository.class);
        LearningAssignmentSubmissionRepository submissions = mock(LearningAssignmentSubmissionRepository.class);
        LearningAssignmentProgressService progress = mock(LearningAssignmentProgressService.class);
        EducationCourse course = new EducationCourse("tenant-a", "teacher-1", "math-g1", "高一数学",
                "数学", "高中一年级", "人教A版");
        when(courses.getForParticipant("tenant-a", "student-1", course.getId()))
                .thenReturn(EducationCourseView.from(course, 1));

        var service = new EducationCourseResultService(results, learnerResults, courses, assignments,
                enrollments, submissions, progress);
        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.exportCsv("tenant-a", "student-1", course.getId()));

        assertEquals("EDUCATION_COURSE_OWNER_ONLY", exception.getCode());
    }

    private LearningAssignmentProgressView progress(LearningAssignment assignment) {
        return new LearningAssignmentProgressView(assignment.getId(), assignment.getTitle(),
                assignment.getTeacherUserId(), assignment.getLearnerUserId(), "COMPLETED",
                assignment.getDueAt(), "goal-1", 0.2, 0.8, 0.8, 1.0,
                2, 2, Instant.now(), 0, 0, 0, 0, 0,
                0.6, 1, 1, 1.0, 0, 0, 0.0, 0, 0, Instant.now());
    }
}
