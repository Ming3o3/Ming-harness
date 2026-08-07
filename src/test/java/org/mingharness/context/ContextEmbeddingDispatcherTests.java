package org.mingharness.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mingharness.config.ContextIndexProperties;
import org.mingharness.observability.HarnessMetrics;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ContextEmbeddingDispatcherTests {

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void shouldNotCallEmbeddingBeforeTransactionCommits() {
        ContextEmbeddingIndexer indexer = mock(ContextEmbeddingIndexer.class);
        HarnessMetrics metrics = mock(HarnessMetrics.class);
        when(indexer.ready()).thenReturn(true);
        ContextEmbeddingDispatcher dispatcher = new ContextEmbeddingDispatcher(indexer, metrics,
                new ContextIndexProperties(false, 1, 1));
        try {
            TransactionSynchronizationManager.initSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(true);

            dispatcher.dispatchAfterCommit("DOCUMENT", "doc-1");
            verify(indexer, never()).indexParent("DOCUMENT", "doc-1");

            List<TransactionSynchronization> synchronizations =
                    TransactionSynchronizationManager.getSynchronizations();
            assertEquals(1, synchronizations.size());
            synchronizations.get(0).afterCommit();
            verify(indexer).indexParent("DOCUMENT", "doc-1");
        } finally {
            dispatcher.shutdown();
        }
    }

    @Test
    void shouldRunCommittedIndexAsynchronouslyWhenEnabled() {
        ContextEmbeddingIndexer indexer = mock(ContextEmbeddingIndexer.class);
        HarnessMetrics metrics = mock(HarnessMetrics.class);
        when(indexer.ready()).thenReturn(true);
        ContextEmbeddingDispatcher dispatcher = new ContextEmbeddingDispatcher(indexer, metrics,
                new ContextIndexProperties(true, 1, 1));
        try {
            dispatcher.dispatchAfterCommit("MEMORY", "memory-1");
            verify(indexer, timeout(1_000)).indexParent("MEMORY", "memory-1");
        } finally {
            dispatcher.shutdown();
        }
    }

    @Test
    void shouldSkipDispatchWhenEmbeddingIsNotReady() {
        ContextEmbeddingIndexer indexer = mock(ContextEmbeddingIndexer.class);
        HarnessMetrics metrics = mock(HarnessMetrics.class);
        when(indexer.ready()).thenReturn(false);
        ContextEmbeddingDispatcher dispatcher = new ContextEmbeddingDispatcher(indexer, metrics,
                new ContextIndexProperties(true, 1, 1));
        try {
            dispatcher.dispatchAfterCommit("DOCUMENT", "doc-1");
            verify(indexer).ready();
            verify(indexer, never()).indexParent("DOCUMENT", "doc-1");
            verifyNoInteractions(metrics);
        } finally {
            dispatcher.shutdown();
        }
    }
}
