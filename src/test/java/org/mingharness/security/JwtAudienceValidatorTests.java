package org.mingharness.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证 OIDC audience 必须命中服务端配置的资源标识。 */
class JwtAudienceValidatorTests {

    @Test
    void shouldAcceptAnyConfiguredAudience() {
        JwtAudienceValidator validator = new JwtAudienceValidator("harness-api, legacy-api");
        Jwt token = token(List.of("other-api", "legacy-api"));

        assertFalse(validator.validate(token).hasErrors());
    }

    @Test
    void shouldRejectMissingOrWrongAudience() {
        JwtAudienceValidator validator = new JwtAudienceValidator("harness-api");

        assertTrue(validator.validate(token(List.of("other-api"))).hasErrors());
        assertTrue(validator.validate(token(List.of())).hasErrors());
    }

    @Test
    void shouldRejectEmptyProductionConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new JwtAudienceValidator(" , "));
    }

    private Jwt token(List<String> audiences) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .audience(audiences)
                .build();
    }
}
