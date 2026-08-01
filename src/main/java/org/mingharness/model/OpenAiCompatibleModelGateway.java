package org.mingharness.model;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.observability.HarnessMetrics;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private final ObjectMapper objectMapper;

    OpenAiCompatibleModelGateway(RestClient.Builder restClientBuilder,
                                 ModelConfig config,
                                 SensitiveDataSanitizer sanitizer,
                                 HarnessMetrics metrics) {
        this(restClientBuilder, config, sanitizer, metrics, new ObjectMapper());
    }

    @Autowired
    public OpenAiCompatibleModelGateway(RestClient.Builder restClientBuilder,
                                        ModelConfig config,
                                        SensitiveDataSanitizer sanitizer,
                                        HarnessMetrics metrics,
                                        ObjectMapper objectMapper) {
        this.config = config;
        this.sanitizer = sanitizer;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
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
        PreparedTools preparedTools = prepareTools(request.tools());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.model() == null || request.model().isBlank() ? provider.model() : request.model());
        body.put("messages", List.of(Map.of("role", "user", "content",
                sanitizer.sanitize(request.input() == null ? "" : request.input()))));
        body.put("temperature", 0.2);
        if (!preparedTools.definitions().isEmpty()) {
            body.put("tools", preparedTools.definitions());
            body.put("tool_choice", "auto");
        }
        try {
            Map<String, Object> response = provider.client().post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            return parseResponse(provider, request, response, preparedTools);
        } catch (RestClientResponseException exception) {
            throw new ModelGatewayException(provider.name(), isRetryableStatus(exception.getStatusCode().value()),
                    "模型供应商返回 HTTP " + exception.getStatusCode().value(), exception);
        } catch (RestClientException exception) {
            throw new ModelGatewayException(provider.name(), true, "模型供应商网络调用失败", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private ModelResponse parseResponse(Provider provider, ModelRequest request, Map<String, Object> response,
                                        PreparedTools preparedTools) {
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
        String content = message.get("content") instanceof String value ? value : "";
        List<ModelToolCall> toolCalls = parseToolCalls(message.get("tool_calls"), provider.name(), preparedTools);
        if (content.isBlank() && toolCalls.isEmpty()) {
            throw new ModelGatewayException(provider.name(), false, "模型响应缺少 content 或 tool_calls");
        }
        if (content.length() > config.maxResponseChars()) {
            throw new ModelGatewayException(provider.name(), false, "模型响应超过字符上限");
        }
        Usage usage = usage(response.get("usage"));
        String responseModel = response.get("model") instanceof String value && !value.isBlank()
                ? value : provider.model();
        BigDecimal cost = usageCost(usage.inputTokens(), usage.outputTokens());
        return new ModelResponse(sanitizer.sanitize(content), responseModel,
                request.promptVersion(), usage.inputTokens(), usage.outputTokens(), cost, toolCalls);
    }

    /** 严格解析供应商 tool_calls，未知结构直接失败，避免把未经校验的参数交给工具。 */
    private List<ModelToolCall> parseToolCalls(Object rawValue, String providerName,
                                               PreparedTools preparedTools) {
        if (rawValue == null) return List.of();
        if (!(rawValue instanceof List<?> values)) {
            throw new ModelGatewayException(providerName, false, "模型响应 tool_calls 格式无效");
        }
        List<ModelToolCall> calls = new ArrayList<>();
        for (Object value : values) {
            if (!(value instanceof Map<?, ?> call)) {
                throw new ModelGatewayException(providerName, false, "模型响应 tool_call 项格式无效");
            }
            String id = stringValue(call.get("id"));
            Object functionValue = call.get("function");
            if (id == null || !(functionValue instanceof Map<?, ?> function)) {
                throw new ModelGatewayException(providerName, false, "模型响应 tool_call 缺少 id 或 function");
            }
            String providerToolName = stringValue(function.get("name"));
            String arguments = stringValue(function.get("arguments"));
            if (providerToolName == null || arguments == null
                    || providerToolName.isBlank() || arguments.isBlank()) {
                throw new ModelGatewayException(providerName, false, "模型响应 tool_call 缺少工具名称或参数");
            }
            if (arguments.length() > config.maxResponseChars()) {
                throw new ModelGatewayException(providerName, false, "模型 tool_call 参数超过字符上限");
            }
            String internalToolName = preparedTools.internalName(providerToolName);
            if (internalToolName == null) {
                throw new ModelGatewayException(providerName, false,
                        "模型响应 tool_call 使用未声明的工具: " + providerToolName);
            }
            calls.add(new ModelToolCall(id, internalToolName,
                    preparedTools.normalizeArguments(providerToolName, arguments, objectMapper, providerName)));
        }
        return List.copyOf(calls);
    }

    private PreparedTools prepareTools(List<ModelToolDefinition> tools) {
        if (tools == null || tools.isEmpty()) {
            return PreparedTools.empty();
        }
        List<Map<String, Object>> definitions = new ArrayList<>();
        Map<String, String> providerToInternal = new LinkedHashMap<>();
        Map<String, Boolean> legacyTextTools = new LinkedHashMap<>();
        Set<String> usedProviderNames = new HashSet<>();
        for (ModelToolDefinition tool : tools) {
            String internalName = tool.name();
            if (internalName == null || internalName.isBlank()) {
                throw new ModelGatewayException("model", false, "模型工具定义缺少工具名称");
            }
            String providerToolName = providerToolName(internalName, usedProviderNames);
            boolean legacyText = isLegacyTextSchema(tool.inputSchema());
            Map<String, Object> parameters = legacyText
                    ? legacyTextParameters() : tool.inputSchema();
            definitions.add(Map.of(
                    "type", "function",
                    "function", Map.of(
                            "name", providerToolName,
                            "description", tool.description() == null ? "" : tool.description(),
                            "parameters", parameters
                    )
            ));
            providerToInternal.put(providerToolName, internalName);
            legacyTextTools.put(providerToolName, legacyText);
        }
        return new PreparedTools(List.copyOf(definitions), Map.copyOf(providerToInternal),
                Map.copyOf(legacyTextTools), tools.stream().map(ModelToolDefinition::name).collect(java.util.stream.Collectors.toUnmodifiableSet()));
    }

    private String providerToolName(String internalName, Set<String> usedNames) {
        StringBuilder normalized = new StringBuilder();
        for (int index = 0; index < internalName.length(); index++) {
            char character = internalName.charAt(index);
            normalized.append(isProviderNameCharacter(character) ? character : '_');
        }
        String base = normalized.isEmpty() ? "tool" : normalized.toString();
        base = base.substring(0, Math.min(base.length(), 64));
        String candidate = base;
        int suffix = 2;
        while (!usedNames.add(candidate)) {
            String suffixText = "_" + suffix++;
            int prefixLength = Math.max(1, 64 - suffixText.length());
            candidate = base.substring(0, Math.min(base.length(), prefixLength)) + suffixText;
        }
        return candidate;
    }

    private boolean isProviderNameCharacter(char character) {
        return character >= 'A' && character <= 'Z'
                || character >= 'a' && character <= 'z'
                || character >= '0' && character <= '9'
                || character == '_' || character == '-';
    }

    private boolean isLegacyTextSchema(Map<String, Object> schema) {
        return schema != null && "string".equals(schema.get("type"));
    }

    private Map<String, Object> legacyTextParameters() {
        return Map.of(
                "type", "object",
                "required", List.of("input"),
                "additionalProperties", false,
                "properties", Map.of("input", Map.of("type", "string", "minLength", 1))
        );
    }

    private String stringValue(Object value) {
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    private record PreparedTools(
            List<Map<String, Object>> definitions,
            Map<String, String> providerToInternal,
            Map<String, Boolean> legacyTextTools,
            Set<String> internalNames
    ) {

        private static PreparedTools empty() {
            return new PreparedTools(List.of(), Map.of(), Map.of(), Set.of());
        }

        private String internalName(String providerName) {
            String mapped = providerToInternal.get(providerName);
            return mapped != null ? mapped : internalNames.contains(providerName) ? providerName : null;
        }

        private String normalizeArguments(String providerName, String arguments,
                                          ObjectMapper objectMapper, String modelProvider) {
            if (!Boolean.TRUE.equals(legacyTextTools.get(providerName))) {
                return arguments;
            }
            try {
                JsonNode value = objectMapper.reader().readTree(arguments);
                if (value != null && value.isTextual()) {
                    return value.asText();
                }
                if (value == null || !value.isObject() || value.size() != 1
                        || value.get("input") == null || !value.get("input").isTextual()) {
                    throw new ModelGatewayException(modelProvider, false,
                            "模型响应 legacy tool_call 参数必须是 {input: string}");
                }
                return value.get("input").asText();
            } catch (JacksonException exception) {
                throw new ModelGatewayException(modelProvider, false,
                        "模型响应 legacy tool_call 参数不是有效 JSON", exception);
            }
        }
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
