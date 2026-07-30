package org.mingharness.common;

import java.time.Instant;
import java.util.Map;

public record ErrorResponse(
        String code,
        String message,
        Map<String, String> details,
        Instant timestamp
) {
}
