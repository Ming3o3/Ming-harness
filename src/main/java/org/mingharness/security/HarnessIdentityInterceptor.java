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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
    private final List<ApiKeyCredential> credentials;

    public HarnessIdentityInterceptor(HarnessAuthProperties properties) {
        this.properties = properties;
        this.credentials = parseCredentials(properties.getApiKeys());
        if (!"local".equalsIgnoreCase(properties.getMode())
                && !"api-key".equalsIgnoreCase(properties.getMode())
                && !"oidc".equalsIgnoreCase(properties.getMode())) {
            throw new IllegalArgumentException("harness.auth.mode 只支持 local、api-key 或 oidc");
        }
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())
                || !request.getRequestURI().startsWith("/api/")) {
            return true;
        }

        HarnessIdentity identity = resolveIdentity(request);
        String requiredPermission = requiredPermission(request.getMethod(), request.getRequestURI());
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
        if (request.getRequestURI().startsWith("/api/")) {
            HarnessIdentityContext.clear();
        }
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
            byte[] digest = digest(token);
            return credentials.stream()
                    .filter(item -> MessageDigest.isEqual(item.digest(), digest))
                    .map(ApiKeyCredential::identity)
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "INVALID_API_KEY",
                            "API Key 无效或已失效"));
        }

        return new HarnessIdentity(
                safeHeader(request.getHeader("X-Tenant-Id"), "tenant-demo"),
                safeHeader(request.getHeader("X-User-Id"), "operator"),
                parsePermissions(request.getHeader("X-Permissions")),
                "local"
        );
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

    private List<ApiKeyCredential> parseCredentials(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<ApiKeyCredential> result = new ArrayList<>();
        for (String item : value.split(";")) {
            String[] parts = item.split("\\|", -1);
            if (parts.length != 4 || parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
                throw new IllegalArgumentException(
                        "harness.auth.api-keys 格式错误，应为 key|tenant|user|permission1,permission2");
            }
            result.add(new ApiKeyCredential(
                    digest(parts[0].trim()),
                    new HarnessIdentity(parts[1].trim(), parts[2].trim(), parsePermissions(parts[3]), "api-key")
            ));
        }
        return List.copyOf(result);
    }

    private byte[] digest(String token) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM 不支持 SHA-256", exception);
        }
    }

    private String requiredPermission(String method, String path) {
        if ("GET".equalsIgnoreCase(method) && path.matches("/api/runs/[^/]+/audit-events(?:/verify)?")) {
            return "audit.read";
        }
        if (path.equals("/api/runs")) {
            return "GET".equalsIgnoreCase(method) ? "run.read" : "POST".equalsIgnoreCase(method) ? "run.create" : null;
        }
        if (path.matches("/api/runs/[^/]+")) {
            if ("GET".equalsIgnoreCase(method)) return "run.read";
            if ("DELETE".equalsIgnoreCase(method)) return "run.cancel";
        }
        if (path.matches("/api/runs/[^/]+/(start|retry)")) return "run.execute";
        if (path.matches("/api/runs/[^/]+/(approve|reject)")) return "run.approve";
        if (path.equals("/api/dashboard/summary")) return "run.read";
        if (path.matches("/api/context/(documents|memories)(/[^/]+)?")) {
            return "GET".equalsIgnoreCase(method) ? "context.read" : "context.write";
        }
        if (path.equals("/api/context/preview")) return "context.read";
        if (path.equals("/api/evaluations")) {
            return "GET".equalsIgnoreCase(method) ? "evaluation.read" : "evaluation.run";
        }
        if (path.equals("/api/tools") && "GET".equalsIgnoreCase(method)) return "tool.read";
        return null;
    }

    private record ApiKeyCredential(byte[] digest, HarnessIdentity identity) {
    }
}
