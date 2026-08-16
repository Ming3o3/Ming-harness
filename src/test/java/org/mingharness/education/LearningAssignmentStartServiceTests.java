package org.mingharness.education;

import org.junit.jupiter.api.Test;
import org.mingharness.common.BusinessException;
import org.mingharness.conversation.api.ConversationDetail;
import org.mingharness.conversation.api.ConversationMessageView;
import org.mingharness.conversation.api.ConversationSummary;
import org.mingharness.education.api.ExecuteLearningActionRequest;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LearningAssignmentStartServiceTests {

    @Test
    void shouldAcceptAssignmentAndStartTheFirstEducationRun() {
        LearningAssignmentService assignments = mock(LearningAssignmentService.class);
        EducationActionService actions = mock(EducationActionService.class);
        LearningAssignment assignment = assignment();
        LearningAssignment accepted = assignment();
        accepted.accept("profile-1", "goal-1", Instant.now());
        ConversationDetail conversation = detail("conversation-1");
        when(assignments.getForParticipant("tenant-a", "student-1", assignment.getId()))
                .thenReturn(assignment, accepted);
        when(actions.execute(eq("tenant-a"), eq("student-1"), eq("goal-1"), any(),
                eq("education.read,education.write"), eq("assignment-start-1")))
                .thenReturn(conversation);

        LearningAssignmentStartService service = new LearningAssignmentStartService(assignments, actions);
        var result = service.start("tenant-a", "student-1", assignment.getId(),
                new ExecuteLearningActionRequest(null, null, 4),
                "education.read,education.write", "assignment-start-1");

        assertEquals("ACCEPTED", result.assignment().status());
        assertEquals("goal-1", result.learningGoalId());
        assertEquals("conversation-1", result.conversation().conversation().id());
        verify(assignments).accept("tenant-a", "student-1", assignment.getId());
        var requestCaptor = org.mockito.ArgumentCaptor.forClass(ExecuteLearningActionRequest.class);
        verify(actions).execute(eq("tenant-a"), eq("student-1"), eq("goal-1"), requestCaptor.capture(),
                eq("education.read,education.write"), eq("assignment-start-1"));
        assertEquals(accepted.getId(), requestCaptor.getValue().learningAssignmentId());
    }

    @Test
    void shouldPropagateFrozenProgrammingLanguageToAssignmentRun() {
        LearningAssignmentService assignments = mock(LearningAssignmentService.class);
        EducationActionService actions = mock(EducationActionService.class);
        LearningAssignment assignment = new LearningAssignment("tenant-a", "teacher-1", "student-1",
                "Python 作业", "完成练习", "编程", "大一", "课程版", "函数",
                0.8, Instant.now().plusSeconds(3600), null, null, null, "python");
        assignment.accept("profile-1", "goal-1", Instant.now());
        when(assignments.getForParticipant("tenant-a", "student-1", assignment.getId()))
                .thenReturn(assignment);
        when(actions.execute(eq("tenant-a"), eq("student-1"), eq("goal-1"), any(),
                eq("education.read,education.write"), eq("assignment-start-language")))
                .thenReturn(detail("conversation-language"));

        new LearningAssignmentStartService(assignments, actions).start(
                "tenant-a", "student-1", assignment.getId(), null,
                "education.read,education.write", "assignment-start-language");

        var requestCaptor = org.mockito.ArgumentCaptor.forClass(ExecuteLearningActionRequest.class);
        verify(actions).execute(eq("tenant-a"), eq("student-1"), eq("goal-1"), requestCaptor.capture(),
                eq("education.read,education.write"), eq("assignment-start-language"));
        assertEquals("PYTHON", requestCaptor.getValue().programmingLanguage());
    }

    @Test
    void shouldRejectClientLanguageOverrideForAssignmentStart() {
        LearningAssignmentService assignments = mock(LearningAssignmentService.class);
        EducationActionService actions = mock(EducationActionService.class);
        LearningAssignment assignment = new LearningAssignment("tenant-a", "teacher-1", "student-1",
                "Python 作业", "完成练习", "编程", "大一", "课程版", "函数",
                0.8, Instant.now().plusSeconds(3600), null, null, null, "python");
        assignment.accept("profile-1", "goal-1", Instant.now());
        when(assignments.getForParticipant("tenant-a", "student-1", assignment.getId()))
                .thenReturn(assignment);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                new LearningAssignmentStartService(assignments, actions).start(
                        "tenant-a", "student-1", assignment.getId(),
                        new ExecuteLearningActionRequest(null, null, 4, null, null, "java"),
                        "education.read,education.write", "assignment-start-language-conflict"));

        assertEquals("LEARNING_ASSIGNMENT_LANGUAGE_MISMATCH", exception.getCode());
        verifyNoInteractions(actions);
    }

    @Test
    void shouldRejectTeacherStartingAnotherLearnersAssignment() {
        LearningAssignmentService assignments = mock(LearningAssignmentService.class);
        EducationActionService actions = mock(EducationActionService.class);
        LearningAssignment assignment = assignment();
        when(assignments.getForParticipant("tenant-a", "teacher-1", assignment.getId()))
                .thenReturn(assignment);

        LearningAssignmentStartService service = new LearningAssignmentStartService(assignments, actions);
        BusinessException exception = assertThrows(BusinessException.class, () -> service.start(
                "tenant-a", "teacher-1", assignment.getId(), null, "*", "key"));

        assertEquals("LEARNING_ASSIGNMENT_LEARNER_ONLY", exception.getCode());
    }

    @Test
    void shouldLeaveTeacherInterventionOpenUntilEvidenceIsRecorded() {
        LearningAssignmentService assignments = mock(LearningAssignmentService.class);
        EducationActionService actions = mock(EducationActionService.class);
        LearningAssignmentFeedbackRepository feedbackRepository = mock(LearningAssignmentFeedbackRepository.class);
        LearningAssignmentFeedbackService feedbackService = mock(LearningAssignmentFeedbackService.class);
        LearningAssignment assignment = assignment();
        assignment.accept("profile-1", "goal-1", Instant.now());
        when(assignments.getForParticipant("tenant-a", "student-1", assignment.getId()))
                .thenReturn(assignment);
        when(actions.execute(eq("tenant-a"), eq("student-1"), eq("goal-1"), any(),
                eq("education.read,education.write"), eq("assignment-start-2")))
                .thenReturn(detail("conversation-2"));

        LearningAssignmentStartService service = new LearningAssignmentStartService(
                assignments, actions, feedbackRepository, feedbackService);
        service.start("tenant-a", "student-1", assignment.getId(),
                new ExecuteLearningActionRequest(null, null, 4),
                "education.read,education.write", "assignment-start-2");

        verifyNoInteractions(feedbackService);
    }

    private LearningAssignment assignment() {
        return new LearningAssignment("tenant-a", "teacher-1", "student-1", "函数作业",
                "完成函数定义域练习", "数学", "高中一年级", "人教A版", "函数定义域",
                0.8, Instant.now().plusSeconds(3600));
    }

    private ConversationDetail detail(String conversationId) {
        ConversationSummary summary = new ConversationSummary(conversationId, "tenant-a", "student-1",
                "函数作业", null, Instant.now(), Instant.now(), 0, null, null);
        return new ConversationDetail(summary, List.<ConversationMessageView>of());
    }
}
