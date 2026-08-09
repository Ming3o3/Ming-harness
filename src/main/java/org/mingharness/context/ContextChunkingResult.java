package org.mingharness.context;

import java.util.List;

/** 分块结果及其可重建的策略版本。 */
public record ContextChunkingResult(
        List<ContextChunkDraft> chunks,
        String strategy,
        String version
) {
    public ContextChunkingResult {
        chunks = chunks == null ? List.of() : List.copyOf(chunks);
        strategy = strategy == null || strategy.isBlank() ? "DETERMINISTIC" : strategy;
        version = version == null || version.isBlank() ? "deterministic-v1" : version;
    }
}
