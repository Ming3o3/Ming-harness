package org.mingharness.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mingharness.model.api.ModelProviderConfigView;
import org.mingharness.model.api.UpdateModelProviderConfigRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证用户模型配置的隔离、密钥掩码和 AES-GCM 密文不会回到 API。 */
@SpringBootTest
class ModelProviderConfigServiceTests {

    @Autowired
    private ModelProviderConfigService service;
    @Autowired
    private ModelProviderConfigRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void shouldEncryptKeyAndResolveOnlyForSameOwner() {
        ModelProviderConfigView saved = service.update("tenant-model", "operator",
                new UpdateModelProviderConfigRequest(true, "http://localhost:11434/v1",
                        "qwen2.5-coder", "secret-model-key", false));

        assertTrue(saved.configured());
        assertTrue(saved.enabled());
        assertTrue(saved.apiKeyConfigured());
        assertEquals("••••-key", saved.apiKeyHint());
        assertEquals("user", saved.source());
        assertEquals("secret-model-key", service.resolve("tenant-model", "operator").apiKey());
        assertFalse(service.view("tenant-model", "another-user").configured());
        assertNotEquals("secret-model-key", repository.findByTenantIdAndUserId("tenant-model", "operator")
                .orElseThrow().getApiKeyCiphertext());
    }

    @Test
    void shouldRetainOrClearKeyExplicitly() {
        service.update("tenant-model", "operator",
                new UpdateModelProviderConfigRequest(true, "https://example.com/v1",
                        "model-a", "secret-model-key", false));
        ModelProviderConfigView retained = service.update("tenant-model", "operator",
                new UpdateModelProviderConfigRequest(true, "https://example.com/v1",
                        "model-b", "", false));
        assertTrue(retained.apiKeyConfigured());
        assertEquals("secret-model-key", service.resolve("tenant-model", "operator").apiKey());

        ModelProviderConfigView cleared = service.update("tenant-model", "operator",
                new UpdateModelProviderConfigRequest(false, "https://example.com/v1",
                        "model-b", "", true));
        assertFalse(cleared.enabled());
        assertFalse(cleared.apiKeyConfigured());
        assertNull(service.resolve("tenant-model", "operator").apiKey());
    }

    @Test
    void shouldRejectUnsafeBaseUrlAndMissingModelWhenEnabled() {
        assertThrows(RuntimeException.class, () -> service.update("tenant-model", "operator",
                new UpdateModelProviderConfigRequest(true, "file:///tmp/model", "model-a", "", false)));
        assertThrows(RuntimeException.class, () -> service.update("tenant-model", "operator",
                new UpdateModelProviderConfigRequest(true, "https://example.com/v1", "", "", false)));
    }
}
