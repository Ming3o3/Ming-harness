package org.mingharness.education;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LearningAssignmentTests {

    @Test
    void shouldMoveFromAssignedToAcceptedAndCompleted() {
        LearningAssignment assignment = assignment();

        assignment.accept("profile-1", "goal-1", Instant.now());
        assertEquals(LearningAssignmentStatus.ACCEPTED, assignment.getStatus());
        assertEquals("goal-1", assignment.getLearningGoalId());

        assignment.complete(Instant.now());
        assertEquals(LearningAssignmentStatus.COMPLETED, assignment.getStatus());
        assertEquals("profile-1", assignment.getLearnerProfileId());
    }

    @Test
    void shouldAllowAnAcceptedAssignmentToBecomeOverdueAndStillCompleteWithEvidence() {
        Instant dueAt = Instant.parse("2026-08-01T00:00:00Z");
        LearningAssignment assignment = new LearningAssignment("tenant-a", "teacher-1", "student-1",
                "函数作业", "完成函数定义域练习", "数学", "高中一年级", "人教A版", "函数定义域",
                0.8, dueAt);
        assignment.accept("profile-1", "goal-1", Instant.parse("2026-07-31T00:00:00Z"));

        assertEquals(true, assignment.isOverdue(Instant.parse("2026-08-01T00:00:00Z")));
        assignment.markOverdue(Instant.parse("2026-08-01T00:00:00Z"));
        assertEquals(LearningAssignmentStatus.OVERDUE, assignment.getStatus());

        assignment.complete(Instant.parse("2026-08-02T00:00:00Z"));
        assertEquals(LearningAssignmentStatus.COMPLETED, assignment.getStatus());
    }

    private LearningAssignment assignment() {
        return new LearningAssignment("tenant-a", "teacher-1", "student-1", "函数作业",
                "完成函数定义域练习", "数学", "高中一年级", "人教A版", "函数定义域",
                0.8, Instant.now().plusSeconds(3600));
    }
}
