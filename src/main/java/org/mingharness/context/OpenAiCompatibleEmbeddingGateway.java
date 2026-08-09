package org.mingharness.context;

import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.config.EmbeddingProperties;
import org.mingharness.observability.HarnessMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * OpenAI 兼容 embedding 网关。输入使用批量数组，响应按供应商返回的 index 重新排序，
 * 因此调用方可以稳定地把向量绑定回对应的 ContextChunk。
 */
@Component
public class OpenAiCompatibleEmbeddingGateway implements EmbeddingGateway {

    private final EmbeddingProperties defaultProperties;
    private final EmbeddingProviderConfigService configService;
    private final RestClient.Builder restClientBuilder;
    private final SensitiveDataSanitizer sanitizer;
    private final ObjectMapper objectMapper;
    private final HarnessMetrics metrics;

    public OpenAiCompatibleEmbeddingGateway(EmbeddingProperties properties,
                                            RestClient.Builder restClientBuilder,
                                            SensitiveDataSanitizer sanitizer,
                                            ObjectMapper objectMapper) {
        this(properties, null, restClientBuilder, sanitizer, objectMapper, null);
    }

    public OpenAiCompatibleEmbeddingGateway(EmbeddingProperties properties,
                                            RestClient.Builder restClientBuilder,
                                            SensitiveDataSanitizer sanitizer,
                                            ObjectMapper objectMapper,
                                            HarnessMetrics metrics) {
        this(properties, null, restClientBuilder, sanitizer, objectMapper, metrics);
    }

