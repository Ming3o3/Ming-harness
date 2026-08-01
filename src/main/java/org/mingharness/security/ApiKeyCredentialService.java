package org.mingharness.security;

import org.mingharness.common.BusinessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * API Key 生命周期服务。
 *
 * <p>数据库凭证支持即时撤销和过期；配置中的 {@code HARNESS_API_KEYS} 保留为紧急引导和
 * 向后兼容入口，但不能通过数据库接口撤销，生产环境应逐步迁移到本服务创建的密钥。</p>
 */
@Service
public class ApiKeyCredentialService {

    private static final int TOKEN_BYTES = 32;
    private static final int MAX_LIST_SIZE = 100;
    /**
     * API 权限是稳定的机器标识，只允许精确权限、一级域通配（例如 {@code ops.*}）和全局通配
     * {@code *}。该规则必须与 {@link HarnessIdentity#hasPermission(String)} 保持一致，避免
     * 凭证创建成功但运行时无法按预期授权，或反过来出现未声明的多级通配。
     */
    private static final Pattern PERMISSION_PATTERN = Pattern.compile(
            "(?:\\*|[a-z][a-z0-9-]*\\.\\*|[a-z][a-z0-9]*(?:[._:-][a-z0-9]+)*)");
    private static final int MAX_PERMISSION_COUNT = 64;
    private static final int MAX_PERMISSION_LENGTH = 128;
    private static final int MAX_PERMISSIONS_CSV_LENGTH = 2_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ApiKeyCredentialRepository credentialRepository;
    private final ApiKeyAuditRepository auditRepository;
    private final List<ConfiguredApiKey> configuredKeys;

    public ApiKeyCredentialService(ApiKeyCredentialRepository credentialRepository,
                                   ApiKeyAuditRepository auditRepository,
                                   HarnessAuthProperties properties) {
        this.credentialRepository = credentialRepository;
        this.auditRepository = auditRepository;
        this.configuredKeys = parseConfiguredKeys(properties.getApiKeys());
    }

