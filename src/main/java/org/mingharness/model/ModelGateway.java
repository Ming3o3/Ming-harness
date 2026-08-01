package org.mingharness.model;

import java.util.function.Consumer;

public interface ModelGateway {

    ModelResponse complete(ModelRequest request);

    /**
     * 返回模型的完整结果，同时在供应商支持时持续提供当前已生成的正文快照。
     *
     * <p>默认实现保持旧网关兼容：不具备流式能力的实现仍会在完整响应可用后通知一次。
     * 回调接收的是完整的当前文本，而不是单个 token，便于调用方在断线恢复时持久化最新快照。</p>
     */
    default ModelResponse completeStreaming(ModelRequest request, Consumer<String> onContent) {
        ModelResponse response = complete(request);
        if (onContent != null && !response.content().isBlank()) {
            onContent.accept(response.content());
        }
        return response;
    }
}
