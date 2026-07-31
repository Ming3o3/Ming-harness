package org.mingharness.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 认证模式的 Spring Security 适配层。
 * local/api-key 由 HarnessIdentityInterceptor 处理，Spring Security 保持请求链开放；oidc 模式由 JWT Resource Server 验签。
 */
@Configuration
public class HarnessSecurityConfig {

    /** OIDC 模式显式绑定 issuer + audience，避免只依赖框架默认的 issuer 校验。 */
    @Bean
    @ConditionalOnProperty(prefix = "harness.auth", name = "mode", havingValue = "oidc")
    JwtDecoder harnessJwtDecoder(HarnessAuthProperties properties,
                                 @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer) {
        JwtAudienceValidator audienceValidator = new JwtAudienceValidator(properties.getOidcAudience());
        NimbusJwtDecoder decoder = (NimbusJwtDecoder) JwtDecoders.fromIssuerLocation(issuer);
        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(issuer);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerValidator,
                audienceValidator));
        return decoder;
    }

    @Bean
    SecurityFilterChain harnessSecurityFilterChain(HttpSecurity http,
                                                    HarnessAuthProperties properties) throws Exception {
        http.csrf(csrf -> csrf.disable());
        if ("oidc".equalsIgnoreCase(properties.getMode())) {
            http.authorizeHttpRequests(authorize -> authorize
                    .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                    .anyRequest().authenticated());
            http.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        } else {
            // local 和 api-key 的身份校验由 MVC 拦截器完成，避免 Spring Security 改变现有演示行为。
            http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
        }
        return http.build();
    }
}
