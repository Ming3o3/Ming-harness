package org.mingharness.context;

import org.mingharness.config.EmbeddingProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** 将一个父对象当前有效的子块批量向量化并写入 pgvector。 */
@Service
public class ContextEmbeddingIndexer {

    private final ContextChunkRepository chunkRepository;
    private final EmbeddingGateway embeddingGateway;
    private final ContextEmbeddingStore embeddingStore;
    private final EmbeddingProperties properties;

    public ContextEmbeddingIndexer(ContextChunkRepository chunkRepository,
                                   EmbeddingGateway embeddingGateway,
                                   ContextEmbeddingStore embeddingStore,
                                   EmbeddingProperties properties) {
        this.chunkRepository = chunkRepository;
        this.embeddingGateway = embeddingGateway;
        this.embeddingStore = embeddingStore;
        this.properties = properties;
    }

    /**
     * 返回本次成功写入的 chunk 数量。embedding 未开启或当前数据库不是 PostgreSQL 时返回 0，
     * 让 local/H2 保持现有关键词召回行为。
     */
    public int indexParent(String parentType, String parentId) {
        if (!embeddingGateway.enabled() || !embeddingStore.supported()) return 0;
        List<ContextChunk> chunks = chunkRepository
                .findByParentTypeAndParentIdAndDeletedAtIsNullOrderByChunkIndexAsc(parentType, parentId);
        return indexChunks(chunks);
    }

    public int indexChunks(List<ContextChunk> chunks) {
        if (!embeddingGateway.enabled() || !embeddingStore.supported() || chunks == null || chunks.isEmpty()) return 0;
        int indexed = 0;
        for (int start = 0; start < chunks.size(); start += properties.batchSize()) {
            List<ContextChunk> batch = chunks.subList(start,
                    Math.min(chunks.size(), start + properties.batchSize()));
            List<EmbeddingVector> vectors = embeddingGateway.embed(batch.stream()
                    .map(ContextChunk::getContent).toList());
            if (vectors.size() != batch.size()) {
                throw new EmbeddingGatewayException(false, "embedding 返回数量与 chunk 数量不一致");
            }
            List<ContextEmbeddingUpdate> updates = new ArrayList<>(batch.size());
            for (int index = 0; index < batch.size(); index++) {
                EmbeddingVector vector = vectors.get(index);
                if (vector.dimension() != properties.dimension()) {
                    throw new EmbeddingGatewayException(false,
                            "embedding 维度不匹配，期望 " + properties.dimension());
                }
                updates.add(new ContextEmbeddingUpdate(batch.get(index), vector));
            }
            embeddingStore.save(updates);
            for (ContextEmbeddingUpdate update : updates) {
                update.chunk().markEmbedded(update.vector().model());
            }
            // indexChunks 也会被后台重建服务调用，不能依赖调用方恰好处于 JPA 事务中。
            chunkRepository.saveAll(batch);
            indexed += updates.size();
        }
        return indexed;
    }

    public boolean ready() {
        return embeddingGateway.enabled() && embeddingStore.supported();
    }

    public int batchSize() {
        return properties.batchSize();
    }
}
