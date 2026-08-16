package org.mingharness.model;

import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.observability.HarnessMetrics;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

/**
 * 按 Run 所属用户动态选择演示模型或租户/个人配置的 OpenAI 兼容供应商。
 * 租户默认配置供所有没有个人覆盖的用户使用，配置快照保证新旧 Run 语义稳定。
 */
@Component
public class RuntimeModelGateway implements ModelGateway {

    private final ModelConfig defaults;
    private final ModelProviderConfigService configService;
    private final RestClient.Builder restClientBuilder;
    private final SensitiveDataSanitizer sanitizer;
    private final HarnessMetrics metrics;
    private final ObjectMapper objectMapper;
    private final DemoModelGateway demoGateway = new DemoModelGateway();
    private final ConcurrentMap<String, CachedGateway> externalGateways = new ConcurrentHashMap<>();

    public RuntimeModelGateway(ModelConfig defaults,
                               ModelProviderConfigService configService,
                               RestClient.Builder restClientBuilder,
                               SensitiveDataSanitizer sanitizer,
                               HarnessMetrics metrics,
                               ObjectMapper objectMapper) {
        this.defaults = defaults;
        this.configService = configService;
        this.restClientBuilder = restClientBuilder;
        this.sanitizer = sanitizer;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
    }

    @Override
    public ModelResponse complete(ModelRequest request) {
        return delegate(request).complete(request);
    }

    @Override
    public ModelResponse completeStreaming(ModelRequest request, Consumer<String> onContent) {
        return delegate(request).completeStreaming(request, onContent);
    }

    private ModelGateway delegate(ModelRequest request) {
        String tenantId = valueOrDefault(request == null ? null : request.tenantId(), "tenant-demo");
        String userId = valueOrDefault(request == null ? null : request.userId(), "operator");
        ModelProviderConfigService.ResolvedModelConfig resolved = configService.resolveForRun(
                tenantId, userId, request == null ? null : request.modelConfigSnapshotId());
        if (!resolved.enabled()) return demoGateway;
        if (resolved.apiKey() == null || resolved.apiKey().isBlank()) {
            throw new ModelGatewayException("configuration", ModelErrorCode.CONFIGURATION_INVALID, false,
                    "外部模型已启用但 API Key 未配置，请联系管理员完成模型配置");
        }

        String owner = tenantId + "\u0000" + userId;
        CachedGateway cached = externalGateways.get(owner);
        if (cached != null && cached.version().equals(resolved.version())) {
            return cached.gateway();
        }
        ModelConfig effective = new ModelConfig(true, resolved.baseUrl(), resolved.apiKey(),
                resolved.modelName(), null, null, null,
                defaults.maxAttempts(), defaults.retryBackoffMs(), defaults.circuitFailureThreshold(),
                defaults.circuitOpenMs(), defaults.inputCostPer1kTokens(), defaults.outputCostPer1kTokens(),
                defaults.maxResponseChars());
        ModelGateway gateway = new OpenAiCompatibleModelGateway(restClientBuilder, effective,
                sanitizer, metrics, objectMapper);
        externalGateways.put(owner, new CachedGateway(resolved.version(), gateway));
        return gateway;
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record CachedGateway(String version, ModelGateway gateway) {
    }
}
