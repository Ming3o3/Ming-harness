package org.mingharness.education;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CodeDiagnosticClassifierTests {

    @Test
    void normalizesCommonCrossLanguageDiagnostics() {
        assertEquals(CodeDiagnosticCategory.SYNTAX, classify(CodeEvaluationStatus.FAILED,
                "SyntaxError: invalid syntax"));
        assertEquals(CodeDiagnosticCategory.STRUCTURE, classify(CodeEvaluationStatus.FAILED,
                "IndentationError: unexpected indent"));
        assertEquals(CodeDiagnosticCategory.IDENTIFIER, classify(CodeEvaluationStatus.FAILED,
                "NameError: name 'total' is not defined"));
        assertEquals(CodeDiagnosticCategory.IDENTIFIER, classify(CodeEvaluationStatus.FAILED,
                "error: cannot find symbol"));
        assertEquals(CodeDiagnosticCategory.TYPE, classify(CodeEvaluationStatus.FAILED,
                "TypeError: unsupported operand type"));
        assertEquals(CodeDiagnosticCategory.TYPE, classify(CodeEvaluationStatus.FAILED,
                "incompatible types: String cannot be converted to int"));
        assertEquals(CodeDiagnosticCategory.DEPENDENCY, classify(CodeEvaluationStatus.FAILED,
                "ModuleNotFoundError: No module named pandas"));
        assertEquals(CodeDiagnosticCategory.COMPILATION, classify(CodeEvaluationStatus.FAILED,
                "compilation failed"));
    }

    @Test
    void keepsTimeoutUnknownAndNonFailureStatesDistinguishable() {
        assertEquals(CodeDiagnosticCategory.TIMEOUT, classify(CodeEvaluationStatus.TIMEOUT, ""));
        assertEquals(CodeDiagnosticCategory.UNKNOWN, classify(CodeEvaluationStatus.FAILED,
                "工具链返回了未识别的诊断"));
        assertEquals(CodeDiagnosticCategory.NONE, classify(CodeEvaluationStatus.PASSED,
                "syntax/compile check passed"));
        assertEquals(CodeDiagnosticCategory.NONE, classify(CodeEvaluationStatus.NOT_REQUESTED, ""));
    }

    private CodeDiagnosticCategory classify(CodeEvaluationStatus status, String diagnostics) {
        return CodeDiagnosticClassifier.classify(new EducationCodeEvaluationResult(
                status, diagnostics, "", 1, 4));
    }
}
