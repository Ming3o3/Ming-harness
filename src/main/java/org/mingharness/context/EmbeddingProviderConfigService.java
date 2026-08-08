package org.mingharness.context;

import org.mingharness.common.BusinessException;
import org.mingharness.config.EmbeddingProperties;
import org.mingharness.context.api.EmbeddingProviderConfigRequest;
import org.mingharness.context.api.EmbeddingProviderConfigView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** 管理组织级 Embedding 供应商配置，并将环境变量作为未覆盖时的默认值。 */
@Service
public class EmbeddingProviderConfigService {

    private static final int FIXED_VECTOR_DIMENSION = 1536;
    private static final int MAX_API_KEY_LENGTH = 1000;

    private final EmbeddingProviderConfigRepository repository;
    private final EmbeddingSecretCipher secretCipher;
    private final EmbeddingProperties defaults;
    private final ContextEmbeddingInvalidator invalidator;
    private final ContextEmbeddingStore embeddingStore;

    public EmbeddingProviderConfigService(EmbeddingProviderConfigRepository repository,
                                          EmbeddingSecretCipher secretCipher,
                                          EmbeddingProperties defaults,
                                          ContextEmbeddingInvalidator invalidator,
                                          ContextEmbeddingStore embeddingStore) {
        this.repository = repository;
        this.secretCipher = secretCipher;
        this.defaults = defaults;
        this.invalidator = invalidator;
        this.embeddingStore = embeddingStore;
    }

    @Transactional(readOnly = true)
    public EmbeddingProviderConfigView view(String tenantId) {
        return repository.findByTenantId(tenantId)
                .map(this::toUserView)
                .orElseGet(this::toDefaultView);
    }

