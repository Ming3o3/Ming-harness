package org.mingharness.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.util.Locale;

/** 学生代码评测沙箱的独立资源边界；不复用工作区命令执行配置。 */
@ConfigurationProperties(prefix = "harness.education.code-evaluation")
public record CodeEvaluationProperties(
        boolean enabled,
        String dockerExecutable,
        String temporaryRoot,
        int timeoutMs,
        int maxOutputBytes,
        int maxSourceBytes,
        int memoryMb,
        double cpus,
        int pidsLimit,
        String pythonImage,
        String nodeImage,
        String javaImage,
        String cImage,
        String cppImage,
        String goImage,
        String rustImage
) {

    @ConstructorBinding
    public CodeEvaluationProperties {
        dockerExecutable = blankOrDefault(dockerExecutable, "docker");
        temporaryRoot = blankOrDefault(temporaryRoot, "./data/code-evaluation");
        timeoutMs = bounded(timeoutMs, 10_000, 500, 120_000);
        maxOutputBytes = bounded(maxOutputBytes, 100_000, 1_024, 2_000_000);
        maxSourceBytes = bounded(maxSourceBytes, 100_000, 256, 2_000_000);
        memoryMb = bounded(memoryMb, 256, 64, 2_048);
        cpus = cpus <= 0 || !Double.isFinite(cpus) ? 1.0 : Math.min(4.0, cpus);
        pidsLimit = bounded(pidsLimit, 64, 16, 512);
        pythonImage = blankOrDefault(pythonImage, "python:3.12-slim");
        nodeImage = blankOrDefault(nodeImage, "node:22-bookworm-slim");
        javaImage = blankOrDefault(javaImage, "eclipse-temurin:21-jdk");
        cImage = blankOrDefault(cImage, "gcc:14");
        cppImage = blankOrDefault(cppImage, cImage);
        goImage = blankOrDefault(goImage, "golang:1.23");
        rustImage = blankOrDefault(rustImage, "rust:1.82-slim");
    }

    private static String blankOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static int bounded(int value, int fallback, int min, int max) {
        if (value < min) return fallback;
        return Math.min(value, max);
    }

    public String imageFor(String language) {
        return switch (normalize(language)) {
            case "PYTHON", "PY" -> pythonImage;
            case "JAVASCRIPT", "JS", "NODE" -> nodeImage;
            case "JAVA" -> javaImage;
            case "C" -> cImage;
            case "CPP", "C++", "CXX" -> cppImage;
            case "GO", "GOLANG" -> goImage;
            case "RUST", "RS" -> rustImage;
            default -> null;
        };
    }

    public String normalizedLanguage(String language) {
        return normalize(language);
    }

    private static String normalize(String language) {
        return language == null ? "" : language.trim().toUpperCase(Locale.ROOT);
    }
}
