package org.mingharness.context;

import org.mingharness.observability.HarnessMetrics;
import org.mingharness.config.EmbeddingProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 将一个父对象当前有效的子块批量向量化并写入 pgvector。 */
@Service
public class ContextEmbeddingIndexer {

    private final ContextChunkRepository chunkRepository;
    private final EmbeddingGateway embeddingGateway;
    private final ContextEmbeddingStore embeddingStore;
    private final EmbeddingProperties properties;
    private final EmbeddingProviderConfigService configService;
    private final ContextEmbeddingCache embeddingCache;
    private final HarnessMetrics metrics;

    public ContextEmbeddingIndexer(ContextChunkRepository chunkRepository,
                                   EmbeddingGateway embeddingGateway,
                                   ContextEmbeddingStore embeddingStore,
                                   EmbeddingProperties properties) {
        this(chunkRepository, embeddingGateway, embeddingStore, properties,
                new NoopContextEmbeddingCache(), null, null);
    }

    public ContextEmbeddingIndexer(ContextChunkRepository chunkRepository,
                                   EmbeddingGateway embeddingGateway,
                                   ContextEmbeddingStore embeddingStore,
                                   EmbeddingProperties properties,
                                   ContextEmbeddingCache embeddingCache) {
        this(chunkRepository, embeddingGateway, embeddingStore, properties, embeddingCache, null);
    }

    public ContextEmbeddingIndexer(ContextChunkRepository chunkRepository,
                                   EmbeddingGateway embeddingGateway,
                                   ContextEmbeddingStore embeddingStore,
                                   EmbeddingProperties properties,
                                   ContextEmbeddingCache embeddingCache,
                                   HarnessMetrics metrics) {
        this(chunkRepository, embeddingGateway, embeddingStore, properties, embeddingCache, metrics, null);
    }

    @Autowired
    public ContextEmbeddingIndexer(ContextChunkRepository chunkRepository,
                                   EmbeddingGateway embeddingGateway,
                                   ContextEmbeddingStore embeddingStore,
                                   EmbeddingProperties properties,
                                   ContextEmbeddingCache embeddingCache,
                                   HarnessMetrics metrics,
                                   EmbeddingProviderConfigService configService) {
        this.chunkRepository = chunkRepository;
        this.embeddingGateway = embeddingGateway;
        this.embeddingStore = embeddingStore;
        this.properties = properties;
        this.embeddingCache = embeddingCache;
        this.metrics = metrics;
        this.configService = configService;
    }

    /**
     * 返回本次成功写入的 chunk 数量。embedding 未开启或当前数据库不是 PostgreSQL 时返回 0，
     * 让 local/H2 保持现有关键词召回行为。
     */
    public int indexParent(String parentType, String parentId) {
        if (configService == null && (!embeddingGateway.enabled() || !embeddingStore.supported())) return 0;
        List<ContextChunk> chunks = chunkRepository
                .findByParentTypeAndParentIdAndDeletedAtIsNullOrderByChunkIndexAsc(parentType, parentId);
        return indexChunks(chunks);
    }

