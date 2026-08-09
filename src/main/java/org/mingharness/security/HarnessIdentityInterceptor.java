package org.mingharness.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.mingharness.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 解析本地身份或 API Key，并为 /api/** 端点执行路径级权限校验。
 * local 模式只用于本地演示；生产部署应切换到 api-key 或 OIDC 认证。
 */
@Component
public class HarnessIdentityInterceptor implements HandlerInterceptor {

    private static final Pattern SAFE_VALUE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:@-]{0,127}");
    private final HarnessAuthProperties properties;
    private final ApiKeyCredentialService apiKeyCredentialService;

    public HarnessIdentityInterceptor(HarnessAuthProperties properties,
                                      ApiKeyCredentialService apiKeyCredentialService) {
        this.properties = properties;
        this.apiKeyCredentialService = apiKeyCredentialService;
        if (!"local".equalsIgnoreCase(properties.getMode())
                && !"api-key".equalsIgnoreCase(properties.getMode())
                && !"oidc".equalsIgnoreCase(properties.getMode())) {
            throw new IllegalArgumentException("harness.auth.mode 只支持 local、api-key 或 oidc");
        }
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())
                || !isHarnessProtectedPath(request.getRequestURI())) {
            return true;
        }

        HarnessIdentity identity = resolveIdentity(request);
        String requestPath = request.getRequestURI();
        String requiredPermission = requiredPermission(request.getMethod(), requestPath);
        if (protectedAuthenticationMode() && requiredPermission != null
                && !identity.hasPermission(requiredPermission)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "PERMISSION_DENIED",
                    "当前身份缺少接口权限: " + requiredPermission);
        }
        HarnessIdentityContext.set(identity);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception exception) {
        if (isHarnessProtectedPath(request.getRequestURI())) {
            HarnessIdentityContext.clear();
        }
    }

    private boolean isHarnessProtectedPath(String path) {
        return path.startsWith("/api/")
                || path.equals("/actuator/metrics")
                || path.startsWith("/actuator/metrics/")
                || path.equals("/actuator/prometheus")
                || path.equals("/actuator/info");
    }

    private HarnessIdentity resolveIdentity(HttpServletRequest request) {
        if ("oidc".equalsIgnoreCase(properties.getMode())) {
            return resolveOidcIdentity();
        }
        if ("api-key".equalsIgnoreCase(properties.getMode())) {
            String token = apiKey(request);
            if (token == null) {
                throw new BusinessException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                        "缺少 Authorization Bearer Token 或 X-Api-Key");
            }
            return authenticateApiKeyToken(token);
        }

        return new HarnessIdentity(
                safeHeader(request.getHeader("X-Tenant-Id"), "tenant-demo"),
                safeHeader(request.getHeader("X-User-Id"), "operator"),
                parsePermissions(request.getHeader("X-Permissions")),
                "local"
        );
    }

    /** 供管理端点 Security Filter 复用同一套 API Key 校验规则。 */
    HarnessIdentity authenticateApiKeyToken(String token) {
        return apiKeyCredentialService.authenticate(token);
    }

    /** API Key 和 OIDC 都需要执行接口级 RBAC；local 保留请求头演示兼容性。 */
    private boolean protectedAuthenticationMode() {
        return "api-key".equalsIgnoreCase(properties.getMode())
                || "oidc".equalsIgnoreCase(properties.getMode());
    }

    private HarnessIdentity resolveOidcIdentity() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)
                || !authentication.isAuthenticated()) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                    "OIDC JWT 身份认证失败");
        }
        Jwt jwt = jwtAuthentication.getToken();
        String tenantId = firstClaim(jwt, "tenant_id", "tenant");
        String userId = jwt.getSubject();
        if (tenantId == null || tenantId.isBlank() || userId == null || userId.isBlank()) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "IDENTITY_CLAIMS_MISSING",
                    "OIDC Token 缺少 tenant_id 或 sub 声明");
        }
        return new HarnessIdentity(tenantId, userId, jwtPermissions(jwt), "oidc");
    }

    private String firstClaim(Jwt jwt, String... names) {
        for (String name : names) {
            Object value = jwt.getClaims().get(name);
            if (value != null && !value.toString().isBlank()) {
                return value.toString();
            }
        }
        return null;
    }

    private Set<String> jwtPermissions(Jwt jwt) {
        Object raw = jwt.getClaims().get("permissions");
        if (raw == null) raw = jwt.getClaims().get("scope");
        if (raw == null) raw = jwt.getClaims().get("scp");
        if (raw instanceof String value) {
            return Arrays.stream(value.split("[ ,]"))
                    .map(String::trim)
                    .filter(item -> !item.isBlank())
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
        if (raw instanceof java.util.Collection<?> values) {
            return values.stream()
                    .map(String::valueOf)
                    .filter(item -> !item.isBlank())
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
        return Set.of();
    }

    private String apiKey(HttpServletRequest request) {
        String apiKey = request.getHeader("X-Api-Key");
        if (apiKey != null && !apiKey.isBlank()) {
            return apiKey.trim();
        }
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = authorization.substring(7).trim();
            return token.isBlank() ? null : token;
        }
        return null;
    }

    private String safeHeader(String value, String fallback) {
        return value != null && SAFE_VALUE.matcher(value).matches() ? value : fallback;
    }

    private Set<String> parsePermissions(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private String requiredPermission(String method, String path) {
        if (path.startsWith("/actuator/")) {
            return "ops.read";
        }
        if (path.equals("/api/health")) return "ops.read";
        if (path.equals("/api/model-config") || path.equals("/api/model-config/test")) return "model.configure";
        if (path.matches("/api/admin/api-keys(?:/audits)?")) {
            return "GET".equalsIgnoreCase(method) ? "auth.key.read" : "auth.key.manage";
        }
        if (path.matches("/api/admin/api-keys/[^/]+")) {
            return "GET".equalsIgnoreCase(method) ? "auth.key.read" : "auth.key.manage";
        }
        if (path.matches("/api/admin/api-keys/[^/]+/rotate")) {
            return "auth.key.manage";
        }
        if (path.matches("/api/admin/tenants/[^/]+/policy(?:/audits)?")) {
            return "GET".equalsIgnoreCase(method) ? "tenant.policy.read" : "tenant.policy.write";
        }
        if ("GET".equalsIgnoreCase(method) && path.matches("/api/runs/[^/]+/audit-events(?:/verify)?")) {
            return "audit.read";
        }
        if (path.matches("/api/runs/[^/]+/feedback")) return "run.read";
        if (path.equals("/api/runs")) {
            return "GET".equalsIgnoreCase(method) ? "run.read" : "POST".equalsIgnoreCase(method) ? "run.create" : null;
        }
        if ("GET".equalsIgnoreCase(method) && path.matches("/api/runs/[^/]+/events")) return "run.read";
        if (path.matches("/api/runs/[^/]+")) {
            if ("GET".equalsIgnoreCase(method)) return "run.read";
            if ("DELETE".equalsIgnoreCase(method)) return "run.cancel";
        }
        if (path.matches("/api/runs/[^/]+/(start|retry)")) return "run.execute";
        if (path.matches("/api/runs/[^/]+/(approve|reject)")) return "run.approve";
        if (path.equals("/api/conversations")) {
            return "GET".equalsIgnoreCase(method) ? "run.read" : "run.create";
        }
        if (path.matches("/api/conversations/[^/]+/(messages|attachments)(?:/[^/]+)?")) return "run.create";
        if (path.matches("/api/conversations/[^/]+")) {
            return "GET".equalsIgnoreCase(method) ? "run.read" : "run.create";
        }
        if (path.equals("/api/dashboard/summary")) return "run.read";
        if (path.equals("/api/context/reindex")) return "context.reindex";
        if (path.equals("/api/context/configuration")) return "context.read";
        if (path.equals("/api/context/embedding-config")
                || path.equals("/api/context/embedding-config/test")) return "context.configure";
        if (path.matches("/api/context/(documents|memories)(/[^/]+)?")) {
            return "GET".equalsIgnoreCase(method) ? "context.read" : "context.write";
        }
        if (path.equals("/api/context/preview")) return "context.read";
        if (path.matches("/api/evaluations/cases(?:/[^/]+)?")) {
            return "GET".equalsIgnoreCase(method) ? "evaluation.read" : "evaluation.run";
        }
        if (path.equals("/api/evaluations") || path.equals("/api/evaluations/retrieval")) {
            return "GET".equalsIgnoreCase(method) ? "evaluation.read" : "evaluation.run";
        }
        if ("GET".equalsIgnoreCase(method)
                && (path.equals("/api/workspace")
                || path.equals("/api/workspace/files")
                || path.equals("/api/workspace/files/content")
                || path.equals("/api/workspace/git/status")
                || path.equals("/api/workspace/git/diff"))) return "workspace.read";
        if (path.equals("/api/workspace/files/editor-content")) return "workspace.write";
        if (path.equals("/api/workspaces")) {
            return "GET".equalsIgnoreCase(method) ? "workspace.read" : "workspace.manage";
        }
        if (path.equals("/api/tools") && "GET".equalsIgnoreCase(method)) return "tool.read";
        return null;
    }

}
