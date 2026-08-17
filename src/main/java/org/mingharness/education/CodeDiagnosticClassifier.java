package org.mingharness.education;

import java.util.Locale;

/**
 * 将不同语言工具链的诊断文本归一为有限类别。
 *
 * <p>分类只使用已脱敏的诊断摘要，不把分类结果当作教师评分；未匹配的文本保留为
 * UNKNOWN，避免规则猜测造成错误的知识点归因。</p>
 */
public final class CodeDiagnosticClassifier {

    private CodeDiagnosticClassifier() {
    }

    public static CodeDiagnosticCategory classify(EducationCodeEvaluationResult evaluation) {
        if (evaluation == null || evaluation.status() == null) return CodeDiagnosticCategory.UNKNOWN;
        if (evaluation.status() == CodeEvaluationStatus.PASSED
                || evaluation.status() == CodeEvaluationStatus.NOT_REQUESTED
                || evaluation.status() == CodeEvaluationStatus.REJECTED
                || evaluation.status() == CodeEvaluationStatus.UNAVAILABLE
                || evaluation.status() == CodeEvaluationStatus.ERROR) {
            return CodeDiagnosticCategory.NONE;
        }
        if (evaluation.status() == CodeEvaluationStatus.TIMEOUT) return CodeDiagnosticCategory.TIMEOUT;
        String text = (evaluation.diagnostics() + "\n" + evaluation.output())
                .toLowerCase(Locale.ROOT);
        if (contains(text, "输出不匹配", "output mismatch", "wrong answer")) {
            return CodeDiagnosticCategory.OUTPUT_MISMATCH;
        }
        if (contains(text, "边界", "edge case", "edge-case")) {
            return CodeDiagnosticCategory.EDGE_CASE;
        }
        if (contains(text, "复杂度", "complexity", "time limit")) {
            return CodeDiagnosticCategory.COMPLEXITY;
        }
        if (contains(text, "运行时", "runtime error", "traceback", "exception")) {
            return CodeDiagnosticCategory.RUNTIME;
        }
        if (contains(text, "indentationerror", "indentation error", "unexpected indent",
                "missing }", "expected }", "unmatched", "unclosed")) {
            return CodeDiagnosticCategory.STRUCTURE;
        }
        if (contains(text, "syntaxerror", "syntax error", "parse error", "invalid syntax",
                "unexpected token", "expected ';'", "expected ')'")
                || text.contains("语法")) {
            return CodeDiagnosticCategory.SYNTAX;
        }
        if (contains(text, "nameerror", "cannot find symbol", "not declared", "undeclared",
                "is not defined", "undefined variable")) {
            return CodeDiagnosticCategory.IDENTIFIER;
        }
        if (contains(text, "typeerror", "type mismatch", "incompatible types", "cannot convert",
                "expected type", "invalid operation")) {
            return CodeDiagnosticCategory.TYPE;
        }
        if (contains(text, "modulenotfounderror", "no module named", "cannot find package",
                "package does not exist", "undefined reference", "linker", "unresolved import")) {
            return CodeDiagnosticCategory.DEPENDENCY;
        }
        if (contains(text, "error:", "error ", "fatal error", "compilation failed",
                "could not compile")) {
            return CodeDiagnosticCategory.COMPILATION;
        }
        return CodeDiagnosticCategory.UNKNOWN;
    }

    private static boolean contains(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) return true;
        }
        return false;
    }
}
