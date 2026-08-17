package org.mingharness.education;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EducationCodeEvaluationResultTests {

    @Test
    void calculatesBehaviorPassRateWithoutTreatingCompilePassAsBehaviorEvidence() {
        EducationCodeEvaluationResult compileOnly = new EducationCodeEvaluationResult(
                CodeEvaluationStatus.PASSED, "通过", "", 0, 10);
        assertTrue(!compileOnly.hasBehaviorEvidence());
        assertEquals(CodeBehaviorEvaluationStatus.NOT_CONFIGURED, compileOnly.behaviorStatus());

        EducationCodeEvaluationResult behavior = new EducationCodeEvaluationResult(
                CodeEvaluationStatus.FAILED, "一个用例输出不匹配", "", 1, 20,
                List.of(
                        new EducationCodeTestCaseResult("normal", CodeBehaviorEvaluationStatus.PASSED,
                                "1\n", "1\n", "通过。", 5),
                        new EducationCodeTestCaseResult("edge", CodeBehaviorEvaluationStatus.FAILED,
                                "0\n", "1\n", "输出不匹配。", 5)));
        assertTrue(behavior.hasBehaviorEvidence());
        assertEquals(2, behavior.testCaseCount());
        assertEquals(1, behavior.passedTestCaseCount());
        assertEquals(0.5, behavior.testPassRate());
        assertEquals(CodeBehaviorEvaluationStatus.FAILED, behavior.behaviorStatus());
    }
}
