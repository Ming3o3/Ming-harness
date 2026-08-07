package org.mingharness.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * 上下文父文档子块的边界配置。
 *
 * <p>这里使用字符数作为本地、供应商无关的安全边界。接入具体 embedding 模型后，
 * 索引服务仍可在调用模型前使用该模型 tokenizer 做更精确的 token 裁剪。</p>
 */
@ConfigurationProperties(prefix = "harness.context")
public record ContextChunkingProperties(int chunkMaxChars, int chunkOverlapChars,
                                        boolean semanticEnabled,
                                        double semanticBreakpoint,
                                        int semanticMinUnits) {

    /** 兼容已有测试和本地构造调用，默认关闭外部语义分块。 */
    public ContextChunkingProperties(int chunkMaxChars, int chunkOverlapChars) {
        this(chunkMaxChars, chunkOverlapChars, false, 0.35, 3);
    }

    @ConstructorBinding
    public ContextChunkingProperties {
        chunkMaxChars = Math.min(12_000, Math.max(128, chunkMaxChars));
        chunkOverlapChars = Math.min(chunkMaxChars / 2, Math.max(0, chunkOverlapChars));
        semanticBreakpoint = Double.isFinite(semanticBreakpoint)
                ? Math.min(1.0, Math.max(-1.0, semanticBreakpoint)) : 0.35;
        semanticMinUnits = Math.min(20, Math.max(2, semanticMinUnits));
    }
}