    public int indexChunks(List<ContextChunk> chunks) {
        if (!embeddingStore.supported() || chunks == null || chunks.isEmpty()) return 0;
        String tenantId = chunks.get(0).getTenantId();
        EmbeddingProviderConfigService.ResolvedEmbeddingConfig config = config(tenantId);
        boolean gatewayEnabled = configService == null
                ? embeddingGateway.enabled() : embeddingGateway.enabled(tenantId);
        if (!config.enabled() || !gatewayEnabled) return 0;
        List<ContextChunk> misses = new ArrayList<>();
        List<ContextEmbeddingUpdate> updates = new ArrayList<>(chunks.size());
        for (ContextChunk chunk : chunks) {
            Optional<EmbeddingVector> cached = cached(chunk, config);
            if (cached.isPresent()) {
                updates.add(new ContextEmbeddingUpdate(chunk, cached.get()));
                if (metrics != null) metrics.contextEmbeddingCacheHit();
            } else {
                misses.add(chunk);
                if (metrics != null) metrics.contextEmbeddingCacheMiss();
            }
        }

        for (int start = 0; start < misses.size(); start += config.batchSize()) {
            List<ContextChunk> batch = misses.subList(start,
                    Math.min(misses.size(), start + config.batchSize()));
            List<EmbeddingVector> vectors = configService == null
                    ? embeddingGateway.embed(batch.stream().map(ContextChunk::getContent).toList())
                    : embeddingGateway.embed(tenantId, batch.stream().map(ContextChunk::getContent).toList());
            if (vectors.size() != batch.size()) {
                throw new EmbeddingGatewayException(false, "embedding 返回数量与 chunk 数量不一致");
            }
            for (int index = 0; index < batch.size(); index++) {
                EmbeddingVector vector = vectors.get(index);
                validateDimension(vector, config);
                ContextChunk chunk = batch.get(index);
                updates.add(new ContextEmbeddingUpdate(chunk, vector));
                saveCache(chunk, vector, config);
            }
        }

        if (updates.isEmpty()) return 0;
        embeddingStore.save(updates);
        for (ContextEmbeddingUpdate update : updates) {
            update.chunk().markEmbedded(config.signature());
        }
        // indexChunks 也会被后台重建服务调用，不能依赖调用方恰好处于 JPA 事务中。
        chunkRepository.saveAll(updates.stream().map(ContextEmbeddingUpdate::chunk).toList());
        return updates.size();
    }

    public boolean ready() {
        return embeddingGateway.enabled() && embeddingStore.supported();
    }

    public boolean ready(String tenantId) {
        return embeddingStore.supported() && embeddingGateway.enabled(tenantId);
    }

    public int batchSize() {
        return properties.effectiveBatchSize();
    }

    public int batchSize(String tenantId) {
        return config(tenantId).batchSize();
    }

    private Optional<EmbeddingVector> cached(ContextChunk chunk,
                                             EmbeddingProviderConfigService.ResolvedEmbeddingConfig config) {
        try {
            Optional<EmbeddingVector> value = embeddingCache.find(chunk.getTenantId(),
                    chunk.getContentHash(), config.model(), config.modelVersion(), config.dimension());
            if (value.isPresent() && value.get().dimension() == config.dimension()) {
                return value;
            }
        } catch (RuntimeException ignored) {
            // 缓存是加速层，异常由具体实现记录后应继续走供应商调用。
        }
        return Optional.empty();
    }

    private void validateDimension(EmbeddingVector vector,
                                   EmbeddingProviderConfigService.ResolvedEmbeddingConfig config) {
        if (vector == null || vector.dimension() != config.dimension()) {
            throw new EmbeddingGatewayException(false,
                    "embedding 维度不匹配，期望 " + config.dimension());
        }
    }

    private void saveCache(ContextChunk chunk, EmbeddingVector vector,
                           EmbeddingProviderConfigService.ResolvedEmbeddingConfig config) {
        try {
            embeddingCache.save(chunk.getTenantId(), chunk.getContentHash(), config.model(),
                    config.modelVersion(), vector);
        } catch (RuntimeException ignored) {
            // 缓存写入失败不能回滚已经成功的供应商调用和主向量索引。
        }
    }

    private EmbeddingProviderConfigService.ResolvedEmbeddingConfig config(String tenantId) {
        return configService == null
                ? new EmbeddingProviderConfigService.ResolvedEmbeddingConfig(properties.enabled(), properties.baseUrl(),
                properties.apiKey(), properties.model(), properties.modelVersion(), properties.dimension(),
                properties.effectiveBatchSize(), properties.maxInputChars(), properties.maxInputTokens(),
                properties.maxResponseChars(), properties.maxAttempts(), properties.retryBackoffMs(), properties.timeoutMs(),
                "environment", null)
                : configService.resolve(tenantId);
    }
}
