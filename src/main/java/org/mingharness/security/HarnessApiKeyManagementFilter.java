package org.mingharness.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.mingharness.common.BusinessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 将 API Key 身份桥接到 Spring Security，仅用于 Actuator 管理端点。
 *
 * <p>业务 API 仍由 HarnessIdentityInterceptor 绑定租户和用户；Actuator Endpoint
 * 不一定经过 MVC HandlerInterceptor，因此必须在 Security Filter 层完成 ops.read 授权。</p>
 */
public class HarnessApiKeyManagementFilter extends OncePerRequestFilter {

    private static final String OPS_READ_AUTHORITY = "HARNESS_ops.read";

    private final HarnessAuthProperties properties;
    private final HarnessIdentityInterceptor identityInterceptor;

    public HarnessApiKeyManagementFilter(HarnessAuthProperties properties,
                                         HarnessIdentityInterceptor identityInterceptor) {
        this.properties = properties;
        this.identityInterceptor = identityInterceptor;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"api-key".equalsIgnoreCase(properties.getMode())) {
            return true;
        }
        String path = request.getRequestURI();
        return !path.equals("/actuator/metrics")
                && !path.startsWith("/actuator/metrics/")
                && !path.equals("/actuator/prometheus")
                && !path.equals("/actuator/info");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = apiKey(request);
        if (token != null) {
            try {
                HarnessIdentity identity = identityInterceptor.authenticateApiKeyToken(token);
                List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                identity.permissions().stream()
                        .map(permission -> new SimpleGrantedAuthority("HARNESS_" + permission))
                        .forEach(authorities::add);
                if (identity.hasPermission("ops.read")) {
                    // 兼容 ops.* 和 * 通配权限，供 Spring Security 的精确权限匹配使用。
                    authorities.add(new SimpleGrantedAuthority(OPS_READ_AUTHORITY));
                }
                Authentication authentication = new UsernamePasswordAuthenticationToken(
                        identity.userId(), null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (BusinessException ignored) {
                // 不在过滤器内泄露 API Key 失败原因，由 Spring Security 统一返回 401。
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
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
}
