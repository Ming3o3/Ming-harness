package org.mingharness.education;

import org.junit.jupiter.api.Test;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.education.api.LearningAssignmentAcceptView;
import org.mingharness.education.api.LearningAssignmentRequest;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LearningAssignmentServiceTests {

    @Test
    void shouldAcceptAssignmentAndCreateLearnerProfileAndGoal() {
        LearningAssignmentRepository assignments = mock(LearningAssignmentRepository.class);
        LearnerProfileRepository profiles = mock(LearnerProfileRepository.class);
        LearningGoalRepository goals = mock(LearningGoalRepository.class);
        LearnerMasteryRepository mastery = mock(LearnerMasteryRepository.class);
        LearningAssignment assignment = assignment();
        when(assignments.findByTenantIdAndId("tenant-a", assignment.getId()))
                .thenReturn(Optional.of(assignment));
        when(profiles.findByTenantIdAndUserIdAndSubjectAndGradeLevelAndCurriculumVersion(
                "tenant-a", "student-1", "数学", "高中一年级", "人教A版"))
                .thenReturn(Optional.empty());
        when(profiles.save(any(LearnerProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mastery.findByTenantIdAndLearnerProfileIdAndConceptKey(
                eq("tenant-a"), any(), eq("函数定义域"))).thenReturn(Optional.empty());
        when(goals.save(any(LearningGoal.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(assignments.save(any(LearningAssignment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LearningAssignmentService service = new LearningAssignmentService(
                assignments, profiles, goals, mastery, new SensitiveDataSanitizer());
        LearningAssignmentAcceptView accepted = service.accept("tenant-a", "student-1", assignment.getId());

        assertEquals(LearningAssignmentStatus.ACCEPTED.name(), accepted.assignment().status());
        assertEquals(accepted.learnerProfileId(), accepted.assignment().learnerProfileId());
        assertEquals(accepted.learningGoalId(), accepted.assignment().learningGoalId());
    }

    @Test
    void shouldTreatAwaitingEvidenceAsAnAlreadyAcceptedAssignment() {
        LearningAssignmentRepository assignments = mock(LearningAssignmentRepository.class);
        LearningAssignment assignment = assignment();
        assignment.accept("profile-1", "goal-1", Instant.now());
        assignment.awaitEvidence(Instant.now());
        when(assignments.findByTenantIdAndId("tenant-a", assignment.getId()))
                .thenReturn(Optional.of(assignment));

        LearningAssignmentAcceptView accepted = new LearningAssignmentService(
                assignments, mock(LearnerProfileRepository.class), mock(LearningGoalRepository.class),
                mock(LearnerMasteryRepository.class), new SensitiveDataSanitizer())
                .accept("tenant-a", "student-1", assignment.getId());

        assertEquals(LearningAssignmentStatus.AWAITING_EVIDENCE.name(), accepted.assignment().status());
        assertEquals("profile-1", accepted.learnerProfileId());
        assertEquals("goal-1", accepted.learningGoalId());
    }

    @Test
    void shouldExpireDueAssignmentsAndPersistTheLifecycleTransition() {
        LearningAssignmentRepository assignments = mock(LearningAssignmentRepository.class);
        LearningAssignment overdue = new LearningAssignment("tenant-a", "teacher-1", "student-1",
                "函数作业", "完成练习", "数学", "高中一年级", "人教A版", "函数定义域", 0.8,
                Instant.parse("2026-08-01T00:00:00Z"));
        when(assignments.findByStatusInAndDueAtLessThanEqualOrderByDueAtAsc(
                any(), eq(Instant.parse("2026-08-01T00:00:00Z")), any()))
                .thenReturn(java.util.List.of(overdue));
        when(assignments.save(any(LearningAssignment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        LearningAssignmentService service = new LearningAssignmentService(
                assignments, mock(LearnerProfileRepository.class), mock(LearningGoalRepository.class),
                mock(LearnerMasteryRepository.class), new SensitiveDataSanitizer());

        assertEquals(1, service.expireOverdue(Instant.parse("2026-08-01T00:00:00Z"), 100));
        assertEquals(LearningAssignmentStatus.OVERDUE, overdue.getStatus());
    }

    @Test
    void shouldAllowOnlyTheTeacherToCancelAnIncompleteAssignment() {
        LearningAssignmentRepository assignments = mock(LearningAssignmentRepository.class);
        LearningAssignment assignment = assignment();
        when(assignments.findByTenantIdAndId("tenant-a", assignment.getId()))
                .thenReturn(Optional.of(assignment));
        when(assignments.save(any(LearningAssignment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        LearningAssignmentService service = new LearningAssignmentService(
                assignments, mock(LearnerProfileRepository.class), mock(LearningGoalRepository.class),
                mock(LearnerMasteryRepository.class), new SensitiveDataSanitizer());

        org.mingharness.common.BusinessException forbidden = org.junit.jupiter.api.Assertions.assertThrows(
                org.mingharness.common.BusinessException.class,
                () -> service.cancel("tenant-a", "student-1", assignment.getId()));
        assertEquals("LEARNING_ASSIGNMENT_TEACHER_ONLY", forbidden.getCode());

        LearningAssignment cancelled = service.cancel("tenant-a", "teacher-1", assignment.getId());
        assertEquals(LearningAssignmentStatus.CANCELLED, cancelled.getStatus());
    }

    private LearningAssignment assignment() {
        return new LearningAssignment("tenant-a", "teacher-1", "student-1", "函数作业",
                "完成函数定义域练习", "数学", "高中一年级", "人教A版", "函数定义域",
                0.8, Instant.now().plusSeconds(3600));
    }
}
