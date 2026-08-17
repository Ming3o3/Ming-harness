package org.mingharness.education;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** 一次教育 Run 使用的、带捕获时点的学习者状态快照。 */
public record LearnerStateSnapshot(
        String version,
        Instant capturedAt,
        Map<String, LearnerStateEvidence> evidence
) {

    public LearnerStateSnapshot {
        version = version == null || version.isBlank() ? "LEGACY" : version.trim();
        evidence = evidence == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(evidence));
    }

    public static LearnerStateSnapshot empty() {
        return new LearnerStateSnapshot("EMPTY", null, Map.of());
    }

    public boolean hasEvidence() {
        return !evidence.isEmpty();
    }
}
