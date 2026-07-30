package org.mingharness.model;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "harness.model")
public record ModelConfig(
        boolean enabled,
        String baseUrl,
        String apiKey,
        String name
) {
}
