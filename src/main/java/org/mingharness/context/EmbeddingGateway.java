package org.mingharness.context;

import java.util.List;

/** 面向上下文索引和查询的 embedding 供应商抽象。 */
public interface EmbeddingGateway {

    boolean enabled();

    List<EmbeddingVector> embed(List<String> inputs);

    /** 按组织解析用户在控制台保存的供应商配置；旧调用方仍可使用无租户重载。 */
    default boolean enabled(String tenantId) {
        return enabled();
    }

    default List<EmbeddingVector> embed(String tenantId, List<String> inputs) {
        return embed(inputs);
    }
}