    /** 验证数据库密钥优先于静态引导密钥；任一失败原因统一隐藏，避免枚举凭证状态。 */
    @Transactional(readOnly = true)
    public HarnessIdentity authenticate(String token) {
        if (token == null || token.isBlank()) {
            throw invalidApiKey();
        }
        String hash = hash(token.trim());
        Optional<ApiKeyCredential> stored;
        try {
            stored = credentialRepository.findByKeyHash(hash);
        } catch (DataAccessException exception) {
            // API Key 数据库不可用时快速失败，不能把未知凭证当作匿名或本地身份继续执行。
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_STORE_UNAVAILABLE",
                    "认证存储暂时不可用，请稍后重试");
        }
        if (stored.isPresent()) {
            ApiKeyCredential credential = stored.get();
            if (credential.usableAt(Instant.now())) {
                return new HarnessIdentity(credential.getTenantId(), credential.getUserId(),
                        parsePermissions(credential.getPermissions()), "api-key");
            }
            throw invalidApiKey();
        }
        return configuredKeys.stream()
                .filter(item -> MessageDigest.isEqual(item.hash().getBytes(StandardCharsets.UTF_8),
                        hash.getBytes(StandardCharsets.UTF_8)))
                .map(ConfiguredApiKey::identity)
                .findFirst()
                .orElseThrow(this::invalidApiKey);
    }

    /** 创建后只返回一次明文密钥；再次查询只返回前缀与元数据。 */
    @Transactional
    public ApiKeyView create(CreateApiKeyRequest request, String actorId) {
        validateCreateRequest(request);
        String token = nextToken();
        ApiKeyCredential credential = new ApiKeyCredential(hash(token), token.substring(0, 8),
                request.tenantId().trim(), request.userId().trim(),
                permissionsCsv(request.permissions()), request.expiresAt());
        ApiKeyCredential saved = credentialRepository.save(credential);
        auditRepository.save(new ApiKeyAudit(saved.getId(), saved.getTenantId(), safeActor(actorId),
                "API_KEY_CREATED", "prefix=" + saved.getKeyPrefix() + ";expiresAt=" + saved.getExpiresAt()));
        return toView(saved, token);
    }

    /**
     * 原子轮换数据库 API Key：新凭证沿用旧租户、用户和权限，旧凭证在同一事务内立即撤销。
     * 新 secret 只在本次响应中返回，避免先创建新 Key 再人工撤销旧 Key 造成长期双活。
     */
    @Transactional
    public ApiKeyView rotate(String keyId, String actorId, RotateApiKeyRequest request) {
        ApiKeyCredential previous = credentialRepository.findById(keyId).orElseThrow(() ->
                new BusinessException(HttpStatus.NOT_FOUND, "API_KEY_NOT_FOUND", "API Key 不存在"));
        if (previous.getStatus() != ApiKeyStatus.ACTIVE) {
            throw new BusinessException(HttpStatus.CONFLICT, "API_KEY_NOT_ROTATABLE",
                    "只有有效的 API Key 可以轮换");
        }
        Instant expiresAt = request != null && request.expiresAt() != null
                ? request.expiresAt() : previous.getExpiresAt();
        Set<String> permissions = parsePermissions(previous.getPermissions());
        validateCreateRequest(new CreateApiKeyRequest(
                previous.getTenantId(), previous.getUserId(), permissions, expiresAt));

        String actor = safeActor(actorId);
        String token = nextToken();
        ApiKeyCredential replacement = new ApiKeyCredential(hash(token), token.substring(0, 8),
                previous.getTenantId(), previous.getUserId(), permissionsCsv(permissions), expiresAt);
        previous.revoke(actor);
        ApiKeyCredential revoked = credentialRepository.save(previous);
        ApiKeyCredential saved = credentialRepository.save(replacement);
        auditRepository.save(new ApiKeyAudit(revoked.getId(), revoked.getTenantId(), actor,
                "API_KEY_ROTATED", "newKeyId=" + saved.getId() + ";newPrefix=" + saved.getKeyPrefix()));
        auditRepository.save(new ApiKeyAudit(saved.getId(), saved.getTenantId(), actor,
                "API_KEY_ROTATED_FROM", "oldKeyId=" + revoked.getId() + ";oldPrefix=" + revoked.getKeyPrefix()));
        return toView(saved, token);
    }

    @Transactional(readOnly = true)
    public List<ApiKeyView> list(String tenantId) {
        requireIdentifier(tenantId, "租户标识");
        return credentialRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, PageRequest.of(0, MAX_LIST_SIZE))
                .stream()
                .map(credential -> toView(credential, null))
                .toList();
    }

    @Transactional
    public ApiKeyView revoke(String keyId, String actorId) {
        ApiKeyCredential credential = credentialRepository.findById(keyId).orElseThrow(() ->
                new BusinessException(HttpStatus.NOT_FOUND, "API_KEY_NOT_FOUND", "API Key 不存在"));
        boolean active = credential.getStatus() == ApiKeyStatus.ACTIVE;
        credential.revoke(safeActor(actorId));
        ApiKeyCredential saved = credentialRepository.save(credential);
        if (active) {
            auditRepository.save(new ApiKeyAudit(saved.getId(), saved.getTenantId(), safeActor(actorId),
                    "API_KEY_REVOKED", "prefix=" + saved.getKeyPrefix()));
        }
        return toView(saved, null);
    }

    @Transactional(readOnly = true)
    public ApiKeyView get(String keyId) {
        return credentialRepository.findById(keyId)
                .map(credential -> toView(credential, null))
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "API_KEY_NOT_FOUND", "API Key 不存在"));
    }

    @Transactional(readOnly = true)
    public List<ApiKeyAuditView> auditTrail(String tenantId) {
        requireIdentifier(tenantId, "租户标识");
        return auditRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, PageRequest.of(0, MAX_LIST_SIZE))
                .stream()
                .map(audit -> new ApiKeyAuditView(audit.getId(), audit.getKeyId(), audit.getTenantId(),
                        audit.getActorId(), audit.getEventType(), audit.getDetails(), audit.getCreatedAt()))
                .toList();
    }

    private void validateCreateRequest(CreateApiKeyRequest request) {
        if (request == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "API_KEY_REQUEST_REQUIRED", "API Key 创建请求不能为空");
        }
        requireIdentifier(request.tenantId(), "租户标识");
        requireIdentifier(request.userId(), "用户标识");
        validatePermissions(request.permissions());
        if (request.expiresAt() != null && !request.expiresAt().isAfter(Instant.now())) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "API_KEY_EXPIRY_INVALID",
                    "API Key 过期时间必须晚于当前时间");
        }
    }

    private void requireIdentifier(String value, String label) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9._:@-]{0,127}")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "API_KEY_IDENTITY_INVALID", label + "不合法");
        }
    }

    private List<ConfiguredApiKey> parseConfiguredKeys(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<ConfiguredApiKey> result = new ArrayList<>();
        for (String item : value.split(";")) {
            String[] parts = item.split("\\|", -1);
            if (parts.length != 4 || parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
                throw new IllegalArgumentException(
                        "harness.auth.api-keys 格式错误，应为 key|tenant|user|permission1,permission2");
            }
            requireIdentifier(parts[1].trim(), "配置租户标识");
            requireIdentifier(parts[2].trim(), "配置用户标识");
            Set<String> permissions = parsePermissions(parts[3]);
            validatePermissions(permissions);
            result.add(new ConfiguredApiKey(hash(parts[0].trim()), new HarnessIdentity(parts[1].trim(),
                    parts[2].trim(), permissions, "api-key")));
        }
        return List.copyOf(result);
    }

    private Set<String> parsePermissions(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return java.util.Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    private String permissionsCsv(Set<String> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return "";
        }
        return permissions.stream().filter(item -> item != null && !item.isBlank()).map(String::trim)
                .distinct().sorted().collect(Collectors.joining(","));
    }

    /** 限制权限集合规模和格式，防止无效权限或超长载荷进入认证数据库。 */
    private void validatePermissions(Set<String> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return;
        }
        if (permissions.size() > MAX_PERMISSION_COUNT) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "API_KEY_PERMISSIONS_INVALID",
                    "API Key 权限数量不能超过 " + MAX_PERMISSION_COUNT);
        }
        for (String permission : permissions) {
            String normalized = permission == null ? "" : permission.trim();
            if (normalized.length() > MAX_PERMISSION_LENGTH
                    || !PERMISSION_PATTERN.matcher(normalized).matches()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "API_KEY_PERMISSIONS_INVALID",
                        "API Key 权限格式不合法");
            }
        }
        if (permissionsCsv(permissions).length() > MAX_PERMISSIONS_CSV_LENGTH) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "API_KEY_PERMISSIONS_INVALID",
                    "API Key 权限总长度不能超过 " + MAX_PERMISSIONS_CSV_LENGTH);
        }
    }

    private String nextToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return "mh_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String token) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM 不支持 SHA-256", exception);
        }
    }

    private String safeActor(String actorId) {
        return actorId == null || actorId.isBlank() ? "system" : actorId;
    }

    private BusinessException invalidApiKey() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, "INVALID_API_KEY", "API Key 无效或已失效");
    }

    private ApiKeyView toView(ApiKeyCredential credential, String secret) {
        return new ApiKeyView(credential.getId(), credential.getKeyPrefix(), credential.getTenantId(),
                credential.getUserId(), parsePermissions(credential.getPermissions()).stream().sorted().toList(),
                credential.getStatus(), credential.getCreatedAt(), credential.getExpiresAt(),
                credential.getRevokedAt(), credential.getRevokedBy(), secret);
    }

    private record ConfiguredApiKey(String hash, HarnessIdentity identity) {
    }
}
