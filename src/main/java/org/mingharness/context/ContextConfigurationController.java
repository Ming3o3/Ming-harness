package org.mingharness.context;

import org.mingharness.config.ContextChunkingProperties;
import org.mingharness.config.ContextRetrievalProperties;
import org.mingharness.security.HarnessIdentityContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 向控制台提供脱敏的上下文向量运行配置，不返回 embedding 地址或密钥。 */
@RestController
@RequestMapping("/api/context")
public class ContextConfigurationController {

    private final EmbeddingProviderConfigService configService;
    private final ContextChunkingProperties chunkingProperties;
    private final ContextRetrievalProperties retrievalProperties;
    private final ContextEmbeddingStore embeddingStore;

    public ContextConfigurationController(EmbeddingProviderConfigService configService,
                                          ContextChunkingProperties chunkingProperties,
                                          ContextRetrievalProperties retrievalProperties,
                                          ContextEmbeddingStore embeddingStore) {
        this.configService = configService;
        this.chunkingProperties = chunkingProperties;
        this.retrievalProperties = retrievalProperties;
        this.embeddingStore = embeddingStore;
    }

    @GetMapping("/configuration")
    public ContextConfigurationView configuration() {
        var embedding = configService.resolve(HarnessIdentityContext.require().tenantId());
        return new ContextConfigurationView(
                embedding.enabled(),
                embedding.enabled() && embeddingStore.supported(),
                embedding.model(),
                embedding.modelVersion(),
                embedding.dimension(),
                embedding.batchSize(),
                chunkingProperties.chunkMaxChars(),
                chunkingProperties.parentWindowMaxChars(),
                chunkingProperties.semanticEnabled(),
                retrievalProperties.candidateLimit(),
                retrievalProperties.maxCandidatesPerParent(),
                retrievalProperties.maxParents(),
                retrievalProperties.minSimilarity(),
                retrievalProperties.rrfEnabled(),
                retrievalProperties.vectorWeight(),
                retrievalProperties.keywordWeight());
    }

    public record ContextConfigurationView(
            boolean embeddingEnabled,
            boolean embeddingReady,
            String model,
            String modelVersion,
            int dimension,
            int batchSize,
            int chunkMaxChars,
            int parentWindowMaxChars,
            boolean semanticChunkingEnabled,
            int candidateLimit,
            int maxCandidatesPerParent,
            int maxParents,
            double minSimilarity,
            boolean rrfEnabled,
            double vectorWeight,
            double keywordWeight
    ) {
    }
}
