package org.mingharness.security;

import org.mingharness.common.BusinessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** 租户内用户直接授权服务；授权结果会在每次请求解析身份时合并。 */
@Service
public class HarnessUserPermissionService {

    private static final int MAX_LIST_SIZE = 500;
    private static final int MAX_PERMISSION_COUNT = 64;
    private static final int MAX_PERMISSION_LENGTH = 128;
    private static final int MAX_PERMISSIONS_CSV_LENGTH = 2_000;
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:@-]{0,127}");
    private static final Pattern PERMISSION_PATTERN = Pattern.compile(
            "(?:\\*|[a-z][a-z0-9-]*\\.\\*|[a-z][a-z0-9]*(?:[._:-][a-z0-9]+)*)");

    private final HarnessUserPermissionRepository repository;

    public HarnessUserPermissionService(HarnessUserPermissionRepository repository) {
        this.repository = repository;
    }

    /** 将租户内直接授权合并到当前请求的已认证权限中。数据库故障时拒绝继续使用未知权限事实。 */
    @Transactional(readOnly = true)
    public Set<String> merge(String tenantId, String userId, Set<String> basePermissions) {
        requireIdentifier(tenantId, "组织标识");
        requireIdentifier(userId, "用户标识");
        try {
            Optional<HarnessUserPermission> assigned = repository.findByTenantIdAndUserId(tenantId, userId);
            if (assigned.isEmpty()) return immutablePermissions(basePermissions);
            Set<String> result = new LinkedHashSet<>(immutablePermissions(basePermissions));
            result.addAll(parsePermissions(assigned.get().getPermissions()));
            return Set.copyOf(result);
        } catch (DataAccessException exception) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_STORE_UNAVAILABLE",
                    "用户权限存储暂时不可用，请稍后重试");
        }
    }

    @Transactional(readOnly = true)
    public List<UserPermissionView> list(String tenantId) {
        requireIdentifier(tenantId, "组织标识");
        return repository.findByTenantIdOrderByUpdatedAtDesc(tenantId, PageRequest.of(0, MAX_LIST_SIZE))
                .stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public UserPermissionView get(String tenantId, String userId) {
        requireIdentifier(tenantId, "组织标识");
        requireIdentifier(userId, "用户标识");
        return repository.findByTenantIdAndUserId(tenantId, userId)
                .map(this::toView)
                .orElse(new UserPermissionView(null, tenantId, userId, List.of(), null, null, null));
    }

    @Transactional
    public UserPermissionView assign(AssignUserPermissionsRequest request, String actorId) {
        if (request == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "USER_PERMISSIONS_REQUEST_REQUIRED",
                    "用户权限请求不能为空");
        }
        requireIdentifier(request.tenantId(), "组织标识");
        requireIdentifier(request.userId(), "用户标识");
        Set<String> permissions = normalizeAndValidate(request.permissions());
        Optional<HarnessUserPermission> existing = repository.findByTenantIdAndUserId(
                request.tenantId().trim(), request.userId().trim());
        HarnessUserPermission target;
        if (existing.isPresent()) {
            target = existing.get();
            target.update(permissionsCsv(permissions), safeActor(actorId));
        } else {
            target = new HarnessUserPermission(request.tenantId().trim(), request.userId().trim(),
                    permissionsCsv(permissions), safeActor(actorId));
        }
        return toView(repository.save(target));
    }

    @Transactional
    public void clear(String tenantId, String userId) {
        requireIdentifier(tenantId, "组织标识");
        requireIdentifier(userId, "用户标识");
        repository.findByTenantIdAndUserId(tenantId, userId).ifPresent(repository::delete);
    }

    private UserPermissionView toView(HarnessUserPermission value) {
        return new UserPermissionView(value.getId(), value.getTenantId(), value.getUserId(),
                parsePermissions(value.getPermissions()).stream().sorted().toList(), value.getCreatedAt(),
                value.getUpdatedAt(), value.getUpdatedBy());
    }

    private Set<String> immutablePermissions(Set<String> permissions) {
        if (permissions == null || permissions.isEmpty()) return Set.of();
        return permissions.stream().filter(value -> value != null && !value.isBlank())
                .map(String::trim).collect(Collectors.toUnmodifiableSet());
    }

    private Set<String> parsePermissions(String value) {
        if (value == null || value.isBlank()) return Set.of();
        return Arrays.stream(value.split(",")).map(String::trim).filter(item -> !item.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    private Set<String> normalizeAndValidate(Set<String> permissions) {
        Set<String> normalized = immutablePermissions(permissions);
        if (normalized.size() > MAX_PERMISSION_COUNT) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "USER_PERMISSIONS_INVALID",
                    "用户权限数量不能超过 " + MAX_PERMISSION_COUNT);
        }
        for (String permission : normalized) {
            if (permission.length() > MAX_PERMISSION_LENGTH || !PERMISSION_PATTERN.matcher(permission).matches()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "USER_PERMISSIONS_INVALID",
                        "用户权限格式不合法");
            }
        }
        if (permissionsCsv(normalized).length() > MAX_PERMISSIONS_CSV_LENGTH) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "USER_PERMISSIONS_INVALID",
                    "用户权限总长度不能超过 " + MAX_PERMISSIONS_CSV_LENGTH);
        }
        return normalized;
    }

    private String permissionsCsv(Set<String> permissions) {
        return permissions.stream().sorted(Comparator.naturalOrder()).collect(Collectors.joining(","));
    }

    private void requireIdentifier(String value, String label) {
        if (value == null || !IDENTIFIER_PATTERN.matcher(value.trim()).matches()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "USER_PERMISSIONS_IDENTITY_INVALID", label + "不合法");
        }
    }

    private String safeActor(String actorId) {
        return actorId == null || actorId.isBlank() ? "system" : actorId.trim();
    }
}
