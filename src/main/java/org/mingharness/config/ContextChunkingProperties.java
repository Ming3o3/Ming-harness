package org.mingharness.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 上下文父文档子块的边界配置。
 *
 * <p>这里使用字符数作为本地、供应商无关的安全边界。接入具体 embedding 模型后，
 * 索引服务仍可在调用模型前使用该模型 tokenizer 做更精确的 token 裁剪。</p>
 */
@ConfigurationProperties(prefix = "harness.context")
public record ContextChunkingProperties(int chunkMaxChars, int chunkOverlapChars) {

    public ContextChunkingProperties {
        chunkMaxChars = Math.min(12_000, Math.max(128, chunkMaxChars));
        chunkOverlapChars = Math.min(chunkMaxChars / 2, Math.max(0, chunkOverlapChars));
    }
}
