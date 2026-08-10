package org.mingharness.education;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LearningAssignmentCompletionServiceTests {

    @Test
    void shouldCompleteAcceptedAssignmentWhenGoalReachesTarget() {
        LearningAssignmentRepository assignments = mock(LearningAssignmentRepository.class);
        LearningAssignment assignment = new LearningAssignment("tenant-a", "teacher-1", "student-1",
                "函数作业", "完成练习", "数学", "高中一年级", "人教A版", "函数", 0.8,
                Instant.now());
        assignment.accept("profile-1", "goal-1", Instant.now());
        when(assignments.findByTenantIdAndLearningGoalId("tenant-a", "goal-1"))
                .thenReturn(List.of(assignment));
        when(assignments.save(any(LearningAssignment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LearningAssignmentCompletionService service = new LearningAssignmentCompletionService(assignments);
        assertEquals(1, service.completeForGoal("tenant-a", "student-1", "goal-1", Instant.now()));
        assertEquals(LearningAssignmentStatus.COMPLETED, assignment.getStatus());
        verify(assignments).save(assignment);
    }
}
