package org.mingharness.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.mingharness.security.HarnessIdentityInterceptor;

import java.util.Arrays;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;
    private final HarnessIdentityInterceptor identityInterceptor;

    public WebConfig(@Value("${harness.cors.allowed-origins}") String allowedOrigins,
                     HarnessIdentityInterceptor identityInterceptor) {
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toArray(String[]::new);
        this.identityInterceptor = identityInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // API Key/OIDC 也要保护管理指标；健康探针本身不在这里拦截，避免影响容器存活检查。
        registry.addInterceptor(identityInterceptor)
                .addPathPatterns("/api/**", "/actuator/metrics", "/actuator/metrics/**",
                        "/actuator/prometheus", "/actuator/info");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
