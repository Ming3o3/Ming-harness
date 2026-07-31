package org.mingharness.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 认证模式的 Spring Security 适配层。
 * local/api-key 由 HarnessIdentityInterceptor 处理，管理指标也必须经过其 RBAC；oidc 模式由 JWT Resource Server 验签。
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
                                                    HarnessAuthProperties properties,
                                                    HarnessIdentityInterceptor identityInterceptor) throws Exception {
        http.csrf(csrf -> csrf.disable());
        if ("oidc".equalsIgnoreCase(properties.getMode())) {
            http.authorizeHttpRequests(authorize -> authorize
                    .requestMatchers("/actuator/health/**").permitAll()
                    .requestMatchers("/actuator/metrics", "/actuator/metrics/**", "/actuator/prometheus", "/actuator/info")
                    .hasAuthority("HARNESS_ops.read")
                    .anyRequest().authenticated());
            http.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
                    jwt.jwtAuthenticationConverter(harnessJwtAuthenticationConverter())));
        } else if ("api-key".equalsIgnoreCase(properties.getMode())) {
            http.addFilterBefore(new HarnessApiKeyManagementFilter(properties, identityInterceptor),
                    AnonymousAuthenticationFilter.class);
            http.authorizeHttpRequests(authorize -> authorize
                    .requestMatchers("/actuator/health/**").permitAll()
                    .requestMatchers("/actuator/metrics", "/actuator/metrics/**", "/actuator/prometheus", "/actuator/info")
                    .hasAuthority("HARNESS_ops.read")
                    .anyRequest().permitAll());
        } else {
            // local 的身份校验由 MVC 拦截器处理，并保留零依赖演示行为。
            http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
        }
        return http.build();
    }

    /** 将 OIDC 的 permissions/scope/scp 声明映射为管理端点可识别的权限。 */
    private JwtAuthenticationConverter harnessJwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter defaultConverter = new JwtGrantedAuthoritiesConverter();
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Set<GrantedAuthority> authorities = new LinkedHashSet<>(defaultConverter.convert(jwt));
            Set<String> permissions = new LinkedHashSet<>();
            addClaimPermissions(permissions, jwt.getClaims().get("permissions"));
            addClaimPermissions(permissions, jwt.getClaims().get("scope"));
            addClaimPermissions(permissions, jwt.getClaims().get("scp"));
            permissions.forEach(permission ->
                    authorities.add(new SimpleGrantedAuthority("HARNESS_" + permission)));
            if (permissions.contains("*") || permissions.contains("ops.*") || permissions.contains("ops.read")) {
                authorities.add(new SimpleGrantedAuthority("HARNESS_ops.read"));
            }
            return authorities;
        });
        return converter;
    }

    private void addClaimPermissions(Set<String> permissions, Object raw) {
        if (raw instanceof String value) {
            for (String item : value.split("[ ,]")) {
                if (!item.isBlank()) permissions.add(item.trim());
            }
        } else if (raw instanceof Collection<?> values) {
            values.stream().map(String::valueOf).filter(item -> !item.isBlank()).forEach(permissions::add);
        }
    }
}
