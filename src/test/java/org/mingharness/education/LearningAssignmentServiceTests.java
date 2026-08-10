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

    private LearningAssignment assignment() {
        return new LearningAssignment("tenant-a", "teacher-1", "student-1", "函数作业",
                "完成函数定义域练习", "数学", "高中一年级", "人教A版", "函数定义域",
                0.8, Instant.now().plusSeconds(3600));
    }
}
