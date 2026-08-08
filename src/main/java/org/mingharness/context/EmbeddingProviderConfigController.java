package org.mingharness.context;

import jakarta.validation.Valid;
import org.mingharness.context.api.EmbeddingProviderConfigRequest;
import org.mingharness.context.api.EmbeddingProviderConfigView;
import org.mingharness.context.api.EmbeddingConnectionTestView;
import org.mingharness.security.HarnessIdentity;
import org.mingharness.security.HarnessIdentityContext;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 当前组织的 Embedding 连接设置；API Key 永远不会从这里返回。 */
@RestController
@RequestMapping("/api/context/embedding-config")
public class EmbeddingProviderConfigController {

    private final EmbeddingProviderConfigService service;
    private final EmbeddingConnectionTester connectionTester;

    public EmbeddingProviderConfigController(EmbeddingProviderConfigService service,
                                             EmbeddingConnectionTester connectionTester) {
        this.service = service;
        this.connectionTester = connectionTester;
    }

    @GetMapping
    public EmbeddingProviderConfigView get() {
        return service.view(identity().tenantId());
    }

    @PutMapping
    public EmbeddingProviderConfigView update(@Valid @RequestBody EmbeddingProviderConfigRequest request) {
        return service.update(identity().tenantId(), request);
    }

    @org.springframework.web.bind.annotation.PostMapping("/test")
    public EmbeddingConnectionTestView test(@Valid @RequestBody EmbeddingProviderConfigRequest request) {
        return connectionTester.test(service.preview(identity().tenantId(), request));
    }

    @DeleteMapping
    public EmbeddingProviderConfigView reset() {
        return service.reset(identity().tenantId());
    }

    private HarnessIdentity identity() {
        return HarnessIdentityContext.require();
    }
}
