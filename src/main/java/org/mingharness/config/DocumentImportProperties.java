package org.mingharness.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.util.Locale;
import java.util.Set;

/** 知识库 PDF/DOCX 导入的资源边界。 */
@ConfigurationProperties(prefix = "harness.context.document")
public record DocumentImportProperties(
        int maxUploadBytes,
        int maxContentChars,
        String importDirectory,
        int workerCount,
        int queueCapacity
) {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("pdf", "docx");

    /** 兼容已有测试和本地构造调用。 */
    public DocumentImportProperties(int maxUploadBytes, int maxContentChars) {
        this(maxUploadBytes, maxContentChars, "./data/context-document-imports", 1, 16);
    }

    @ConstructorBinding
    public DocumentImportProperties {
        maxUploadBytes = bounded(maxUploadBytes, 100 * 1024 * 1024, 128 * 1024, 100 * 1024 * 1024);
        maxContentChars = bounded(maxContentChars, 1_000_000, 1_000, 10_000_000);
        importDirectory = importDirectory == null || importDirectory.isBlank()
                ? "./data/context-document-imports" : importDirectory.trim();
        workerCount = bounded(workerCount, 1, 1, 4);
        queueCapacity = bounded(queueCapacity, 16, 1, 128);
    }

    public boolean supportsExtension(String extension) {
        if (extension == null) return false;
        String normalized = extension.trim().toLowerCase(Locale.ROOT);
        return SUPPORTED_EXTENSIONS.contains(normalized);
    }

    private static int bounded(int value, int fallback, int min, int max) {
        if (value < min) return fallback;
        return Math.min(value, max);
    }
}
