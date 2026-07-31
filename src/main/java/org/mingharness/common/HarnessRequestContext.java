package org.mingharness.common;

/**
 * 保存当前 HTTP 请求的关联信息，供错误响应、日志和后续网关适配器使用。
 * 使用线程本地变量只保存请求元数据，不保存租户业务数据。
 */
public record HarnessRequestContext(String requestId, String traceId) {

    private static final ThreadLocal<HarnessRequestContext> CURRENT = new ThreadLocal<>();

    static void set(HarnessRequestContext context) {
        CURRENT.set(context);
    }

    static void clear() {
        CURRENT.remove();
    }

    public static HarnessRequestContext current() {
        return CURRENT.get();
    }

    public static String currentTraceId() {
        HarnessRequestContext context = CURRENT.get();
        return context == null ? null : context.traceId();
    }
}
