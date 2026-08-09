package org.mingharness.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mingharness.config.ContextChunkingProperties;
import org.mingharness.config.ContextIndexProperties;
import org.mingharness.observability.HarnessMetrics;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ContextSemanticRechunkDispatcherTests {

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void shouldNotRechunkBeforeTransactionCommits() {
        ContextSemanticRechunker rechunker = mock(ContextSemanticRechunker.class);
        ContextEmbeddingDispatcher embeddingDispatcher = mock(ContextEmbeddingDispatcher.class);
        EmbeddingGateway gateway = readyGateway();
        ContextEmbeddingStore store = readyStore();
        ContextSemanticRechunkDispatcher dispatcher = newDispatcher(rechunker, embeddingDispatcher, gateway, store,
                new ContextIndexProperties(false, 1, 1));
        try {
            TransactionSynchronizationManager.initSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(true);

            assertTrue(dispatcher.dispatchAfterCommit("DOCUMENT", "doc-1"));
            verify(rechunker, never()).rechunk("DOCUMENT", "doc-1");

            List<TransactionSynchronization> synchronizations =
                    TransactionSynchronizationManager.getSynchronizations();
            assertEquals(1, synchronizations.size());
            synchronizations.get(0).afterCommit();
            verify(rechunker).rechunk("DOCUMENT", "doc-1");
        } finally {
            dispatcher.shutdown();
        }
    }

    @Test
    void shouldRunRechunkAsynchronouslyWhenEnabled() {
        ContextSemanticRechunker rechunker = mock(ContextSemanticRechunker.class);
        ContextEmbeddingDispatcher embeddingDispatcher = mock(ContextEmbeddingDispatcher.class);
        ContextSemanticRechunkDispatcher dispatcher = newDispatcher(rechunker, embeddingDispatcher,
                readyGateway(), readyStore(), new ContextIndexProperties(true, 1, 1));
        try {
            assertTrue(dispatcher.dispatchAfterCommit("MEMORY", "memory-1"));
            verify(rechunker, timeout(1_000)).rechunk("MEMORY", "memory-1");
        } finally {
            dispatcher.shutdown();
        }
    }

    @Test
    void shouldSkipRechunkWhenSemanticIndexIsUnavailable() {
        ContextSemanticRechunker rechunker = mock(ContextSemanticRechunker.class);
        ContextEmbeddingDispatcher embeddingDispatcher = mock(ContextEmbeddingDispatcher.class);
        EmbeddingGateway gateway = mock(EmbeddingGateway.class);
        ContextEmbeddingStore store = mock(ContextEmbeddingStore.class);
        when(gateway.enabled()).thenReturn(false);
        when(store.supported()).thenReturn(true);
        ContextSemanticRechunkDispatcher dispatcher = newDispatcher(rechunker, embeddingDispatcher, gateway, store,
                new ContextIndexProperties(true, 1, 1));
        try {
            assertFalse(dispatcher.enabled());
            assertFalse(dispatcher.dispatchAfterCommit("DOCUMENT", "doc-1"));
            verifyNoInteractions(rechunker, embeddingDispatcher);
        } finally {
            dispatcher.shutdown();
        }
    }

    @Test
    void shouldFallbackToDeterministicIndexWhenRechunkFails() {
        ContextSemanticRechunker rechunker = mock(ContextSemanticRechunker.class);
        ContextEmbeddingDispatcher embeddingDispatcher = mock(ContextEmbeddingDispatcher.class);
        doThrow(new IllegalStateException("temporary")).when(rechunker)
                .rechunk("DOCUMENT", "doc-1");
        ContextSemanticRechunkDispatcher dispatcher = newDispatcher(rechunker, embeddingDispatcher,
                readyGateway(), readyStore(), new ContextIndexProperties(false, 1, 1));
        try {
            assertTrue(dispatcher.dispatchAfterCommit("DOCUMENT", "doc-1"));
            verify(embeddingDispatcher).dispatchAfterCommit("DOCUMENT", "doc-1");
        } finally {
            dispatcher.shutdown();
        }
    }

    private ContextSemanticRechunkDispatcher newDispatcher(ContextSemanticRechunker rechunker,
                                                            ContextEmbeddingDispatcher embeddingDispatcher,
                                                            EmbeddingGateway gateway,
                                                            ContextEmbeddingStore store,
                                                            ContextIndexProperties indexProperties) {
        return new ContextSemanticRechunkDispatcher(rechunker, embeddingDispatcher,
                new ContextChunkingProperties(128, 16, true, 0.35, 3, 256),
                indexProperties, gateway, store, mock(HarnessMetrics.class));
    }

    private EmbeddingGateway readyGateway() {
        EmbeddingGateway gateway = mock(EmbeddingGateway.class);
        when(gateway.enabled()).thenReturn(true);
        return gateway;
    }

    private ContextEmbeddingStore readyStore() {
        ContextEmbeddingStore store = mock(ContextEmbeddingStore.class);
        when(store.supported()).thenReturn(true);
        return store;
    }
}
