package org.mingharness.model;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "harness.model")
public record ModelConfig(
        boolean enabled,
        String baseUrl,
        String apiKey,
        String name,
        String fallbackBaseUrl,
        String fallbackApiKey,
        String fallbackName,
        int maxAttempts,
        long retryBackoffMs,
        int circuitFailureThreshold,
        long circuitOpenMs,
        java.math.BigDecimal inputCostPer1kTokens,
        java.math.BigDecimal outputCostPer1kTokens,
        int maxResponseChars
) {

    public ModelConfig {
        baseUrl = blankOrDefault(baseUrl, "https://api.openai.com/v1");
        name = blankOrDefault(name, "gpt-4o-mini");
        fallbackBaseUrl = blankToNull(fallbackBaseUrl);
        fallbackApiKey = blankToNull(fallbackApiKey);
        fallbackName = blankOrDefault(fallbackName, name);
        maxAttempts = Math.min(5, Math.max(1, maxAttempts));
        retryBackoffMs = Math.max(0, Math.min(10_000, retryBackoffMs));
        circuitFailureThreshold = Math.min(20, Math.max(1, circuitFailureThreshold));
        circuitOpenMs = Math.max(1_000, Math.min(600_000, circuitOpenMs));
        inputCostPer1kTokens = nonNegative(inputCostPer1kTokens);
        outputCostPer1kTokens = nonNegative(outputCostPer1kTokens);
        maxResponseChars = Math.min(1_000_000, Math.max(1_000, maxResponseChars));
    }

    public boolean fallbackEnabled() {
        return fallbackBaseUrl != null && !fallbackBaseUrl.isBlank();
    }

    private static String blankOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static java.math.BigDecimal nonNegative(java.math.BigDecimal value) {
        return value == null || value.signum() < 0 ? java.math.BigDecimal.ZERO : value;
    }
}