    @Autowired
    public OpenAiCompatibleEmbeddingGateway(EmbeddingProperties properties,
                                            EmbeddingProviderConfigService configService,
                                            RestClient.Builder restClientBuilder,
                                            SensitiveDataSanitizer sanitizer,
                                            ObjectMapper objectMapper,
                                            HarnessMetrics metrics) {
        this.defaultProperties = properties;
        this.configService = configService;
        this.restClientBuilder = restClientBuilder;
        this.sanitizer = sanitizer;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    @Override
    public boolean enabled() {
        return defaultProperties.enabled();
    }

    @Override
    public boolean enabled(String tenantId) {
        return resolve(tenantId).enabled();
    }

    @Override
    public List<EmbeddingVector> embed(List<String> inputs) {
        return embed(null, inputs);
    }

    @Override
    public List<EmbeddingVector> embed(String tenantId, List<String> inputs) {
        EmbeddingProviderConfigService.ResolvedEmbeddingConfig properties = resolve(tenantId);
        if (!properties.enabled() || inputs == null || inputs.isEmpty()) {
            return List.of();
        }
        if (inputs.size() > properties.batchSize()) {
            throw new EmbeddingGatewayException(false,
                    "embedding 批量大小超过限制: " + properties.batchSize());
        }
        List<String> normalized = inputs.stream().map(input -> normalizeInput(input, properties)).toList();
        return invokeWithRetry(normalized, properties);
    }

    private List<EmbeddingVector> invokeWithRetry(List<String> inputs,
                                                  EmbeddingProviderConfigService.ResolvedEmbeddingConfig properties) {
        EmbeddingGatewayException lastFailure = null;
        for (int attempt = 1; attempt <= properties.maxAttempts(); attempt++) {
            try {
                if (metrics != null) metrics.contextEmbeddingRequest();
                return invokeOnce(inputs, properties);
            } catch (EmbeddingGatewayException exception) {
                lastFailure = exception;
                if (!exception.retryable() || attempt >= properties.maxAttempts()) {
                    if (metrics != null) metrics.contextEmbeddingFailed();
                    throw exception;
                }
                if (metrics != null) metrics.contextEmbeddingRetry();
                sleepBeforeRetry(attempt, properties);
            }
        }
        throw lastFailure == null
                ? new EmbeddingGatewayException(true, "embedding 供应商没有返回结果")
                : lastFailure;
    }

    private List<EmbeddingVector> invokeOnce(List<String> inputs,
                                             EmbeddingProviderConfigService.ResolvedEmbeddingConfig properties) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", properties.model());
        request.put("input", inputs);
        // OpenAI 兼容服务使用 dimensions 选择输出维度；必须与 pgvector 列及本地校验一致。
        request.put("dimensions", properties.dimension());
        try {
            String body = buildClient(properties).post()
                    .uri("/embeddings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(String.class);
            if (body == null || body.length() > properties.maxResponseChars()) {
                throw new EmbeddingGatewayException(false, "embedding 响应超过字符上限");
            }
            return parse(body, inputs.size(), properties);
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            throw new EmbeddingGatewayException(isRetryableStatus(status),
                    providerErrorMessage(status, exception.getResponseBodyAsString()), status, exception);
        } catch (EmbeddingGatewayException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new EmbeddingGatewayException(true, "embedding 供应商网络调用失败", exception);
        }
    }

    private List<EmbeddingVector> parse(String body, int expectedCount,
                                        EmbeddingProviderConfigService.ResolvedEmbeddingConfig properties) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode data = root == null ? null : root.get("data");
            if (data == null || !data.isArray() || data.size() != expectedCount) {
                throw new EmbeddingGatewayException(false, "embedding 响应缺少完整 data 数组");
            }
            String responseModel = root.path("model").asText(properties.model());
            Map<Integer, EmbeddingVector> ordered = new TreeMap<>();
            for (int position = 0; position < data.size(); position++) {
                JsonNode item = data.get(position);
                int index = item.path("index").asInt(position);
                JsonNode values = item.get("embedding");
                if (values == null || !values.isArray() || values.size() != properties.dimension()) {
                    throw new EmbeddingGatewayException(false,
                            "embedding 维度不匹配，期望 " + properties.dimension());
                }
                List<Double> vector = new ArrayList<>(values.size());
                for (JsonNode value : values) {
                    if (!value.isNumber()) {
                        throw new EmbeddingGatewayException(false, "embedding 向量包含非数字值");
                    }
                    vector.add(value.asDouble());
                }
                if (ordered.put(index, new EmbeddingVector(responseModel, vector)) != null) {
                    throw new EmbeddingGatewayException(false, "embedding 响应包含重复 index");
                }
            }
            for (int index = 0; index < expectedCount; index++) {
                if (!ordered.containsKey(index)) {
                    throw new EmbeddingGatewayException(false, "embedding 响应 index 不连续");
                }
            }
            return List.copyOf(ordered.values());
        } catch (JacksonException exception) {
            throw new EmbeddingGatewayException(false, "embedding 响应不是有效 JSON", exception);
        }
    }

    private String normalizeInput(String input,
                                  EmbeddingProviderConfigService.ResolvedEmbeddingConfig properties) {
        if (input == null || input.isBlank()) {
            throw new EmbeddingGatewayException(false, "embedding 输入不能为空");
        }
        String normalized = sanitizer.sanitize(input).trim();
        if (normalized.length() > properties.maxInputChars()) {
            throw new EmbeddingGatewayException(false, "embedding 输入超过字符上限");
        }
        if (EmbeddingTokenEstimator.estimate(normalized) > properties.maxInputTokens()) {
            throw new EmbeddingGatewayException(false, "embedding 输入超过 token 上限");
        }
        return normalized;
    }

    private EmbeddingProviderConfigService.ResolvedEmbeddingConfig resolve(String tenantId) {
        return configService == null ? new EmbeddingProviderConfigService.ResolvedEmbeddingConfig(
                defaultProperties.enabled(), defaultProperties.baseUrl(), defaultProperties.apiKey(),
                defaultProperties.model(), defaultProperties.modelVersion(), defaultProperties.dimension(),
                defaultProperties.batchSize(), defaultProperties.maxInputChars(), defaultProperties.maxInputTokens(),
                defaultProperties.maxResponseChars(), defaultProperties.maxAttempts(), defaultProperties.retryBackoffMs(),
                defaultProperties.timeoutMs(), "environment", null) : configService.resolve(tenantId);
    }

    private RestClient buildClient(EmbeddingProviderConfigService.ResolvedEmbeddingConfig properties) {
        RestClient.Builder source = restClientBuilder.clone();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) properties.timeoutMs());
        requestFactory.setReadTimeout((int) properties.timeoutMs());
        source.baseUrl(properties.baseUrl()).requestFactory(requestFactory);
        if (properties.apiKey() != null && !properties.apiKey().isBlank()) {
            source.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey());
        }
        return source.build();
    }

    private boolean isRetryableStatus(int status) {
        return status == 408 || status == 425 || status == 429 || status >= 500;
    }

    private String providerErrorMessage(int status, String responseBody) {
        String detail = responseBody == null ? "" : sanitizer.sanitize(responseBody).trim();
        if (detail.length() > 2_000) detail = detail.substring(0, 2_000) + "...";
        return detail.isBlank() ? "embedding 供应商返回 HTTP " + status
                : "embedding 供应商返回 HTTP " + status + ": " + detail;
    }

    private void sleepBeforeRetry(int attempt,
                                  EmbeddingProviderConfigService.ResolvedEmbeddingConfig properties) {
        long delay = Math.min(10_000, properties.retryBackoffMs() * (1L << Math.min(attempt - 1, 10)));
        if (delay <= 0) return;
        try {
            Thread.sleep(delay);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new EmbeddingGatewayException(true, "embedding 重试等待被中断", exception);
        }
    }
}
