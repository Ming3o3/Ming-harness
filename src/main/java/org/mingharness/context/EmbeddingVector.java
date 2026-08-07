package org.mingharness.context;

import java.util.List;

/** 一条 embedding API 返回的向量，保留供应商模型名便于重建索引。 */
public record EmbeddingVector(String model, List<Double> values) {

    public EmbeddingVector {
        values = values == null ? List.of() : List.copyOf(values);
    }

    public int dimension() {
        return values.size();
    }
}
