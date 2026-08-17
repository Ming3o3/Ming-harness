package org.mingharness.education;

/** Run 中冻结的最小行为测试数据；不包含教师可变的实体字段。 */
public record EducationProgrammingTestCase(
        String caseKey,
        String input,
        String expectedOutput,
        double weight
) {
    public EducationProgrammingTestCase {
        caseKey = caseKey == null ? "" : caseKey.trim();
        input = input == null ? "" : input;
        expectedOutput = expectedOutput == null ? "" : expectedOutput;
        weight = !Double.isFinite(weight) || weight <= 0.0 ? 1.0 : Math.min(100.0, weight);
    }
}
