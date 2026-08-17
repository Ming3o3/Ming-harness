package org.mingharness.education;

import org.junit.jupiter.api.Test;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.education.api.LearningAssignmentTestCaseRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LearningAssignmentTestCaseServiceTests {

    @Test
    void teacherCanCreateCaseBeforeLearnerAcceptsAssignment() {
        LearningAssignmentService assignments = mock(LearningAssignmentService.class);
        LearningAssignmentTestCaseRepository repository = mock(LearningAssignmentTestCaseRepository.class);
        LearningAssignment assignment = assignment();
        when(assignments.getForParticipant("tenant-a", "teacher-1", assignment.getId()))
                .thenReturn(assignment);
        when(repository.findByTenantIdAndLearningAssignmentIdAndCaseKey(
                "tenant-a", assignment.getId(), "normal")).thenReturn(Optional.empty());
        when(repository.save(any(LearningAssignmentTestCase.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = new LearningAssignmentTestCaseService(assignments, repository,
                new SensitiveDataSanitizer()).create("tenant-a", "teacher-1", assignment.getId(),
                new LearningAssignmentTestCaseRequest("normal", "普通样例", "2 3\n", "5\n",
                        false, 2.0, 1, "循环"));

        assertEquals("normal", result.caseKey());
        assertEquals("循环", result.conceptKey());
        assertEquals("5\n", result.expectedOutput());
        assertEquals(2.0, result.weight());
    }

    @Test
    void refusesMutationAfterAssignmentHasBeenAccepted() {
        LearningAssignmentService assignments = mock(LearningAssignmentService.class);
        LearningAssignmentTestCaseRepository repository = mock(LearningAssignmentTestCaseRepository.class);
        LearningAssignment assignment = assignment();
        assignment.accept("profile-1", "goal-1", Instant.now());
        when(assignments.getForParticipant("tenant-a", "teacher-1", assignment.getId()))
                .thenReturn(assignment);

        var exception = assertThrows(org.mingharness.common.BusinessException.class, () ->
                new LearningAssignmentTestCaseService(assignments, repository,
                        new SensitiveDataSanitizer()).create("tenant-a", "teacher-1", assignment.getId(),
                        new LearningAssignmentTestCaseRequest("normal", null, "", "5", false,
                                1.0, 0)));

        assertEquals("ASSIGNMENT_TEST_CASES_FROZEN", exception.getCode());
    }

    @Test
    void learnerListDoesNotExposeHiddenCases() {
        LearningAssignmentService assignments = mock(LearningAssignmentService.class);
        LearningAssignmentTestCaseRepository repository = mock(LearningAssignmentTestCaseRepository.class);
        LearningAssignment assignment = assignment();
        when(assignments.getForParticipant("tenant-a", "student-1", assignment.getId()))
                .thenReturn(assignment);
        when(repository.findByTenantIdAndLearningAssignmentIdAndEnabledTrueOrderBySequenceAscCreatedAtAsc(
                "tenant-a", assignment.getId())).thenReturn(List.of(
                new LearningAssignmentTestCase("tenant-a", assignment.getId(), "visible", null,
                        "1\n", "1\n", false, 1.0, 0),
                new LearningAssignmentTestCase("tenant-a", assignment.getId(), "hidden", null,
                        "2\n", "2\n", true, 1.0, 1)));

        var result = new LearningAssignmentTestCaseService(assignments, repository,
                new SensitiveDataSanitizer()).list("tenant-a", "student-1", assignment.getId(), false);

        assertEquals(1, result.size());
        assertEquals("visible", result.get(0).caseKey());
        assertEquals(null, result.get(0).expectedOutput());
    }

    private LearningAssignment assignment() {
        return new LearningAssignment("tenant-a", "teacher-1", "student-1", "代码作业",
                "完成函数", "编程", "大一", "课程版", "函数", 0.8,
                Instant.now().plusSeconds(3600), null, null, null, "python");
    }
}
