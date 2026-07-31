package org.mingharness.model;

import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.observability.HarnessMetrics;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容模型网关，负责供应商级重试、熔断、备用路由、响应契约校验和成本解析。
 * Runtime 仍通过 ModelGateway 接口调用，因此不会改变现有 Run/Step 执行协议。
 */
@Component
@ConditionalOnProperty(prefix = "harness.model", name = "enabled", havingValue = "true")
public class OpenAiCompatibleModelGateway implements ModelGateway {

    private final Provider primary;
    private final Provider fallback;
    private final ModelConfig config;
    private final SensitiveDataSanitizer sanitizer;
    private final HarnessMetrics metrics;

    public OpenAiCompatibleModelGateway(RestClient.Builder restClientBuilder,
                                        ModelConfig config,
                                        SensitiveDataSanitizer sanitizer,
                                        HarnessMetrics metrics) {
        this.config = config;
        this.sanitizer = sanitizer;
        this.metrics = metrics;
        this.primary = provider(restClientBuilder, "primary", config.baseUrl(), config.apiKey(), config.name());
        this.fallback = config.fallbackEnabled()
                ? provider(restClientBuilder, "fallback", config.fallbackBaseUrl(), config.fallbackApiKey(), config.fallbackName())
                : null;
    }

    @Override
    public ModelResponse complete(ModelRequest request) {
        if (request == null) {
            throw new ModelGatewayException("primary", false, "模型请求不能为空");
        }
        ModelGatewayException primaryFailure = null;
        try {
            return invokeWithPolicy(primary, request);
        } catch (ModelGatewayException exception) {
            primaryFailure = exception;
            // 只有临时依赖故障才切备用供应商，参数/响应契约错误直接暴露，避免静默改变语义。
            if (!exception.retryable() || fallback == null) {
                throw exception;
            }
            metrics.modelFallback();
        }
        try {
            return invokeWithPolicy(fallback, request);
        } catch (ModelGatewayException fallbackFailure) {
            throw new ModelGatewayException("fallback", fallbackFailure.retryable(),
                    "主模型和备用模型均调用失败: primary=" + safeMessage(primaryFailure)
                            + "; fallback=" + safeMessage(fallbackFailure), fallbackFailure);
        }
    }

    private ModelResponse invokeWithPolicy(Provider provider, ModelRequest request) {
        if (!provider.breaker().allowRequest()) {
            throw new ModelGatewayException(provider.name(), true, "模型供应商熔断中: " + provider.name());
        }
        ModelGatewayException lastFailure = null;
        for (int attempt = 1; attempt <= config.maxAttempts(); attempt++) {
            try {
                ModelResponse response = invokeOnce(provider, request);
                provider.breaker().recordSuccess();
                return response;
            } catch (ModelGatewayException exception) {
                lastFailure = exception;
                provider.breaker().recordFailure();
                if (!exception.retryable() || attempt >= config.maxAttempts()
                        || !provider.breaker().allowRequest()) {
                    metrics.modelFailed();
                    throw exception;
                }
                metrics.modelRetry();
                sleepBeforeRetry(attempt);
            }
        }
        throw lastFailure == null
                ? new ModelGatewayException(provider.name(), true, "模型调用没有返回结果") : lastFailure;
    }

