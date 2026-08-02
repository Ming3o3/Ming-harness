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
 * 按 Run 所属用户动态选择演示模型或用户配置的 OpenAI 兼容供应商。
 * 默认配置仍来自环境变量，控制台保存的配置只影响该租户/用户的新执行。
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
        ModelProviderConfigService.ResolvedModelConfig resolved = configService.resolve(
                valueOrDefault(request == null ? null : request.tenantId(), "tenant-demo"),
                valueOrDefault(request == null ? null : request.userId(), "operator"));
        if (!resolved.enabled()) return demoGateway;

        String owner = valueOrDefault(request == null ? null : request.tenantId(), "tenant-demo")
                + "\u0000" + valueOrDefault(request == null ? null : request.userId(), "operator");
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
