package org.mingharness.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.HttpMediaTypeNotSupportedException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTests {

    @Test
    void shouldReturn415ForUnsupportedMediaType() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler(new SensitiveDataSanitizer());

        var response = handler.handleUnsupportedMediaType(new HttpMediaTypeNotSupportedException(
                MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON)));

        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("UNSUPPORTED_MEDIA_TYPE", response.getBody().code());
    }

    @Test
    void shouldLogUnhandledExceptionWithTraceIdAndStackTrace(CapturedOutput output) {
        GlobalExceptionHandler handler = new GlobalExceptionHandler(new SensitiveDataSanitizer());
        HarnessRequestContext.set(new HarnessRequestContext("request-123", "trace-123"));

        try {
            var response = handler.handleUnexpectedException(new IllegalStateException("database unavailable"));

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("INTERNAL_ERROR", response.getBody().code());
            // ConsoleAppender 可能按平台写入 stdout 或 stderr；getAll() 才是完整日志。
            assertTrue(output.getAll().contains("未处理的请求异常"));
            assertTrue(output.getAll().contains(response.getBody().traceId()));
            assertTrue(output.getAll().contains("IllegalStateException"));
            assertTrue(output.getAll().contains("database unavailable"));
        } finally {
            HarnessRequestContext.clear();
        }
    }
}
