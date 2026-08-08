package org.mingharness.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.util.Locale;
import java.util.Set;

/** 知识库 PDF/DOCX 导入的资源边界。 */
@ConfigurationProperties(prefix = "harness.context.document")
public record DocumentImportProperties(
        int maxUploadBytes,
        int maxContentChars
) {

    @ConstructorBinding
    public DocumentImportProperties {
        maxUploadBytes = bounded(maxUploadBytes, 25 * 1024 * 1024, 128 * 1024, 100 * 1024 * 1024);
        maxContentChars = bounded(maxContentChars, 100_000, 1_000, 1_000_000);
    }

    public boolean supportsExtension(String extension) {
        if (extension == null) return false;
        String normalized = extension.trim().toLowerCase(Locale.ROOT);
        return Set.of("pdf", "docx").contains(normalized);
    }

    private static int bounded(int value, int fallback, int min, int max) {
        if (value < min) return fallback;
        return Math.min(value, max);
    }
}
