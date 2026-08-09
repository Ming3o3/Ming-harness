package org.mingharness.context;

import java.time.Instant;
import java.util.Optional;

/**
 * embedding 结果缓存抽象。
 *
 * <p>缓存键必须包含租户、请求模型、向量维度和正文哈希，避免不同模型或不同租户之间
 * 意外复用结果。缓存只是索引加速层，任何读写失败都不应阻断主索引流程。</p>
 */
public interface ContextEmbeddingCache {

    Optional<EmbeddingVector> find(String tenantId, String contentHash,
                                    String requestModel, String modelVersion, int dimension);

    void save(String tenantId, String contentHash, String requestModel,
              String modelVersion, EmbeddingVector vector);

    int deleteUpdatedBefore(Instant cutoff);
}
