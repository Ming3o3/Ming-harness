package org.mingharness.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** 明确校验 JWT aud 声明，避免把发给其他资源服务的 Token 接受进 Harness。 */
public final class JwtAudienceValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_AUDIENCE = new OAuth2Error(
            "invalid_token", "OIDC Token 的 audience 不包含当前 Harness", null);
    private final Set<String> expectedAudiences;

    public JwtAudienceValidator(String configuredAudiences) {
        this.expectedAudiences = Arrays.stream((configuredAudiences == null ? "" : configuredAudiences).split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
        if (expectedAudiences.isEmpty()) {
            throw new IllegalArgumentException("harness.auth.oidc-audience 不能为空，生产环境必须配置 OIDC_AUDIENCE");
        }
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        return token != null && token.getAudience().stream().anyMatch(expectedAudiences::contains)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(INVALID_AUDIENCE);
    }
}
