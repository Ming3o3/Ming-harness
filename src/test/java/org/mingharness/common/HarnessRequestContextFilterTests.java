package org.mingharness.common;

import jakarta.servlet.ServletException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HarnessRequestContextFilterTests {

    private final HarnessRequestContextFilter filter = new HarnessRequestContextFilter();

    @AfterEach
    void clearContext() {
        HarnessRequestContext.clear();
    }

    @Test
    void shouldPropagateSafeIncomingIdsAndClearContextAfterRequest() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HarnessRequestContextFilter.REQUEST_ID_HEADER, "request-123");
        request.addHeader(HarnessRequestContextFilter.TRACE_ID_HEADER, "trace-456");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] traceInChain = new String[1];
        FilterChain chain = (servletRequest, servletResponse) -> traceInChain[0] = HarnessRequestContext.currentTraceId();

        filter.doFilter(request, response, chain);

        assertEquals("request-123", response.getHeader(HarnessRequestContextFilter.REQUEST_ID_HEADER));
        assertEquals("trace-456", response.getHeader(HarnessRequestContextFilter.TRACE_ID_HEADER));
        assertEquals("trace-456", traceInChain[0]);
        assertNull(HarnessRequestContext.current());
    }

    @Test
    void shouldReplaceUnsafeIdsAndExposeTraceIdInErrorResponse() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HarnessRequestContextFilter.REQUEST_ID_HEADER, "bad\nheader");
        request.addHeader(HarnessRequestContextFilter.TRACE_ID_HEADER, "trace/with/slash");
        MockHttpServletResponse response = new MockHttpServletResponse();
        final String[] traceInChain = new String[1];

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            traceInChain[0] = HarnessRequestContext.currentTraceId();
            ErrorResponse error = new ErrorResponse("TEST", "测试", Map.of(), Instant.EPOCH);
            assertEquals(traceInChain[0], error.traceId());
        });

        assertNotNull(traceInChain[0]);
        assertNotEquals("trace/with/slash", traceInChain[0]);
        assertTrue(traceInChain[0].matches("[A-Za-z0-9-]{36}"));
        assertNull(HarnessRequestContext.current());
    }
}