    @SuppressWarnings("unchecked")
    private ModelResponse invokeOnce(Provider provider, ModelRequest request) {
        Map<String, Object> body = Map.of(
                "model", request.model() == null || request.model().isBlank() ? provider.model() : request.model(),
                "messages", List.of(Map.of("role", "user", "content",
                        sanitizer.sanitize(request.input() == null ? "" : request.input()))),
                "temperature", 0.2
        );
        try {
            Map<String, Object> response = provider.client().post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            return parseResponse(provider, request, response);
        } catch (RestClientResponseException exception) {
            throw new ModelGatewayException(provider.name(), isRetryableStatus(exception.getStatusCode().value()),
                    "模型供应商返回 HTTP " + exception.getStatusCode().value(), exception);
        } catch (RestClientException exception) {
            throw new ModelGatewayException(provider.name(), true, "模型供应商网络调用失败", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private ModelResponse parseResponse(Provider provider, ModelRequest request, Map<String, Object> response) {
        if (response == null) {
            throw new ModelGatewayException(provider.name(), false, "模型响应为空");
        }
        Object choicesValue = response.get("choices");
        if (!(choicesValue instanceof List<?> choices) || choices.isEmpty()
                || !(choices.get(0) instanceof Map<?, ?> firstChoice)) {
            throw new ModelGatewayException(provider.name(), false, "模型响应缺少有效 choices");
        }
        Object messageValue = firstChoice.get("message");
        if (!(messageValue instanceof Map<?, ?> message)) {
            throw new ModelGatewayException(provider.name(), false, "模型响应缺少 message");
        }
        Object contentValue = message.get("content");
        if (!(contentValue instanceof String content) || content.isBlank()) {
            throw new ModelGatewayException(provider.name(), false, "模型响应 content 为空或格式不支持");
        }
        if (content.length() > config.maxResponseChars()) {
            throw new ModelGatewayException(provider.name(), false, "模型响应超过字符上限");
        }
        Usage usage = usage(response.get("usage"));
        String responseModel = response.get("model") instanceof String value && !value.isBlank()
                ? value : provider.model();
        BigDecimal cost = usageCost(usage.inputTokens(), usage.outputTokens());
        return new ModelResponse(sanitizer.sanitize(content), responseModel,
                request.promptVersion(), usage.inputTokens(), usage.outputTokens(), cost);
    }

    @SuppressWarnings("unchecked")
    private Usage usage(Object usageValue) {
        if (!(usageValue instanceof Map<?, ?> usage)) {
            return new Usage(0, 0);
        }
        int input = number(usage.get("prompt_tokens"), usage.get("input_tokens"));
        int output = number(usage.get("completion_tokens"), usage.get("output_tokens"));
        return new Usage(input, output);
    }

    private int number(Object primaryValue, Object fallbackValue) {
        Object value = primaryValue == null ? fallbackValue : primaryValue;
        if (value instanceof Number number) {
            return Math.max(0, Math.min(Integer.MAX_VALUE, number.intValue()));
        }
        if (value instanceof String text) {
            try {
                return Math.max(0, Integer.parseInt(text));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private BigDecimal usageCost(int inputTokens, int outputTokens) {
        BigDecimal inputCost = config.inputCostPer1kTokens()
                .multiply(BigDecimal.valueOf(inputTokens))
                .divide(BigDecimal.valueOf(1_000), 8, RoundingMode.HALF_UP);
        BigDecimal outputCost = config.outputCostPer1kTokens()
                .multiply(BigDecimal.valueOf(outputTokens))
                .divide(BigDecimal.valueOf(1_000), 8, RoundingMode.HALF_UP);
        return inputCost.add(outputCost).stripTrailingZeros();
    }

    private Provider provider(RestClient.Builder builder, String name, String baseUrl, String apiKey,
                              String model) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("模型供应商 " + name + " 缺少 API Key");
        }
        return new Provider(name, model,
                builder.clone().baseUrl(baseUrl)
                        .defaultHeader("Authorization", "Bearer " + apiKey)
                        .build(),
                new ModelCircuitBreaker(config.circuitFailureThreshold(), config.circuitOpenMs()));
    }

    private boolean isRetryableStatus(int status) {
        return status == 408 || status == 425 || status == 429 || status >= 500;
    }

    private void sleepBeforeRetry(int attempt) {
        long delay = Math.min(10_000, config.retryBackoffMs() * (1L << Math.min(attempt - 1, 10)));
        if (delay <= 0) {
            return;
        }
        try {
            Thread.sleep(delay);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ModelGatewayException("model", true, "模型重试等待被中断", exception);
        }
    }

    private String safeMessage(ModelGatewayException exception) {
        return exception == null || exception.getMessage() == null ? "unknown" : sanitizer.sanitize(exception.getMessage());
    }

    private record Provider(String name, String model, RestClient client, ModelCircuitBreaker breaker) {
    }

    private record Usage(int inputTokens, int outputTokens) {
    }
}
