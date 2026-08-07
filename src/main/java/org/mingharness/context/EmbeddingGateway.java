package org.mingharness.context;

import java.util.List;

/** 面向上下文索引和查询的 embedding 供应商抽象。 */
public interface EmbeddingGateway {

    boolean enabled();

    List<EmbeddingVector> embed(List<String> inputs);
}
