package org.mingharness.context;

import org.junit.jupiter.api.Test;
import org.mingharness.config.ContextChunkingProperties;
import org.mingharness.config.EmbeddingProperties;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContextSemanticChunkerTests {

    @Test
    void shouldUseAdjacentEmbeddingSimilarityToFindSemanticBoundary() {
        ContextChunker deterministic = new ContextChunker(new ContextChunkingProperties(120, 10));
        ContextChunkingProperties properties = new ContextChunkingProperties(120, 10, true, 0.8, 2);
        EmbeddingGateway gateway = mock(EmbeddingGateway.class);
        when(gateway.enabled()).thenReturn(true);
        when(gateway.embed(anyList())).thenReturn(List.of(
                new EmbeddingVector("model", List.of(1.0, 0.0)),
                new EmbeddingVector("model", List.of(1.0, 0.0)),
                new EmbeddingVector("model", List.of(0.0, 1.0)),
                new EmbeddingVector("model", List.of(0.0, 1.0))));
        EmbeddingProperties embeddingProperties = new EmbeddingProperties(true, "http://embedding", "key",
                "model", 2, 8, 1_000, 100_000, 1, 0, 10_000);

        ContextSemanticChunker chunker = new ContextSemanticChunker(deterministic, properties,
                embeddingProperties, gateway);

        ContextChunkingResult result = chunker.chunk("主题说明。第一步完成。\n\n第二部分说明。第二步完成。");

        assertEquals("SEMANTIC", result.strategy());
        assertEquals("semantic-v2", result.version());
        assertEquals(2, result.chunks().size());
        assertTrue(result.chunks().get(0).content().contains("第一步完成"));
        assertTrue(result.chunks().get(1).content().contains("第二步完成"));
    }

    @Test
    void shouldFallbackToDeterministicChunksWhenEmbeddingFails() {
        ContextChunker deterministic = new ContextChunker(new ContextChunkingProperties(120, 10));
        ContextChunkingProperties properties = new ContextChunkingProperties(120, 10, true, 0.8, 2);
        EmbeddingGateway gateway = mock(EmbeddingGateway.class);
        when(gateway.enabled()).thenReturn(true);
        when(gateway.embed(anyList())).thenThrow(new EmbeddingGatewayException(true, "temporary"));
        EmbeddingProperties embeddingProperties = new EmbeddingProperties(true, "http://embedding", "key",
                "model", 2, 8, 1_000, 100_000, 1, 0, 10_000);

        ContextChunkingResult result = new ContextSemanticChunker(deterministic, properties,
                embeddingProperties, gateway).chunk("第一段。\n\n第二段。\n\n第三段。");

        assertEquals("DETERMINISTIC", result.strategy());
        assertEquals("deterministic-v1", result.version());
        assertTrue(result.chunks().size() >= 1);
    }

    @Test
    void shouldFallbackBeforeEmbeddingWhenAtomicUnitExceedsTokenBudget() {
        ContextChunker deterministic = new ContextChunker(new ContextChunkingProperties(120, 10));
        ContextChunkingProperties properties = new ContextChunkingProperties(120, 10, true, 0.8, 2);
        EmbeddingGateway gateway = mock(EmbeddingGateway.class);
        when(gateway.enabled()).thenReturn(true);
        EmbeddingProperties embeddingProperties = new EmbeddingProperties(true, "http://embedding", "key",
                "model", "v1", 2, 8, 1_000, 2, 100_000, 1, 0, 10_000);

        ContextChunkingResult result = new ContextSemanticChunker(deterministic, properties,
                embeddingProperties, gateway).chunk("第一段内容。第二段内容。第三段内容。");

        assertEquals("DETERMINISTIC", result.strategy());
        verify(gateway, never()).embed(anyList());
    }

    @Test
    void shouldKeepMarkdownCodeAndTablesAsProtectedEmbeddingUnits() {
        ContextChunker deterministic = new ContextChunker(new ContextChunkingProperties(1_000, 10));
        ContextChunkingProperties properties = new ContextChunkingProperties(1_000, 10, true, 0.8, 2);
        EmbeddingGateway gateway = mock(EmbeddingGateway.class);
        when(gateway.enabled()).thenReturn(true);
        when(gateway.embed(anyList())).thenAnswer(invocation -> {
            List<String> inputs = invocation.getArgument(0);
            return inputs.stream()
                    .map(ignored -> new EmbeddingVector("model", List.of(1.0, 0.0)))
                    .toList();
        });
        EmbeddingProperties embeddingProperties = new EmbeddingProperties(true, "http://embedding", "key",
                "model", 2, 8, 1_000, 100_000, 1, 0, 10_000);
        ContextSemanticChunker chunker = new ContextSemanticChunker(deterministic, properties,
                embeddingProperties, gateway);

        String markdown = """
                # 部署说明
                应用需要先准备数据库。

                ```java
                client.connect();
                if (ready) { start(); }
                ```

                | 配置项 | 说明 |
                | --- | --- |
                | timeout | 请求超时时间 |
                """;
        ContextChunkingResult result = chunker.chunk(markdown);

        assertEquals("SEMANTIC", result.strategy());
        assertEquals("semantic-v2", result.version());
        verify(gateway, atLeastOnce()).embed(argThat(inputs -> inputs.stream()
                .anyMatch(value -> value.contains("```java") && value.contains("client.connect()"))));
        verify(gateway, atLeastOnce()).embed(argThat(inputs -> inputs.stream()
                .anyMatch(value -> value.contains("| 配置项 | 说明 |") && value.contains("| timeout |"))));
        assertTrue(result.chunks().stream().anyMatch(chunk -> chunk.content().contains("client.connect()")));
        assertTrue(result.chunks().stream().anyMatch(chunk -> chunk.content().contains("| timeout |")));
    }
}
