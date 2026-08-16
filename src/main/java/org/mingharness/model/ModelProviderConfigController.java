package org.mingharness.model;

import jakarta.validation.Valid;
import org.mingharness.model.api.ModelConnectionTestView;
import org.mingharness.model.api.ModelProviderConfigView;
import org.mingharness.model.api.UpdateModelProviderConfigRequest;
import org.mingharness.security.HarnessIdentity;
import org.mingharness.security.HarnessIdentityContext;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理员维护租户默认模型连接设置；API Key 永远不会从这里返回。 */
@RestController
@RequestMapping("/api/model-config")
public class ModelProviderConfigController {

    private final ModelProviderConfigService service;
    private final ModelConnectionTester connectionTester;

    public ModelProviderConfigController(ModelProviderConfigService service,
                                         ModelConnectionTester connectionTester) {
        this.service = service;
        this.connectionTester = connectionTester;
    }

    @GetMapping
    public ModelProviderConfigView get() {
        HarnessIdentity identity = HarnessIdentityContext.require();
        return service.viewTenantDefault(identity.tenantId(), identity.userId());
    }

    @PutMapping
    public ModelProviderConfigView update(@Valid @RequestBody UpdateModelProviderConfigRequest request) {
        HarnessIdentity identity = HarnessIdentityContext.require();
        return service.updateTenantDefault(identity.tenantId(), identity.userId(), request);
    }

    @PostMapping("/test")
    public ModelConnectionTestView test(@Valid @RequestBody UpdateModelProviderConfigRequest request) {
        HarnessIdentity identity = HarnessIdentityContext.require();
        return connectionTester.test(service.previewTenantDefault(identity.tenantId(), identity.userId(), request),
                identity.tenantId(), identity.userId());
    }

    @DeleteMapping
    public ModelProviderConfigView reset() {
        HarnessIdentity identity = HarnessIdentityContext.require();
        return service.resetTenantDefault(identity.tenantId());
    }
}
