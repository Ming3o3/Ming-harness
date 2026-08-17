package org.mingharness.education.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 教师配置编程作业行为测试用例的请求。 */
public record LearningAssignmentTestCaseRequest(
        @NotBlank(message = "测试用例标识不能为空")
        @Size(max = 64, message = "测试用例标识长度不能超过 64 个字符") String caseKey,
        @Size(max = 255, message = "测试用例名称长度不能超过 255 个字符") String name,
        @Size(max = 20000, message = "测试输入长度不能超过 20000 个字符") String input,
        @NotBlank(message = "期望输出不能为空")
        @Size(max = 20000, message = "期望输出长度不能超过 20000 个字符") String expectedOutput,
        boolean hidden,
        @DecimalMin(value = "0.01", message = "测试权重必须大于 0")
        @DecimalMax(value = "100", message = "测试权重不能超过 100") Double weight,
        Integer sequence
) {
    public double effectiveWeight() {
        return weight == null ? 1.0 : weight;
    }

    public int effectiveSequence() {
        return sequence == null ? 0 : sequence;
    }
}
