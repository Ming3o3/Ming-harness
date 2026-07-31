package org.mingharness.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 统一处理请求关联 ID 和 Trace ID，并通过响应头返回给调用方。
 * 外部传入的 ID 只接受有限字符集，避免日志注入和超长 Header 风险。
 */
@Component
public class HarnessRequestContextFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = safeOrNew(request.getHeader(REQUEST_ID_HEADER));
        String traceId = safeOrNew(request.getHeader(TRACE_ID_HEADER));
        HarnessRequestContext context = new HarnessRequestContext(requestId, traceId);

        HarnessRequestContext.set(context);
        MDC.put("requestId", requestId);
        MDC.put("traceId", traceId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("requestId");
            MDC.remove("traceId");
            HarnessRequestContext.clear();
        }
    }

    private String safeOrNew(String value) {
        return value != null && SAFE_ID.matcher(value).matches()
                ? value : UUID.randomUUID().toString();
    }
}
