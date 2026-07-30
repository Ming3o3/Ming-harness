package org.mingharness.context.api;

import java.util.List;

public record ContextResult(String text, List<ContextEvidence> evidences) {

    public boolean isEmpty() {
        return evidences == null || evidences.isEmpty();
    }
}
