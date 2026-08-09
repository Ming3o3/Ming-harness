package org.mingharness.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 上下文向量索引的提交策略。
 *
 * <p>正文和 chunk 先提交到数据库，成功提交后再把 embedding 工作投递到有界线程池。
 * 队列满时保留数据库中的未索引 chunk，后续通过受保护的重建接口继续处理。</p>
 */
@ConfigurationProperties(prefix = "harness.context.index")
public record ContextIndexProperties(boolean asyncEnabled, int concurrency, int queueCapacity) {

    public ContextIndexProperties {
        concurrency = Math.min(16, Math.max(1, concurrency));
        queueCapacity = Math.min(10_000, Math.max(1, queueCapacity));
    }
}
