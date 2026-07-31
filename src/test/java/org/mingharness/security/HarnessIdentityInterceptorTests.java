package org.mingharness.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mingharness.common.BusinessException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HarnessIdentityInterceptorTests {

    @AfterEach
    void clearIdentity() {
        HarnessIdentityContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void localModeShouldKeepHeaderCompatibility() throws Exception {
        HarnessAuthProperties properties = new HarnessAuthProperties();
        HarnessIdentityInterceptor interceptor = new HarnessIdentityInterceptor(properties);
        MockHttpServletRequest request = request("GET", "/api/runs");
        request.addHeader("X-Tenant-Id", "tenant-a");
        request.addHeader("X-User-Id", "alice");
        request.addHeader("X-Permissions", "run.read,run.create");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), null));
        assertEquals("tenant-a", HarnessIdentityContext.require().tenantId());
        assertEquals("alice", HarnessIdentityContext.require().userId());
        assertTrue(HarnessIdentityContext.require().hasPermission("run.read"));

        interceptor.afterCompletion(request, new MockHttpServletResponse(), null, null);
        assertNull(HarnessIdentityContext.current());
    }

    @Test
    void apiKeyShouldBindTenantUserAndPermissions() throws Exception {
        HarnessAuthProperties properties = new HarnessAuthProperties();
        properties.setMode("api-key");
        properties.setApiKeys("secret-key|tenant-a|alice|run.read,run.create");
        HarnessIdentityInterceptor interceptor = new HarnessIdentityInterceptor(properties);
        MockHttpServletRequest request = request("GET", "/api/runs");
        request.addHeader("Authorization", "Bearer secret-key");

        interceptor.preHandle(request, new MockHttpServletResponse(), null);

        assertEquals("tenant-a", HarnessIdentityContext.require().tenantId());
        assertEquals("alice", HarnessIdentityContext.require().userId());
        assertTrue(HarnessIdentityContext.require().hasPermission("run.read"));
    }

    @Test
    void apiKeyShouldRejectMissingCredentialsAndInsufficientPermissions() {
        HarnessAuthProperties properties = new HarnessAuthProperties();
        properties.setMode("api-key");
        properties.setApiKeys("secret-key|tenant-a|alice|run.create");
        HarnessIdentityInterceptor interceptor = new HarnessIdentityInterceptor(properties);

        BusinessException missing = assertThrows(BusinessException.class,
                () -> interceptor.preHandle(request("GET", "/api/runs"), new MockHttpServletResponse(), null));
        assertEquals("AUTHENTICATION_REQUIRED", missing.getCode());

        MockHttpServletRequest insufficientRequest = request("GET", "/api/runs");
        insufficientRequest.addHeader("X-Api-Key", "secret-key");
        BusinessException insufficient = assertThrows(BusinessException.class,
                () -> interceptor.preHandle(insufficientRequest, new MockHttpServletResponse(), null));
        assertEquals("PERMISSION_DENIED", insufficient.getCode());
    }

    @Test
    void apiKeyShouldRequireOpsPermissionForActuatorMetrics() {
        HarnessAuthProperties properties = new HarnessAuthProperties();
        properties.setMode("api-key");
        properties.setApiKeys("secret-key|tenant-a|alice|run.read");
        HarnessIdentityInterceptor interceptor = new HarnessIdentityInterceptor(properties);
        MockHttpServletRequest request = request("GET", "/actuator/metrics/harness.runs.created");
        request.addHeader("X-Api-Key", "secret-key");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> interceptor.preHandle(request, new MockHttpServletResponse(), null));

        assertEquals("PERMISSION_DENIED", exception.getCode());
    }

    @Test
    void oidcShouldMapJwtClaimsToIdentity() throws Exception {
        HarnessAuthProperties properties = new HarnessAuthProperties();
        properties.setMode("oidc");
        HarnessIdentityInterceptor interceptor = new HarnessIdentityInterceptor(properties);
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .claim("sub", "oidc-user")
                .claim("tenant_id", "tenant-oidc")
                .claim("permissions", List.of("run.read", "run.create"))
                .build();
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt);
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        MockHttpServletRequest request = request("GET", "/api/runs");

        interceptor.preHandle(request, new MockHttpServletResponse(), null);

        assertEquals("tenant-oidc", HarnessIdentityContext.require().tenantId());
        assertEquals("oidc-user", HarnessIdentityContext.require().userId());
        assertTrue(HarnessIdentityContext.require().hasPermission("run.read"));
        interceptor.afterCompletion(request, new MockHttpServletResponse(), null, null);
    }

    @Test
    void oidcShouldRejectMissingPermission() {
        HarnessAuthProperties properties = new HarnessAuthProperties();
        properties.setMode("oidc");
        HarnessIdentityInterceptor interceptor = new HarnessIdentityInterceptor(properties);
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .claim("sub", "oidc-user")
                .claim("tenant_id", "tenant-oidc")
                .claim("scope", "context.read")
                .build();
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt);
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> interceptor.preHandle(request("GET", "/api/runs"),
                        new MockHttpServletResponse(), null));

        assertEquals("PERMISSION_DENIED", exception.getCode());
    }

    private MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRequestURI(uri);
        return request;
    }
}
