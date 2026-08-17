package org.mingharness.education.api;

import org.mingharness.education.LearningAssignmentTestCase;

import java.time.Instant;

/** 编程行为测试用例投影；学习者视图不会泄露隐藏用例的期望输出。 */
public record LearningAssignmentTestCaseView(
        String id,
        String learningAssignmentId,
        String caseKey,
        String name,
        String input,
        String expectedOutput,
        boolean hidden,
        double weight,
        int sequence,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
    public static LearningAssignmentTestCaseView forTeacher(LearningAssignmentTestCase item) {
        return from(item, item.getExpectedOutput());
    }

    public static LearningAssignmentTestCaseView forLearner(LearningAssignmentTestCase item) {
        return from(item, null);
    }

    private static LearningAssignmentTestCaseView from(LearningAssignmentTestCase item,
                                                       String expectedOutput) {
        return new LearningAssignmentTestCaseView(item.getId(), item.getLearningAssignmentId(),
                item.getCaseKey(), item.getName(), item.getInputData(), expectedOutput,
                item.isHidden(), item.getWeight(), item.getSequence(), item.isEnabled(),
                item.getCreatedAt(), item.getUpdatedAt());
    }
}
