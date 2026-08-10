package org.mingharness.education;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LearningAssignmentEvidenceServiceTests {

    @Test
    void shouldReturnEvidenceForAssignmentLearnerGoalToBothParticipants() {
        LearningAssignmentService assignments = mock(LearningAssignmentService.class);
        AssessmentAttemptRepository attempts = mock(AssessmentAttemptRepository.class);
        LearningAssignment assignment = new LearningAssignment("tenant-a", "teacher-1", "student-1",
                "函数作业", "完成练习", "数学", "高中一年级", "人教A版", "函数", 0.8,
                Instant.now());
        assignment.accept("profile-1", "goal-1", Instant.now());
        AssessmentAttempt attempt = new AssessmentAttempt("tenant-a", "student-1", "run-1", "step-1",
                "goal-1", "profile-1", "函数", true, 0.8, 0.4, 0.8,
                "MODEL_TOOL", "作答证据", "正确");
        when(assignments.getForParticipant("tenant-a", "teacher-1", assignment.getId()))
                .thenReturn(assignment);
        when(attempts.findByTenantIdAndUserIdAndLearningGoalIdOrderByCreatedAtAsc(
                "tenant-a", "student-1", "goal-1")).thenReturn(List.of(attempt));

        LearningAssignmentEvidenceService service = new LearningAssignmentEvidenceService(assignments, attempts);

        assertEquals(1, service.list("tenant-a", "teacher-1", assignment.getId()).size());
        assertEquals("作答证据", service.list("tenant-a", "teacher-1", assignment.getId()).get(0).evidenceText());
    }

    @Test
    void shouldReturnEmptyEvidenceBeforeAssignmentIsAccepted() {
        LearningAssignmentService assignments = mock(LearningAssignmentService.class);
        AssessmentAttemptRepository attempts = mock(AssessmentAttemptRepository.class);
        LearningAssignment assignment = new LearningAssignment("tenant-a", "teacher-1", "student-1",
                "函数作业", "完成练习", "数学", "高中一年级", "人教A版", "函数", 0.8,
                Instant.now());
        when(assignments.getForParticipant("tenant-a", "teacher-1", assignment.getId()))
                .thenReturn(assignment);

        LearningAssignmentEvidenceService service = new LearningAssignmentEvidenceService(assignments, attempts);

        assertEquals(List.of(), service.list("tenant-a", "teacher-1", assignment.getId()));
    }
}
