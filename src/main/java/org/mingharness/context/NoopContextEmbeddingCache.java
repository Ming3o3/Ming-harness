package org.mingharness.context;

import java.time.Instant;
import java.util.Optional;

/** local/H2 或单元测试中的空缓存实现。 */
final class NoopContextEmbeddingCache implements ContextEmbeddingCache {

    @Override
    public Optional<EmbeddingVector> find(String tenantId, String contentHash,
                                           String requestModel, String modelVersion, int dimension) {
        return Optional.empty();
    }

    @Override
    public void save(String tenantId, String contentHash, String requestModel,
                     String modelVersion, EmbeddingVector vector) {
        // 向量缓存不是主索引路径，非 PostgreSQL 环境直接跳过。
    }

    @Override
    public int deleteUpdatedBefore(Instant cutoff) {
        return 0;
    }
}
