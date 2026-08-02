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
    private final ModelProviderConfigSnapshotRepository snapshotRepository;
    private final ModelSecretCipher secretCipher;
    private final ModelConfig defaultConfig;

    public ModelProviderConfigService(ModelProviderConfigRepository repository,
                                      ModelProviderConfigSnapshotRepository snapshotRepository,
                                      ModelSecretCipher secretCipher,
                                      ModelConfig defaultConfig) {
        this.repository = repository;
        this.snapshotRepository = snapshotRepository;
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
        NormalizedModelConfig normalized = normalize(request);
        ModelProviderConfig existing = repository.findByTenantIdAndUserId(tenantId, userId).orElse(null);
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
            existing = new ModelProviderConfig(tenantId, userId, normalized.enabled(), normalized.baseUrl(),
                    normalized.modelName(), ciphertext, hint);
        } else {
            existing.update(normalized.enabled(), normalized.baseUrl(), normalized.modelName(), ciphertext, hint);
        }
        ModelProviderConfig saved = repository.save(existing);
        ModelProviderConfigSnapshot snapshot = snapshotRepository.save(new ModelProviderConfigSnapshot(saved));
        saved.attachSnapshot(snapshot.getId());
        return toUserView(repository.save(saved));
    }

    /** 原子捕获创建 Run 时的模型配置，避免模型名和连接配置在并发更新中错配。 */
    @Transactional
    public CapturedModelConfig captureForRun(String tenantId, String userId) {
        ModelProviderConfig existing = repository.findByTenantIdAndUserId(tenantId, userId).orElse(null);
        if (existing == null) {
            return new CapturedModelConfig(null, new ResolvedModelConfig(defaultConfig.enabled(), defaultConfig.baseUrl(),
                    defaultConfig.apiKey(), defaultConfig.name(), "environment"));
        }
        if (existing.getActiveSnapshotId() != null && !existing.getActiveSnapshotId().isBlank()) {
            Optional<ModelProviderConfigSnapshot> active = snapshotRepository.findByIdAndTenantIdAndUserId(
                    existing.getActiveSnapshotId(), tenantId, userId);
            if (active.isPresent()) {
                return new CapturedModelConfig(active.get().getId(), toResolvedSnapshot(active.get()));
            }
        }
        ModelProviderConfigSnapshot snapshot = snapshotRepository.save(new ModelProviderConfigSnapshot(existing));
        existing.attachSnapshot(snapshot.getId());
        repository.save(existing);
        return new CapturedModelConfig(snapshot.getId(), toResolvedSnapshot(snapshot));
    }

    /** 预览未保存的配置，供连接测试使用；不会写入数据库。 */
    @Transactional(readOnly = true)
    public ResolvedModelConfig preview(String tenantId, String userId,
                                       UpdateModelProviderConfigRequest request) {
        NormalizedModelConfig normalized = normalize(request);
        ModelProviderConfig existing = repository.findByTenantIdAndUserId(tenantId, userId).orElse(null);
        String apiKey = normalized.clearApiKey() ? "" : normalized.suppliedApiKey();
        if (!normalized.clearApiKey() && apiKey.isBlank()
                && existing != null && existing.getApiKeyCiphertext() != null) {
            apiKey = secretCipher.decrypt(existing.getApiKeyCiphertext());
        }
        return new ResolvedModelConfig(normalized.enabled(), normalized.baseUrl(), apiKey,
                normalized.modelName(), "preview");
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

    /** 按 Run 创建时固化的快照解析模型配置；找不到快照时兼容旧 Run 的当前配置行为。 */
    @Transactional(readOnly = true)
    public ResolvedModelConfig resolveForRun(String tenantId, String userId, String snapshotId) {
        if (snapshotId == null || snapshotId.isBlank()) {
            return resolve(tenantId, userId);
        }
        return snapshotRepository.findByIdAndTenantIdAndUserId(snapshotId, tenantId, userId)
                .map(this::toResolvedSnapshot)
                .orElseGet(() -> resolve(tenantId, userId));
    }

    private ResolvedModelConfig toResolvedSnapshot(ModelProviderConfigSnapshot snapshot) {
        return new ResolvedModelConfig(snapshot.isEnabled(), snapshot.getBaseUrl(),
                secretCipher.decrypt(snapshot.getApiKeyCiphertext()), snapshot.getModelName(),
                "snapshot:" + snapshot.getId());
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

    private NormalizedModelConfig normalize(UpdateModelProviderConfigRequest request) {
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
        return new NormalizedModelConfig(enabled, baseUrl, modelName, suppliedApiKey,
                Boolean.TRUE.equals(request.clearApiKey()));
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

    public record CapturedModelConfig(String snapshotId, ResolvedModelConfig config) {
    }

    private record NormalizedModelConfig(boolean enabled, String baseUrl, String modelName,
                                         String suppliedApiKey, boolean clearApiKey) {
    }
}
