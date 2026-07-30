package org.mingharness.context.api;

public record ContextEvidence(
        String documentId,
        String title,
        String citation,
        String excerpt
) {
}
