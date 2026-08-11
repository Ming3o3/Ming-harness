package org.mingharness.education;

import org.junit.jupiter.api.Test;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.education.api.LearningAssignmentEvaluationRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LearningAssignmentIndependentEvaluationServiceTests {

    @Test
    void shouldSaveIndependentEvaluationWithoutChangingAssignmentStatus() {
        LearningAssignmentRepository assignments = mock(LearningAssignmentRepository.class);
        LearningAssignmentEvaluationRepository evaluations = mock(LearningAssignmentEvaluationRepository.class);
        LearningAssignment assignment = completedAssignment();
        when(assignments.findByTenantIdAndId("tenant-a", assignment.getId()))
                .thenReturn(Optional.of(assignment));
        when(evaluations.existsByTenantIdAndLearningAssignmentIdAndEvaluatorUserIdAndDecision(
                "tenant-a", assignment.getId(), "reviewer-2",
                LearningAssignmentEvaluationDecision.INDEPENDENT)).thenReturn(false);
        when(evaluations.save(any(LearningAssignmentEvaluation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var service = new LearningAssignmentIndependentEvaluationService(
                assignments, evaluations, new SensitiveDataSanitizer());
        var result = service.evaluate("tenant-a", "reviewer-2", assignment.getId(),
                new LearningAssignmentEvaluationRequest(5, 4, 3, "独立复核"));

        assertEquals("INDEPENDENT", result.decision());
        assertEquals(LearningAssignmentStatus.COMPLETED, assignment.getStatus());
        assertEquals(LearningAssignmentReviewStatus.PENDING, assignment.getReviewStatus());
    }

    @Test
    void shouldRejectTeacherAsIndependentEvaluator() {
        LearningAssignmentRepository assignments = mock(LearningAssignmentRepository.class);
        LearningAssignmentEvaluationRepository evaluations = mock(LearningAssignmentEvaluationRepository.class);
        LearningAssignment assignment = completedAssignment();
        when(assignments.findByTenantIdAndId("tenant-a", assignment.getId()))
                .thenReturn(Optional.of(assignment));

        var service = new LearningAssignmentIndependentEvaluationService(
                assignments, evaluations, new SensitiveDataSanitizer());
        var exception = assertThrows(org.mingharness.common.BusinessException.class, () ->
                service.evaluate("tenant-a", "teacher-1", assignment.getId(),
                        new LearningAssignmentEvaluationRequest(5, 4, 3, null)));

        assertEquals("LEARNING_ASSIGNMENT_INDEPENDENT_EVALUATOR_REQUIRED", exception.getCode());
    }

    @Test
    void shouldClassifyConsensusByThreeScoreDifferences() {
        LearningAssignmentRepository assignments = mock(LearningAssignmentRepository.class);
        LearningAssignmentEvaluationRepository evaluations = mock(LearningAssignmentEvaluationRepository.class);
        LearningAssignment assignment = completedAssignment();
        LearningAssignmentEvaluation teacher = new LearningAssignmentEvaluation(
                "tenant-a", assignment.getId(), null, "student-1", "teacher-1",
                LearningAssignmentEvaluationDecision.VERIFY, 5, 4, 3, "教师确认", Instant.now());
        LearningAssignmentEvaluation independent = new LearningAssignmentEvaluation(
                "tenant-a", assignment.getId(), null, "student-1", "reviewer-2",
                LearningAssignmentEvaluationDecision.INDEPENDENT, 4, 5, 3, "独立复核", Instant.now());
        when(assignments.findByTenantIdAndId("tenant-a", assignment.getId()))
                .thenReturn(Optional.of(assignment));
        when(evaluations.findByTenantIdAndLearningAssignmentIdOrderByCreatedAtDesc(
                "tenant-a", assignment.getId())).thenReturn(List.of(independent, teacher));

        var service = new LearningAssignmentIndependentEvaluationService(
                assignments, evaluations, new SensitiveDataSanitizer());
        var result = service.consensus("tenant-a", "teacher-1", assignment.getId());

        assertEquals("AGREED", result.status());
        assertEquals(1.0, result.contentScoreDifference());
        assertEquals(1.0, result.evidenceScoreDifference());
        assertEquals(0.0, result.transferScoreDifference());
    }

    private LearningAssignment completedAssignment() {
        LearningAssignment assignment = new LearningAssignment("tenant-a", "teacher-1", "student-1",
                "函数作业", "完成练习", "数学", "高中一年级", "人教A版", "函数", 0.8,
                Instant.now().plusSeconds(3600), "course-1", "batch-1");
        assignment.accept("profile-1", "goal-1", Instant.now());
        assignment.complete(Instant.now());
        return assignment;
    }
}
