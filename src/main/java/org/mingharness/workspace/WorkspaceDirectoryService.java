package org.mingharness.workspace;

import org.mingharness.common.BusinessException;
import org.mingharness.config.WorkspaceProperties;
import org.mingharness.workspace.api.LocalWorkspaceView;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 管理本地目录授权记录，并在每次使用前重新校验目录仍然可访问。 */
@Service
public class WorkspaceDirectoryService {

    private final LocalWorkspaceRepository repository;
    private final WorkspacePathCipher pathCipher;
    private final WorkspaceProperties properties;
    private final boolean localRegistrationEnabled;
    private final String desktopBridgeToken;

    public WorkspaceDirectoryService(LocalWorkspaceRepository repository,
                                     WorkspacePathCipher pathCipher,
                                     WorkspaceProperties properties,
                                     @Value("${harness.workspace.local-registration-enabled:false}")
                                     boolean localRegistrationEnabled,
                                     @Value("${harness.workspace.desktop-bridge-token:}") String desktopBridgeToken) {
        this.repository = repository;
        this.pathCipher = pathCipher;
        this.properties = properties;
        this.localRegistrationEnabled = localRegistrationEnabled;
        this.desktopBridgeToken = desktopBridgeToken;
    }

    @Transactional(readOnly = true)
    public List<LocalWorkspaceView> list(String tenantId, String userId) {
        return repository.findByTenantIdAndUserIdOrderByUpdatedAtDesc(tenantId, userId)
                .stream().map(this::view).toList();
    }

    /** 该入口仅提供给原生桌面目录选择器，普通浏览器模式默认关闭。 */
    @Transactional
    public LocalWorkspaceView register(String tenantId, String userId, String displayName, String rawRootPath,
                                       String suppliedBridgeToken) {
        if (!properties.enabled()) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "WORKSPACE_DISABLED", "本地工作区工具未启用");
        }
        if (!localRegistrationEnabled) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "LOCAL_WORKSPACE_REGISTRATION_DISABLED",
                    "仅桌面本地模式允许登记本机目录");
        }
        // 路径只能由本机 Electron/Tauri 主进程持有的令牌登记，渲染层和普通浏览器不会得到该令牌。
        if (desktopBridgeToken == null || desktopBridgeToken.isBlank()) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "DESKTOP_BRIDGE_UNAVAILABLE",
                    "桌面工作区桥接尚未配置");
        }
        if (suppliedBridgeToken == null || !MessageDigest.isEqual(
                desktopBridgeToken.getBytes(StandardCharsets.UTF_8),
                suppliedBridgeToken.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "DESKTOP_BRIDGE_DENIED",
                    "本地目录只能通过受信任的桌面桥接登记");
        }
        Path root = validateRoot(rawRootPath);
        List<LocalWorkspace> existingWorkspaces = repository.findByTenantIdAndUserIdOrderByUpdatedAtDesc(tenantId, userId);
        for (LocalWorkspace existing : existingWorkspaces) {
            try {
                Path existingRoot = validateRoot(pathCipher.decrypt(existing.getRootPathCiphertext()));
                if (existingRoot.equals(root)) {
                    // Electron 反复选择同一个项目是常见行为，复用记录才能保持旧会话/Run 的关联可追溯。
                    existing.touch();
                    return view(repository.save(existing));
                }
            } catch (BusinessException ignored) {
                // 无法解密或已失效的旧记录不影响新项目登记，也不暴露其真实路径。
            }
        }
        String name = uniqueName(normalizeName(displayName, root), existingWorkspaces);
        LocalWorkspace saved = repository.save(new LocalWorkspace(tenantId, userId, name,
                pathCipher.encrypt(root.toString())));
        return view(saved);
    }

    /** 在创建会话、Run 和执行工具时验证工作区归属，并返回已校验的真实根目录。 */
    @Transactional(readOnly = true)
    public Path requireRoot(String workspaceId, String tenantId, String userId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            return configuredRoot();
        }
        LocalWorkspace workspace = repository.findByIdAndTenantIdAndUserId(workspaceId, tenantId, userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "WORKSPACE_NOT_FOUND",
                        "本地工作区不存在或无权访问"));
        return validateRoot(pathCipher.decrypt(workspace.getRootPathCiphertext()));
    }

    public Path configuredRoot() {
        return Path.of(properties.root()).toAbsolutePath().normalize();
    }

    private LocalWorkspaceView view(LocalWorkspace workspace) {
        boolean accessible = false;
        boolean gitRepository = false;
        try {
            Path root = validateRoot(pathCipher.decrypt(workspace.getRootPathCiphertext()));
            accessible = true;
            Path git = root.resolve(".git");
            gitRepository = !Files.isSymbolicLink(git)
                    && Files.isDirectory(git, LinkOption.NOFOLLOW_LINKS);
        } catch (BusinessException ignored) {
            // 磁盘被卸载或目录被移动时，列表仍返回记录，但不暴露原始路径和异常细节。
        }
        return new LocalWorkspaceView(workspace.getId(), workspace.getDisplayName(), accessible,
                gitRepository, workspace.getCreatedAt(), workspace.getUpdatedAt());
    }

    private Path validateRoot(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "WORKSPACE_ROOT_REQUIRED", "本地工作区目录不能为空");
        }
        try {
            Path input = Path.of(rawPath);
            if (!input.isAbsolute()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "WORKSPACE_ROOT_NOT_ABSOLUTE",
                        "本地工作区必须是绝对路径");
            }
            Path root = input.normalize();
            if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root)) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "WORKSPACE_ROOT_UNAVAILABLE",
                        "所选本地工作区目录不可访问");
            }
            return root;
        } catch (java.nio.file.InvalidPathException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "WORKSPACE_ROOT_INVALID", "本地工作区路径不合法");
        }
    }

    private String normalizeName(String requested, Path root) {
        String value = requested == null || requested.isBlank()
                ? String.valueOf(root.getFileName()) : requested.trim();
        value = value.replaceAll("[\\p{Cntrl}]", "_").trim();
        if (value.isBlank()) value = "本地工作区";
        return value.substring(0, Math.min(value.length(), 255));
    }

    /** 同名目录来自不同磁盘或父目录时追加序号，避免数据库唯一约束导致桌面选择失败。 */
    private String uniqueName(String preferred, List<LocalWorkspace> existingWorkspaces) {
        Set<String> used = new HashSet<>();
        for (LocalWorkspace workspace : existingWorkspaces) {
            used.add(workspace.getDisplayName());
        }
        if (!used.contains(preferred)) return preferred;
        for (int suffixIndex = 2; suffixIndex < Integer.MAX_VALUE; suffixIndex++) {
            String suffix = " (" + suffixIndex + ")";
            int baseLength = Math.max(1, 255 - suffix.length());
            String candidate = preferred.substring(0, Math.min(preferred.length(), baseLength)) + suffix;
            if (!used.contains(candidate)) return candidate;
        }
        throw new BusinessException(HttpStatus.CONFLICT, "WORKSPACE_NAME_EXHAUSTED", "本地工作区名称已达到上限");
    }
}
