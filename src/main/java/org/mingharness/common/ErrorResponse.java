package org.mingharness.common;

import java.time.Instant;
import java.util.Map;

public record ErrorResponse(
        String code,
        String message,
        Map<String, String> details,
        Instant timestamp,
        String traceId
) {

    /** 兼容已有调用方，自动附加当前请求的 Trace ID。 */
    public ErrorResponse(String code, String message, Map<String, String> details, Instant timestamp) {
        this(code, message, details, timestamp, HarnessRequestContext.currentTraceId());
    }
}
