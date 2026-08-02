package org.mingharness.model.api;

/** 模型连接探测结果，不包含请求内容、API Key 或供应商响应正文。 */
public record ModelConnectionTestView(
        boolean success,
        String status,
        String message,
        String modelName,
        long latencyMs
) {
}
