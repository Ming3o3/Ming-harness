package org.mingharness.context.api;

import java.util.List;

public record ContextBuilderResponse(String text, List<ContextEvidence> evidences) {
}
