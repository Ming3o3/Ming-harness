package org.mingharness.education;

/** 代码评测上下文；测试用例来自 Run 级冻结快照，而不是当前可变作业配置。 */
public record EducationCodeEvaluationContext(
        EducationProgrammingTestCaseSnapshot testCaseSnapshot
) {
    public EducationCodeEvaluationContext {
        testCaseSnapshot = testCaseSnapshot == null
                ? EducationProgrammingTestCaseSnapshot.empty() : testCaseSnapshot;
    }

    public static EducationCodeEvaluationContext empty() {
        return new EducationCodeEvaluationContext(EducationProgrammingTestCaseSnapshot.empty());
    }
}