    @Transactional(readOnly = true)
    public ResolvedEmbeddingConfig resolve(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) return environmentConfig();
        return repository.findByTenantId(tenantId)
                .map(value -> new ResolvedEmbeddingConfig(value.isEnabled(), value.getBaseUrl(),
                        decrypt(value.getApiKeyCiphertext()), value.getModelName(), value.getModelVersion(),
                        value.getDimension(), defaults.batchSize(), defaults.maxInputChars(), defaults.maxInputTokens(),
                        defaults.maxResponseChars(), defaults.maxAttempts(), defaults.retryBackoffMs(), defaults.timeoutMs(),
                        "tenant:" + value.getId(), value.getUpdatedAt()))
                .orElseGet(this::environmentConfig);
    }

    @Transactional
    public EmbeddingProviderConfigView update(String tenantId, EmbeddingProviderConfigRequest request) {
        Normalized normalized = normalize(request);
        EmbeddingProviderConfig existing = repository.findByTenantId(tenantId).orElse(null);
        String ciphertext = existing == null ? null : existing.getApiKeyCiphertext();
        String hint = existing == null ? null : existing.getApiKeyHint();
        if (normalized.clearApiKey()) {
            ciphertext = null;
            hint = null;
        } else if (!normalized.suppliedApiKey().isBlank()) {
            ciphertext = secretCipher.encrypt(normalized.suppliedApiKey());
            hint = maskApiKey(normalized.suppliedApiKey());
        }
        if (existing == null) {
            existing = new EmbeddingProviderConfig(tenantId, normalized.enabled(), normalized.baseUrl(),
                    normalized.model(), normalized.modelVersion(), normalized.dimension(), ciphertext, hint);
        } else {
            existing.update(normalized.enabled(), normalized.baseUrl(), normalized.model(),
                    normalized.modelVersion(), normalized.dimension(), ciphertext, hint);
        }
        EmbeddingProviderConfig saved = repository.save(existing);
        // 向量空间或供应商发生变化时，旧 chunk 必须重新索引，不能被新查询误用。
        invalidator.invalidateTenant(tenantId);
        return toUserView(saved);
    }

    /** 预览未保存配置，供连接测试使用；不会写入数据库。 */
    @Transactional(readOnly = true)
    public ResolvedEmbeddingConfig preview(String tenantId, EmbeddingProviderConfigRequest request) {
        Normalized normalized = normalize(request);
        EmbeddingProviderConfig existing = repository.findByTenantId(tenantId).orElse(null);
        String apiKey = normalized.clearApiKey() ? "" : normalized.suppliedApiKey();
        if (apiKey.isBlank() && existing != null && existing.getApiKeyCiphertext() != null) {
            apiKey = secretCipher.decrypt(existing.getApiKeyCiphertext());
        }
        return resolved(normalized.enabled(), normalized.baseUrl(), apiKey, normalized.model(),
                normalized.modelVersion(), normalized.dimension(), "preview", null);
    }

    @Transactional
    public EmbeddingProviderConfigView reset(String tenantId) {
        repository.findByTenantId(tenantId).ifPresent(repository::delete);
        invalidator.invalidateTenant(tenantId);
        return toDefaultView();
    }

    public ResolvedEmbeddingConfig environmentConfig() {
        return resolved(defaults.enabled(), defaults.baseUrl(), defaults.apiKey(), defaults.model(),
                defaults.modelVersion(), defaults.dimension(), "environment", null);
    }

    private ResolvedEmbeddingConfig resolved(boolean enabled, String baseUrl, String apiKey, String model,
                                             String modelVersion, int dimension, String source,
                                             java.time.Instant updatedAt) {
        return new ResolvedEmbeddingConfig(enabled, baseUrl, apiKey, model, modelVersion, dimension,
                defaults.batchSize(), defaults.maxInputChars(), defaults.maxInputTokens(), defaults.maxResponseChars(),
                defaults.maxAttempts(), defaults.retryBackoffMs(), defaults.timeoutMs(), source, updatedAt);
    }

    private String decrypt(String ciphertext) {
        return ciphertext == null ? defaults.apiKey() : secretCipher.decrypt(ciphertext);
    }

    private EmbeddingProviderConfigView toUserView(EmbeddingProviderConfig value) {
        return new EmbeddingProviderConfigView(true, value.isEnabled(), value.isEnabled() && embeddingStore.supported(),
                "tenant", value.getBaseUrl(), value.getModelName(), value.getModelVersion(), value.getDimension(),
                value.getApiKeyCiphertext() != null, value.getApiKeyHint(), value.getUpdatedAt());
    }

    private EmbeddingProviderConfigView toDefaultView() {
        boolean configured = defaults.apiKey() != null && !defaults.apiKey().isBlank();
        return new EmbeddingProviderConfigView(false, defaults.enabled(), defaults.enabled() && embeddingStore.supported(),
                "environment", defaults.baseUrl(), defaults.model(), defaults.modelVersion(), defaults.dimension(),
                configured, configured ? "环境变量已配置" : null, null);
    }

    private Normalized normalize(EmbeddingProviderConfigRequest request) {
        if (request == null) throw bad("EMBEDDING_CONFIG_REQUIRED", "Embedding 配置不能为空");
        boolean enabled = Boolean.TRUE.equals(request.enabled());
        boolean blankUrl = request.baseUrl() == null || request.baseUrl().isBlank();
        String baseUrl = blankUrl ? defaults.baseUrl() : normalizeBaseUrl(request.baseUrl());
        if (enabled && blankUrl) throw bad("EMBEDDING_BASE_URL_REQUIRED", "启用 Embedding 时必须填写 API 地址");
        String model = request.modelName() == null || request.modelName().isBlank()
                ? defaults.model() : normalizeText(request.modelName(), "Embedding 模型不能为空");
        String version = request.modelVersion() == null || request.modelVersion().isBlank()
                ? defaults.modelVersion() : normalizeText(request.modelVersion(), "Embedding 模型版本不能为空");
        int dimension = request.dimension() == null ? defaults.dimension() : request.dimension();
        if (dimension != FIXED_VECTOR_DIMENSION) {
            throw bad("EMBEDDING_DIMENSION_UNSUPPORTED",
                    "当前 pgvector 索引固定为 " + FIXED_VECTOR_DIMENSION + " 维，不能配置为 " + dimension + " 维");
        }
        String apiKey = request.apiKey() == null ? "" : request.apiKey().trim();
        if (apiKey.length() > MAX_API_KEY_LENGTH) throw bad("EMBEDDING_API_KEY_TOO_LONG", "Embedding API Key 不能超过 1000 个字符");
        return new Normalized(enabled, baseUrl, model, version, dimension, apiKey,
                Boolean.TRUE.equals(request.clearApiKey()));
    }

    private String normalizeBaseUrl(String value) {
        String normalized = value.trim().replaceAll("/+$", "");
        try {
            URI uri = new URI(normalized);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null) {
                throw invalidBaseUrl();
            }
        } catch (URISyntaxException exception) {
            throw invalidBaseUrl();
        }
        return normalized;
    }

    private String normalizeText(String value, String message) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw bad("EMBEDDING_VALUE_REQUIRED", message);
        return normalized;
    }

    private BusinessException invalidBaseUrl() {
        return bad("EMBEDDING_BASE_URL_INVALID", "Embedding API 地址必须是合法的 http 或 https 地址，不能包含账号、片段或凭证");
    }

    private BusinessException bad(String code, String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, code, message);
    }

    private String maskApiKey(String value) {
        if (value.length() <= 4) return "••••";
        return "••••" + value.substring(value.length() - 4);
    }

    public record ResolvedEmbeddingConfig(boolean enabled, String baseUrl, String apiKey,
                                           String model, String modelVersion, int dimension,
                                           int batchSize, int maxInputChars, int maxInputTokens,
                                           int maxResponseChars, int maxAttempts, long retryBackoffMs,
                                           long timeoutMs, String source, java.time.Instant updatedAt) {

        /** 绑定供应商、模型、版本和配置更新时间，防止旧异步任务回写可检索向量。 */
        public String signature() {
            String value = source + "|" + model + "|" + modelVersion + "|"
                    + (updatedAt == null ? "environment" : updatedAt.toEpochMilli());
            try {
                byte[] digest = MessageDigest.getInstance("SHA-256")
                        .digest(value.getBytes(StandardCharsets.UTF_8));
                StringBuilder result = new StringBuilder(digest.length * 2);
                for (byte item : digest) result.append(String.format("%02x", item));
                return result.toString();
            } catch (Exception exception) {
                throw new IllegalStateException("JDK 缺少 SHA-256 算法", exception);
            }
        }
    }

    private record Normalized(boolean enabled, String baseUrl, String model, String modelVersion,
                              int dimension, String suppliedApiKey, boolean clearApiKey) {
    }
}
