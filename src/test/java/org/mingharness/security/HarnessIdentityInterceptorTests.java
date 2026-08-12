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
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class HarnessIdentityInterceptorTests {

    @AfterEach
    void clearIdentity() {
        HarnessIdentityContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void localModeShouldKeepHeaderCompatibility() throws Exception {
        HarnessAuthProperties properties = new HarnessAuthProperties();
        HarnessIdentityInterceptor interceptor = interceptor(properties);
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
        HarnessIdentityInterceptor interceptor = interceptor(properties);
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
        HarnessIdentityInterceptor interceptor = interceptor(properties);

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
        HarnessIdentityInterceptor interceptor = interceptor(properties);
        MockHttpServletRequest request = request("GET", "/actuator/metrics/harness.runs.created");
        request.addHeader("X-Api-Key", "secret-key");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> interceptor.preHandle(request, new MockHttpServletResponse(), null));

        assertEquals("PERMISSION_DENIED", exception.getCode());
    }

    @Test
    void apiKeyShouldKeepWildcardPermissionCompatibility() throws Exception {
        HarnessAuthProperties properties = new HarnessAuthProperties();
        properties.setMode("api-key");
        properties.setApiKeys("secret-key|tenant-a|alice|ops.*");
        HarnessIdentityInterceptor interceptor = interceptor(properties);
        MockHttpServletRequest request = request("GET", "/api/health");
        request.addHeader("X-Api-Key", "secret-key");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), null));
        assertTrue(HarnessIdentityContext.require().hasPermission("ops.read"));
    }

    @Test
    void oidcShouldMapJwtClaimsToIdentity() throws Exception {
        HarnessAuthProperties properties = new HarnessAuthProperties();
        properties.setMode("oidc");
        HarnessIdentityInterceptor interceptor = interceptor(properties);
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
        HarnessIdentityInterceptor interceptor = interceptor(properties);
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

    @Test
    void expiredDatabaseApiKeyShouldBeRejected() {
        HarnessAuthProperties properties = new HarnessAuthProperties();
        ApiKeyCredentialRepository credentialRepository = mock(ApiKeyCredentialRepository.class);
        ApiKeyCredential expired = new ApiKeyCredential(
                "expired-hash", "mh_expire", "tenant-a", "alice", "run.read",
                Instant.now().minusSeconds(1));
        when(credentialRepository.findByKeyHash(anyString()))
                .thenReturn(Optional.of(expired));
        ApiKeyCredentialService service = new ApiKeyCredentialService(
                credentialRepository, mock(ApiKeyAuditRepository.class), properties);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.authenticate("any-token"));

        assertEquals("INVALID_API_KEY", exception.getCode());
    }

    @Test
    void apiKeyPermissionsShouldHaveSafeFormatAndBoundedCount() {
        HarnessAuthProperties properties = new HarnessAuthProperties();
        ApiKeyCredentialService service = new ApiKeyCredentialService(
                mock(ApiKeyCredentialRepository.class), mock(ApiKeyAuditRepository.class), properties);

        BusinessException invalidFormat = assertThrows(BusinessException.class,
                () -> service.create(new CreateApiKeyRequest(
                        "tenant-a", "alice", Set.of("Run.Read"), null), "operator"));
        assertEquals("API_KEY_PERMISSIONS_INVALID", invalidFormat.getCode());

        Set<String> tooMany = new HashSet<>();
        for (int index = 0; index < 65; index++) {
            tooMany.add("run.permission" + index);
        }
        BusinessException invalidCount = assertThrows(BusinessException.class,
                () -> service.create(new CreateApiKeyRequest(
                        "tenant-a", "alice", tooMany, null), "operator"));
        assertEquals("API_KEY_PERMISSIONS_INVALID", invalidCount.getCode());
    }

    @Test
    void educationAssignmentAndNotificationRoutesShouldUseSpecificPermissions() throws Exception {
        HarnessAuthProperties properties = new HarnessAuthProperties();
        properties.setMode("api-key");
        properties.setApiKeys("teacher-key|tenant-a|teacher|education.assign;student-key|tenant-a|student|education.read,education.write");
        HarnessIdentityInterceptor interceptor = interceptor(properties);

        MockHttpServletRequest create = request("POST", "/api/education/assignments");
        create.addHeader("X-Api-Key", "teacher-key");
        assertTrue(interceptor.preHandle(create, new MockHttpServletResponse(), null));
        assertTrue(HarnessIdentityContext.require().hasPermission("education.assign"));
        interceptor.afterCompletion(create, new MockHttpServletResponse(), null, null);

        MockHttpServletRequest readNotifications = request("GET", "/api/education/notifications");
        readNotifications.addHeader("X-Api-Key", "student-key");
        assertTrue(interceptor.preHandle(readNotifications, new MockHttpServletResponse(), null));
        assertTrue(HarnessIdentityContext.require().hasPermission("education.read"));
        interceptor.afterCompletion(readNotifications, new MockHttpServletResponse(), null, null);

        MockHttpServletRequest readProgress = request(
                "GET", "/api/education/assignments/assignment-1/progress");
        readProgress.addHeader("X-Api-Key", "student-key");
        assertTrue(interceptor.preHandle(readProgress, new MockHttpServletResponse(), null));
        assertTrue(HarnessIdentityContext.require().hasPermission("education.read"));
        interceptor.afterCompletion(readProgress, new MockHttpServletResponse(), null, null);

        MockHttpServletRequest cancel = request(
                "POST", "/api/education/assignments/assignment-1/cancel");
        cancel.addHeader("X-Api-Key", "teacher-key");
        assertTrue(interceptor.preHandle(cancel, new MockHttpServletResponse(), null));
        assertTrue(HarnessIdentityContext.require().hasPermission("education.assign"));
        interceptor.afterCompletion(cancel, new MockHttpServletResponse(), null, null);
    }

    @Test
    void educationCourseRoutesShouldRequireReadOrAssignPermission() throws Exception {
        HarnessAuthProperties properties = new HarnessAuthProperties();
        properties.setMode("api-key");
        properties.setApiKeys("teacher-key|tenant-a|teacher|education.assign;student-key|tenant-a|student|education.read");
        HarnessIdentityInterceptor interceptor = interceptor(properties);

        MockHttpServletRequest list = request("GET", "/api/education/courses/course-1/progress");
        list.addHeader("X-Api-Key", "student-key");
        assertTrue(interceptor.preHandle(list, new MockHttpServletResponse(), null));
        assertTrue(HarnessIdentityContext.require().hasPermission("education.read"));
        interceptor.afterCompletion(list, new MockHttpServletResponse(), null, null);

        MockHttpServletRequest assign = request("POST", "/api/education/courses/course-1/assignments");
        assign.addHeader("X-Api-Key", "teacher-key");
        assertTrue(interceptor.preHandle(assign, new MockHttpServletResponse(), null));
        assertTrue(HarnessIdentityContext.require().hasPermission("education.assign"));
        interceptor.afterCompletion(assign, new MockHttpServletResponse(), null, null);

        MockHttpServletRequest denied = request("POST", "/api/education/courses");
        denied.addHeader("X-Api-Key", "student-key");
        BusinessException exception = assertThrows(BusinessException.class,
                () -> interceptor.preHandle(denied, new MockHttpServletResponse(), null));
        assertEquals("PERMISSION_DENIED", exception.getCode());
    }

    @Test
    void educationWriteShouldAllowStudentSubmissionButNotCourseSourceConfiguration() throws Exception {
        HarnessAuthProperties properties = new HarnessAuthProperties();
        properties.setMode("api-key");
        properties.setApiKeys("teacher-key|tenant-a|teacher|education.assign;student-key|tenant-a|student|education.read,education.write");
        HarnessIdentityInterceptor interceptor = interceptor(properties);

        MockHttpServletRequest submit = request(
                "POST", "/api/education/assignments/assignment-1/submissions");
        submit.addHeader("X-Api-Key", "student-key");
        assertTrue(interceptor.preHandle(submit, new MockHttpServletResponse(), null));
        interceptor.afterCompletion(submit, new MockHttpServletResponse(), null, null);

        MockHttpServletRequest source = request("POST", "/api/education/sources");
        source.addHeader("X-Api-Key", "student-key");
        BusinessException sourceDenied = assertThrows(BusinessException.class,
                () -> interceptor.preHandle(source, new MockHttpServletResponse(), null));
        assertEquals("PERMISSION_DENIED", sourceDenied.getCode());

        MockHttpServletRequest teacherSource = request("POST", "/api/education/sources");
        teacherSource.addHeader("X-Api-Key", "teacher-key");
        assertTrue(interceptor.preHandle(teacherSource, new MockHttpServletResponse(), null));
        interceptor.afterCompletion(teacherSource, new MockHttpServletResponse(), null, null);
    }

    private MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRequestURI(uri);
        return request;
    }

    /** 使用空仓储验证静态引导 Key 的兼容行为，不连接数据库。 */
    private HarnessIdentityInterceptor interceptor(HarnessAuthProperties properties) {
        ApiKeyCredentialService service = new ApiKeyCredentialService(
                mock(ApiKeyCredentialRepository.class), mock(ApiKeyAuditRepository.class), properties);
        return new HarnessIdentityInterceptor(properties, service);
    }
}
