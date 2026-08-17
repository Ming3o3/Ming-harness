package org.mingharness.education;

public interface EducationCodeEvaluator {
    EducationCodeEvaluationResult evaluate(String programmingLanguage, String sourceCode);

    /** 有测试快照时执行行为评测；旧适配器默认退化为原有语法/编译检查。 */
    default EducationCodeEvaluationResult evaluate(String programmingLanguage, String sourceCode,
                                                    EducationCodeEvaluationContext context) {
        return evaluate(programmingLanguage, sourceCode);
    }
}
