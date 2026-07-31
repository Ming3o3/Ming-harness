package org.mingharness.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 认证模式的 Spring Security 适配层。
 * local/api-key 由 HarnessIdentityInterceptor 处理，Spring Security 保持请求链开放；oidc 模式由 JWT Resource Server 验签。
 */
@Configuration
public class HarnessSecurityConfig {

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
