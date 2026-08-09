package org.mingharness.context;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mingharness.context.api.EmbeddingProviderConfigRequest;
import org.mingharness.context.api.EmbeddingProviderConfigView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证组织级 Embedding 配置的隔离、密钥保护和固定向量维度约束。 */
@SpringBootTest
class EmbeddingProviderConfigServiceTests {

    @Autowired
    private EmbeddingProviderConfigService service;

    @Autowired
    private EmbeddingProviderConfigRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void shouldEncryptKeyAndResolveOnlyForSameTenant() {
        EmbeddingProviderConfigView saved = service.update("tenant-embedding", request(true,
                "https://example.com/v1", "text-embedding-3-small", "secret-embedding-key", false));

        assertTrue(saved.configured());
        assertTrue(saved.enabled());
        assertTrue(saved.apiKeyConfigured());
        assertEquals("••••-key", saved.apiKeyHint());
        assertEquals("tenant", saved.source());
        assertEquals("secret-embedding-key", service.resolve("tenant-embedding").apiKey());
        assertEquals("https://api.openai.com/v1", service.resolve("another-tenant").baseUrl());
        assertNotEquals("secret-embedding-key", repository.findByTenantId("tenant-embedding")
                .orElseThrow().getApiKeyCiphertext());
    }

    @Test
    void shouldRetainOrClearKeyExplicitly() {
        service.update("tenant-embedding", request(true, "https://example.com/v1",
                "model-a", "secret-embedding-key", false));

        EmbeddingProviderConfigView retained = service.update("tenant-embedding", request(true,
                "https://example.com/v1", "model-b", "", false));
        assertTrue(retained.apiKeyConfigured());
        assertEquals("secret-embedding-key", service.resolve("tenant-embedding").apiKey());

        EmbeddingProviderConfigView cleared = service.update("tenant-embedding", request(false,
                "https://example.com/v1", "model-b", "", true));
        assertFalse(cleared.enabled());
        assertFalse(cleared.apiKeyConfigured());
        assertNull(service.resolve("tenant-embedding").apiKey());
    }

    @Test
    void shouldPreviewWithoutPersistingAndRejectUnsupportedDimension() {
        EmbeddingProviderConfigService.ResolvedEmbeddingConfig preview = service.preview("tenant-embedding",
                request(true, "http://localhost:11434/v1", "nomic-embed-text", "preview-secret", false));

        assertTrue(preview.enabled());
        assertEquals("http://localhost:11434/v1", preview.baseUrl());
        assertEquals("nomic-embed-text", preview.model());
        assertEquals("preview-secret", preview.apiKey());
        assertTrue(repository.findByTenantId("tenant-embedding").isEmpty());
        assertThrows(RuntimeException.class, () -> service.update("tenant-embedding",
                new EmbeddingProviderConfigRequest(true, "https://example.com/v1", "model-a", "v1",
                        768, "secret", false)));
    }

    private EmbeddingProviderConfigRequest request(boolean enabled, String baseUrl, String model,
                                                   String apiKey, boolean clearApiKey) {
        return new EmbeddingProviderConfigRequest(enabled, baseUrl, model, "v1", 1536, apiKey, clearApiKey);
    }
}
