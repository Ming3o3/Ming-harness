package org.mingharness.education;

import java.util.List;

/** 编程作业测试用例的 Run 级不可变快照。 */
public record EducationProgrammingTestCaseSnapshot(
        String version,
        List<EducationProgrammingTestCase> cases
) {
    public EducationProgrammingTestCaseSnapshot {
        version = version == null || version.isBlank() ? "legacy" : version.trim();
        cases = cases == null ? List.of() : List.copyOf(cases);
    }

    public static EducationProgrammingTestCaseSnapshot empty() {
        return new EducationProgrammingTestCaseSnapshot("empty", List.of());
    }
}
