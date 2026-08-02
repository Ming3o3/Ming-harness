package org.mingharness.model;

import org.mingharness.common.BusinessException;
import org.mingharness.model.api.ModelProviderConfigView;
import org.mingharness.model.api.UpdateModelProviderConfigRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

/** 管理按用户隔离的模型供应商配置，并把密钥生命周期限制在后端。 */
@Service
public class ModelProviderConfigService {

    private static final int MAX_API_KEY_LENGTH = 1000;

    private final ModelProviderConfigRepository repository;
    private final ModelSecretCipher secretCipher;
    private final ModelConfig defaultConfig;

    public ModelProviderConfigService(ModelProviderConfigRepository repository,
                                      ModelSecretCipher secretCipher,
                                      ModelConfig defaultConfig) {
        this.repository = repository;
        this.secretCipher = secretCipher;
        this.defaultConfig = defaultConfig;
    }

    @Transactional(readOnly = true)
    public ModelProviderConfigView view(String tenantId, String userId) {
        return repository.findByTenantIdAndUserId(tenantId, userId)
                .map(this::toUserView)
                .orElseGet(this::toDefaultView);
    }

    @Transactional
    public ModelProviderConfigView update(String tenantId, String userId,
                                          UpdateModelProviderConfigRequest request) {
        if (request == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "MODEL_CONFIG_REQUIRED", "模型配置不能为空");
        }
        boolean enabled = Boolean.TRUE.equals(request.enabled());
        boolean blankBaseUrl = request.baseUrl() == null || request.baseUrl().isBlank();
        if (enabled && blankBaseUrl) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "MODEL_BASE_URL_REQUIRED", "模型 API 地址不能为空");
        }
        String baseUrl = blankBaseUrl ? defaultConfig.baseUrl() : normalizeBaseUrl(request.baseUrl());
        boolean blankModelName = request.modelName() == null || request.modelName().isBlank();
        if (enabled && blankModelName) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "MODEL_NAME_REQUIRED", "启用外部模型时必须填写模型名称");
        }
        String modelName = blankModelName ? defaultConfig.name() : normalizeModelName(request.modelName());

        String suppliedApiKey = request.apiKey() == null ? "" : request.apiKey().trim();
        if (suppliedApiKey.length() > MAX_API_KEY_LENGTH) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "MODEL_API_KEY_TOO_LONG", "模型 API Key 不能超过 1000 个字符");
        }
        ModelProviderConfig existing = repository.findByTenantIdAndUserId(tenantId, userId).orElse(null);
        String ciphertext = existing == null ? null : existing.getApiKeyCiphertext();
        String hint = existing == null ? null : existing.getApiKeyHint();
        if (Boolean.TRUE.equals(request.clearApiKey())) {
            ciphertext = null;
            hint = null;
        } else if (!suppliedApiKey.isBlank()) {
            ciphertext = secretCipher.encrypt(suppliedApiKey);
            hint = maskApiKey(suppliedApiKey);
        }

        if (existing == null) {
            existing = new ModelProviderConfig(tenantId, userId, enabled, baseUrl, modelName, ciphertext, hint);
        } else {
            existing.update(enabled, baseUrl, modelName, ciphertext, hint);
        }
        return toUserView(repository.save(existing));
    }

    @Transactional
    public ModelProviderConfigView reset(String tenantId, String userId) {
        repository.findByTenantIdAndUserId(tenantId, userId).ifPresent(repository::delete);
        return toDefaultView();
    }

    /** Worker 按 Run 的身份解析当前有效配置；不向控制器返回密钥。 */
    @Transactional(readOnly = true)
    public ResolvedModelConfig resolve(String tenantId, String userId) {
        Optional<ModelProviderConfig> configured = repository.findByTenantIdAndUserId(tenantId, userId);
        if (configured.isEmpty()) {
            return new ResolvedModelConfig(defaultConfig.enabled(), defaultConfig.baseUrl(),
                    defaultConfig.apiKey(), defaultConfig.name(), "environment");
        }
        ModelProviderConfig value = configured.get();
        return new ResolvedModelConfig(value.isEnabled(), value.getBaseUrl(),
                secretCipher.decrypt(value.getApiKeyCiphertext()), value.getModelName(),
                value.getUpdatedAt().toString());
    }

    public String effectiveModelName(String tenantId, String userId) {
        ResolvedModelConfig resolved = resolve(tenantId, userId);
        return resolved.enabled() ? resolved.modelName() : "demo-model";
    }

    private ModelProviderConfigView toUserView(ModelProviderConfig value) {
        return new ModelProviderConfigView(true, value.isEnabled(), "user", value.getBaseUrl(),
                value.getModelName(), value.getApiKeyCiphertext() != null,
                value.getApiKeyHint(), value.getUpdatedAt());
    }

    private ModelProviderConfigView toDefaultView() {
        boolean keyConfigured = defaultConfig.apiKey() != null && !defaultConfig.apiKey().isBlank();
        return new ModelProviderConfigView(false, defaultConfig.enabled(), "environment",
                defaultConfig.baseUrl(), defaultConfig.name(), keyConfigured,
                keyConfigured ? "环境变量已配置" : null, null);
    }

    private String normalizeBaseUrl(String value) {
        String normalized = value == null ? "" : value.trim().replaceAll("/+$", "");
        if (normalized.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "MODEL_BASE_URL_REQUIRED", "模型 API 地址不能为空");
        }
        try {
            URI uri = new URI(normalized);
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                throw invalidBaseUrl();
            }
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null) {
                throw invalidBaseUrl();
            }
        } catch (URISyntaxException exception) {
            throw invalidBaseUrl();
        }
        return normalized;
    }

    private String normalizeModelName(String value) {
        return value == null ? "" : value.trim();
    }

    private BusinessException invalidBaseUrl() {
        return new BusinessException(HttpStatus.BAD_REQUEST, "MODEL_BASE_URL_INVALID",
                "模型 API 地址必须是合法的 http 或 https 地址，不能包含账号、片段或凭证");
    }

    private String maskApiKey(String value) {
        if (value.length() <= 4) return "••••";
        return "••••" + value.substring(value.length() - 4);
    }

    public record ResolvedModelConfig(boolean enabled, String baseUrl, String apiKey,
                                      String modelName, String version) {
    }
}
