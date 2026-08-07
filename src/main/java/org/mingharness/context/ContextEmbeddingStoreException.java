package org.mingharness.context;

/** pgvector 写入失败，通常属于基础设施错误，不应被误判成 embedding API 响应错误。 */
public class ContextEmbeddingStoreException extends RuntimeException {

    public ContextEmbeddingStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
