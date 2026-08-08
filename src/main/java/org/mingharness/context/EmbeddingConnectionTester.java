package org.mingharness.context;

import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.context.api.EmbeddingConnectionTestView;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 使用未保存配置调用一次 /embeddings，返回脱敏的连通性结果。 */
@Component
public class EmbeddingConnectionTester {

    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;
    private final SensitiveDataSanitizer sanitizer;

    public EmbeddingConnectionTester(RestClient.Builder restClientBuilder,
                                     ObjectMapper objectMapper,
                                     SensitiveDataSanitizer sanitizer) {
        this.restClientBuilder = restClientBuilder;
        this.objectMapper = objectMapper;
        this.sanitizer = sanitizer;
    }

    public EmbeddingConnectionTestView test(EmbeddingProviderConfigService.ResolvedEmbeddingConfig config) {
        if (!config.enabled()) {
            return new EmbeddingConnectionTestView(false, "DISABLED", "请先启用外部 Embedding", config.model(), 0, 0,
                    "EMBEDDING_DISABLED");
        }
        long started = System.nanoTime();
        try {
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout((int) config.timeoutMs());
            requestFactory.setReadTimeout((int) config.timeoutMs());
            RestClient.Builder clientBuilder = restClientBuilder.clone().baseUrl(config.baseUrl())
                    .requestFactory(requestFactory);
            if (config.apiKey() != null && !config.apiKey().isBlank()) {
                clientBuilder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + config.apiKey());
            }
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("model", config.model());
            request.put("input", List.of("Ming Harness embedding connection test"));
            // 连接测试必须复用正式调用的维度参数，否则测试通过后索引仍可能失败。
            request.put("dimensions", config.dimension());
            String body = clientBuilder.build().post().uri("/embeddings")
                    .contentType(MediaType.APPLICATION_JSON).body(request).retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(body);
            JsonNode data = root == null ? null : root.get("data");
            JsonNode vector = data != null && data.isArray() && !data.isEmpty()
                    ? data.get(0).get("embedding") : null;
            if (vector == null || !vector.isArray() || vector.size() != config.dimension()) {
                return failed(config, started, "Embedding 返回维度不匹配，期望 " + config.dimension(),
                        "EMBEDDING_DIMENSION_MISMATCH");
            }
            return new EmbeddingConnectionTestView(true, "SUCCEEDED", "Embedding 连接测试成功", config.model(),
                    vector.size(), elapsedMs(started), null);
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            String detail = sanitizer.sanitize(exception.getResponseBodyAsString());
            if (detail.length() > 500) detail = detail.substring(0, 500) + "...";
            return failed(config, started, detail.isBlank() ? "Embedding 服务返回 HTTP " + status
                    : "Embedding 服务返回 HTTP " + status + ": " + detail, "EMBEDDING_HTTP_" + status);
        } catch (JacksonException exception) {
            return failed(config, started, "Embedding 响应不是有效 JSON", "EMBEDDING_INVALID_RESPONSE");
        } catch (RestClientException exception) {
            return failed(config, started, "Embedding 服务连接失败", "EMBEDDING_CONNECTION_FAILED");
        } catch (RuntimeException exception) {
            return failed(config, started, "Embedding 连接测试失败", "EMBEDDING_TEST_FAILED");
        }
    }

    private EmbeddingConnectionTestView failed(EmbeddingProviderConfigService.ResolvedEmbeddingConfig config,
                                               long started, String message, String code) {
        return new EmbeddingConnectionTestView(false, "FAILED", message, config.model(), 0,
                elapsedMs(started), code);
    }

    private long elapsedMs(long started) {
        return Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
    }
}
