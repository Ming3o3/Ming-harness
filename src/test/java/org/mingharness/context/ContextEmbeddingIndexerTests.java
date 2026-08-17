package org.mingharness.context;

import org.junit.jupiter.api.Test;
import org.mingharness.config.EmbeddingProperties;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContextEmbeddingIndexerTests {

    @Test
    void shouldBatchActiveChunksAndWriteVectors() {
        ContextChunkRepository repository = mock(ContextChunkRepository.class);
        EmbeddingGateway gateway = mock(EmbeddingGateway.class);
        ContextEmbeddingStore store = mock(ContextEmbeddingStore.class);
        EmbeddingProperties properties = new EmbeddingProperties(true, "http://embedding", "key", "model",
                2, 2, 1_000, 100_000, 1, 0, 10_000);
        ContextChunk first = new ContextChunk("tenant-a", "DOCUMENT", "doc-1", 0, "第一块", "hash-1");
        ContextChunk second = new ContextChunk("tenant-a", "DOCUMENT", "doc-1", 1, "第二块", "hash-2");
        ContextChunk third = new ContextChunk("tenant-a", "DOCUMENT", "doc-1", 2, "第三块", "hash-3");
        when(repository.findByParentTypeAndParentIdAndDeletedAtIsNullOrderByChunkIndexAsc("DOCUMENT", "doc-1"))
                .thenReturn(List.of(first, second, third));
        when(gateway.enabled()).thenReturn(true);
        when(store.supported()).thenReturn(true);
        when(gateway.embed(List.of("第一块", "第二块")))
                .thenReturn(List.of(new EmbeddingVector("model", List.of(0.1, 0.2)),
                        new EmbeddingVector("model", List.of(0.2, 0.3))));
        when(gateway.embed(List.of("第三块")))
                .thenReturn(List.of(new EmbeddingVector("model", List.of(0.3, 0.4))));

        ContextEmbeddingIndexer indexer = new ContextEmbeddingIndexer(repository, gateway, store, properties);

        assertEquals(3, indexer.indexParent("DOCUMENT", "doc-1"));
        verify(store).save(anyList());
    }

    @Test
    void shouldNeverSendMoreThanProviderSafeBatchSizeWhenConfiguredBatchIsLarger() {
        ContextChunkRepository repository = mock(ContextChunkRepository.class);
        EmbeddingGateway gateway = mock(EmbeddingGateway.class);
        ContextEmbeddingStore store = mock(ContextEmbeddingStore.class);
        EmbeddingProperties properties = new EmbeddingProperties(true, "http://embedding", "key", "model",
                2, 20, 1_000, 100_000, 1, 0, 10_000);
        List<ContextChunk> chunks = java.util.stream.IntStream.range(0, 11)
                .mapToObj(index -> new ContextChunk("tenant-a", "DOCUMENT", "doc-1", index,
                        "第" + index + "块", "hash-" + index))
                .toList();
        when(repository.findByParentTypeAndParentIdAndDeletedAtIsNullOrderByChunkIndexAsc("DOCUMENT", "doc-1"))
                .thenReturn(chunks);
        when(gateway.enabled()).thenReturn(true);
        when(store.supported()).thenReturn(true);
        when(gateway.embed(anyList())).thenAnswer(invocation -> {
            List<String> inputs = invocation.getArgument(0);
            return inputs.stream()
                    .map(value -> new EmbeddingVector("model", List.of(0.1, 0.2)))
                    .toList();
        });

        ContextEmbeddingIndexer indexer = new ContextEmbeddingIndexer(repository, gateway, store, properties);

        assertEquals(11, indexer.indexParent("DOCUMENT", "doc-1"));
        org.mockito.Mockito.verify(gateway).embed(org.mockito.ArgumentMatchers.argThat(values -> values.size() == 10));
        org.mockito.Mockito.verify(gateway).embed(org.mockito.ArgumentMatchers.argThat(values -> values.size() == 1));
    }

    @Test
    void shouldReuseTenantAndModelScopedCacheWithoutCallingEmbeddingApi() {
        ContextChunkRepository repository = mock(ContextChunkRepository.class);
        EmbeddingGateway gateway = mock(EmbeddingGateway.class);
        ContextEmbeddingStore store = mock(ContextEmbeddingStore.class);
        ContextEmbeddingCache cache = mock(ContextEmbeddingCache.class);
        EmbeddingProperties properties = new EmbeddingProperties(true, "http://embedding", "key", "model",
                2, 8, 1_000, 100_000, 1, 0, 10_000);
        ContextChunk chunk = new ContextChunk("tenant-a", "DOCUMENT", "doc-1", 0,
                "可复用内容", "hash-reusable");
        when(repository.findByParentTypeAndParentIdAndDeletedAtIsNullOrderByChunkIndexAsc("DOCUMENT", "doc-1"))
                .thenReturn(List.of(chunk));
        when(gateway.enabled()).thenReturn(true);
        when(store.supported()).thenReturn(true);
        when(cache.find("tenant-a", "hash-reusable", "model", "v1", 2))
                .thenReturn(Optional.of(new EmbeddingVector("model", List.of(0.3, 0.4))));

        ContextEmbeddingIndexer indexer = new ContextEmbeddingIndexer(repository, gateway, store,
                properties, cache);

        assertEquals(1, indexer.indexParent("DOCUMENT", "doc-1"));
        verify(gateway, never()).embed(anyList());
        org.mockito.Mockito.verify(cache).find("tenant-a", "hash-reusable", "model", "v1", 2);
        org.mockito.Mockito.verify(store).save(anyList());
    }

    @Test
    void shouldCacheOnlyMissedChunksAfterSuccessfulEmbedding() {
        ContextChunkRepository repository = mock(ContextChunkRepository.class);
        EmbeddingGateway gateway = mock(EmbeddingGateway.class);
        ContextEmbeddingStore store = mock(ContextEmbeddingStore.class);
        ContextEmbeddingCache cache = mock(ContextEmbeddingCache.class);
        EmbeddingProperties properties = new EmbeddingProperties(true, "http://embedding", "key", "model",
                2, 8, 1_000, 100_000, 1, 0, 10_000);
        ContextChunk chunk = new ContextChunk("tenant-a", "DOCUMENT", "doc-1", 0,
                "新内容", "hash-new");
        when(repository.findByParentTypeAndParentIdAndDeletedAtIsNullOrderByChunkIndexAsc("DOCUMENT", "doc-1"))
                .thenReturn(List.of(chunk));
        when(gateway.enabled()).thenReturn(true);
        when(store.supported()).thenReturn(true);
        when(cache.find("tenant-a", "hash-new", "model", "v1", 2)).thenReturn(Optional.empty());
        when(gateway.embed(List.of("新内容")))
                .thenReturn(List.of(new EmbeddingVector("model", List.of(0.1, 0.2))));

        ContextEmbeddingIndexer indexer = new ContextEmbeddingIndexer(repository, gateway, store,
                properties, cache);

        assertEquals(1, indexer.indexParent("DOCUMENT", "doc-1"));
        org.mockito.Mockito.verify(cache).save("tenant-a", "hash-new", "model", "v1",
                new EmbeddingVector("model", List.of(0.1, 0.2)));
        org.mockito.Mockito.verify(gateway).embed(List.of("新内容"));
    }

    @Test
    void shouldSkipWhenEmbeddingIsDisabledOrDatabaseDoesNotSupportVectors() {
        ContextChunkRepository repository = mock(ContextChunkRepository.class);
        EmbeddingGateway gateway = mock(EmbeddingGateway.class);
        ContextEmbeddingStore store = mock(ContextEmbeddingStore.class);
        EmbeddingProperties properties = new EmbeddingProperties(false, "http://embedding", "key", "model",
                2, 2, 1_000, 100_000, 1, 0, 10_000);
        when(gateway.enabled()).thenReturn(false);
        when(store.supported()).thenReturn(true);
        ContextEmbeddingIndexer indexer = new ContextEmbeddingIndexer(repository, gateway, store, properties);

        assertEquals(0, indexer.indexParent("DOCUMENT", "doc-1"));

        org.mockito.Mockito.verifyNoInteractions(repository, store);
    }
}
