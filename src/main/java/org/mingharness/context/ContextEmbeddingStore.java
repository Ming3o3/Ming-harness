package org.mingharness.context;

import java.util.List;

/** 将 embedding 写入向量列的存储抽象，避免 JPA 直接绑定 pgvector 类型。 */
public interface ContextEmbeddingStore {

    boolean supported();

    void save(List<ContextEmbeddingUpdate> updates);
}
