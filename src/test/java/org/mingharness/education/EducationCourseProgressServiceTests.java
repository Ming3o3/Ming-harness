package org.mingharness.education;

import org.junit.jupiter.api.Test;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.education.api.EducationCourseLearnerProgressView;
import org.mingharness.education.api.LearningAssignmentProgressView;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EducationCourseProgressServiceTests {

    @Test
    void shouldAggregateCourseAssignmentsAndKeepLearnersWithoutAssignmentsVisible() {
        EducationCourseService courses = mock(EducationCourseService.class);
        EducationEnrollmentRepository enrollments = mock(EducationEnrollmentRepository.class);
        LearningAssignmentRepository assignments = mock(LearningAssignmentRepository.class);
        LearningAssignmentProgressService progress = mock(LearningAssignmentProgressService.class);
        LearningAssignmentFeedbackRepository feedbacks = mock(LearningAssignmentFeedbackRepository.class);
        EducationCourse course = new EducationCourse("tenant-a", "teacher-1", "math-g1", "高一数学",
                "数学", "高中一年级", "人教A版");
        LearningAssignment completed = new LearningAssignment("tenant-a", "teacher-1", "student-1",
                "函数作业", "完成练习", "数学", "高中一年级", "人教A版", "函数定义域", 0.8,
                Instant.now().plusSeconds(3600), course.getId(), "batch-1");
        completed.accept("profile-1", "goal-1", Instant.now());
        completed.complete(Instant.now());
        LearningAssignment assigned = new LearningAssignment("tenant-a", "teacher-1", "student-2",
                "函数作业", "完成练习", "数学", "高中一年级", "人教A版", "函数定义域", 0.8,
                Instant.now().plusSeconds(3600), course.getId(), "batch-1");
        when(courses.requireOwnerCourse("tenant-a", "teacher-1", course.getId())).thenReturn(course);
        when(enrollments.findByTenantIdAndCourseIdOrderByEnrolledAtAsc(
                "tenant-a", course.getId())).thenReturn(List.of(
                new EducationEnrollment("tenant-a", course.getId(), "student-1", Instant.now()),
                new EducationEnrollment("tenant-a", course.getId(), "student-2", Instant.now()),
                new EducationEnrollment("tenant-a", course.getId(), "student-3", Instant.now())));
        when(assignments.findByTenantIdAndCourseIdOrderByCreatedAtDesc(
                "tenant-a", course.getId())).thenReturn(List.of(completed, assigned));
        when(progress.get(eq("tenant-a"), eq("teacher-1"), anyString())).thenAnswer(invocation ->
                progress(invocation.getArgument(2)));
        when(feedbacks.findByTenantIdAndLearningAssignmentIdOrderByCreatedAtDesc(
                anyString(), anyString(), any())).thenReturn(List.of());

        var result = new EducationCourseProgressService(courses, enrollments, assignments, progress, feedbacks)
                .get("tenant-a", "teacher-1", course.getId(), 500);

        assertEquals(2, result.assignmentTotal());
        assertEquals(1, result.completed());
        assertEquals(1, result.assigned());
        assertEquals(1, result.reviewPending());
        assertEquals(0.5, result.assignmentCompletionRate());
        assertEquals(false, result.readyToComplete());
        assertEquals(2, result.completionBlockerCount());
        assertEquals(0, result.submissionBlockerCount());
        assertEquals(3, result.learners().size());
        EducationCourseLearnerProgressView emptyLearner = result.learners().stream()
                .filter(item -> item.learnerUserId().equals("student-3")).findFirst().orElseThrow();
        assertEquals(0, emptyLearner.assignmentTotal());
    }

    private LearningAssignmentProgressView progress(String assignmentId) {
        return new LearningAssignmentProgressView(assignmentId, "函数作业", "teacher-1", "student-1",
                "COMPLETED", Instant.now().plusSeconds(3600), "goal-1", 0.2, 0.7, 0.8, 0.83,
                2, 1, Instant.now(), 0, 0, 0, 0, 0, 0.5, 1, 1, 1.0,
                0, 0, 0.0, 0, 0, Instant.now());
    }
}
