package org.mingharness.education;

import org.junit.jupiter.api.Test;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.config.CodeEvaluationProperties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DockerEducationCodeEvaluatorTests {

    @Test
    void returnsUnavailableWithoutStartingDockerWhenEvaluationIsDisabled() {
        DockerEducationCodeEvaluator evaluator = new DockerEducationCodeEvaluator(
                properties(false), new SensitiveDataSanitizer());

        EducationCodeEvaluationResult result = evaluator.evaluate("PYTHON", "print('ok')");

        assertEquals(CodeEvaluationStatus.UNAVAILABLE, result.status());
    }

    @Test
    void rejectsUnsupportedLanguageBeforeCheckingTheSandbox() {
        DockerEducationCodeEvaluator evaluator = new DockerEducationCodeEvaluator(
                properties(true), new SensitiveDataSanitizer());

        EducationCodeEvaluationResult result = evaluator.evaluate("KOTLIN", "fun main() {}");

        assertEquals(CodeEvaluationStatus.REJECTED, result.status());
    }

    private CodeEvaluationProperties properties(boolean enabled) {
        return new CodeEvaluationProperties(enabled, "docker", "./target/code-evaluation-tests",
                10_000, 100_000, 100_000, 256, 1.0, 64,
                "python:3.12-slim", "node:22-bookworm-slim", "eclipse-temurin:21-jdk",
                "gcc:14", "gcc:14", "golang:1.23", "rust:1.82-slim");
    }
}
