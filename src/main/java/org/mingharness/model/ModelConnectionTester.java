package org.mingharness.model;

import org.mingharness.model.api.ModelConnectionTestView;
import org.mingharness.observability.HarnessMetrics;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.runtime.application.BoundedExecutor;
import org.mingharness.runtime.application.ExecutionTimeoutException;
import org.mingharness.runtime.application.RuntimeLimits;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/** 使用当前未保存的模型配置发起一次最小请求，帮助用户在发送 Run 前发现连接问题。 */
@Component
public class ModelConnectionTester {

    private final ModelConfig defaults;
    private final RestClient.Builder restClientBuilder;
    private final SensitiveDataSanitizer sanitizer;
    private final HarnessMetrics metrics;
    private final ObjectMapper objectMapper;
    private final BoundedExecutor boundedExecutor;
    private final int timeoutMs;

    public ModelConnectionTester(ModelConfig defaults,
                                 RestClient.Builder restClientBuilder,
                                 SensitiveDataSanitizer sanitizer,
                                 HarnessMetrics metrics,
                                 ObjectMapper objectMapper,
                                 BoundedExecutor boundedExecutor,
                                 RuntimeLimits runtimeLimits) {
        this.defaults = defaults;
        this.restClientBuilder = restClientBuilder;
        this.sanitizer = sanitizer;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
        this.boundedExecutor = boundedExecutor;
        this.timeoutMs = Math.max(1_000, Math.min(10_000, runtimeLimits.modelTimeoutMs()));
    }

    public ModelConnectionTestView test(ModelProviderConfigService.ResolvedModelConfig candidate,
                                        String tenantId, String userId) {
        if (!candidate.enabled()) {
            return new ModelConnectionTestView(true, "DISABLED",
                    "当前已关闭外部模型，未发起网络请求。", candidate.modelName(), 0);
        }
        long startedAt = System.nanoTime();
        try {
            ModelConfig config = new ModelConfig(true, candidate.baseUrl(), candidate.apiKey(),
                    candidate.modelName(), null, null, null,
                    defaults.maxAttempts(), defaults.retryBackoffMs(), defaults.circuitFailureThreshold(),
                    defaults.circuitOpenMs(), defaults.inputCostPer1kTokens(), defaults.outputCostPer1kTokens(),
                    defaults.maxResponseChars());
            ModelGateway gateway = new OpenAiCompatibleModelGateway(restClientBuilder, config,
                    sanitizer, metrics, objectMapper);
            ModelResponse response = boundedExecutor.execute("模型连接测试", timeoutMs, () -> gateway.complete(
                    new ModelRequest("请只回复 OK，不要调用工具。", candidate.modelName(),
                            "model-connection-test", List.of(), List.of(), tenantId, userId)));
            return new ModelConnectionTestView(true, "CONNECTED", "连接成功，模型已返回响应。",
                    response.model(), elapsedMs(startedAt));
        } catch (ExecutionTimeoutException exception) {
            return new ModelConnectionTestView(false, "TIMEOUT",
                    "连接测试超时，请检查模型地址、网络或服务状态。", candidate.modelName(),
                    elapsedMs(startedAt), ModelErrorCode.TIMEOUT.name());
        } catch (ModelGatewayException exception) {
            return new ModelConnectionTestView(false, "FAILED",
                    "连接失败：" + safeMessage(exception), candidate.modelName(), elapsedMs(startedAt),
                    exception.code().name());
        } catch (RuntimeException exception) {
            return new ModelConnectionTestView(false, "FAILED",
                    "连接失败：模型供应商暂时不可用。", candidate.modelName(), elapsedMs(startedAt),
                    ModelErrorCode.PROVIDER_UNAVAILABLE.name());
        }
    }

    private String safeMessage(ModelGatewayException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) return "模型供应商请求失败。";
        return sanitizer.sanitize(message);
    }

    private long elapsedMs(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
    }
}
