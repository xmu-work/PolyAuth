package com.polyauth.oop.interfaces.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

/**
 * OOP 后端 Web 拦截器与跨域配置。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final OpenApiAuthInterceptor openApiAuthInterceptor;
    private final boolean allowAnyOrigin;
    private final String[] allowedOrigins;

    /**
     * 注入认证与鉴权拦截器，并解析 CORS 允许的来源列表。
     */
    public WebConfig(
            AuthInterceptor authInterceptor,
            OpenApiAuthInterceptor openApiAuthInterceptor,
            @Value("${cors.allowed-origins:*}")
            String corsAllowedOrigins) {
        this.authInterceptor = authInterceptor;
        this.openApiAuthInterceptor = openApiAuthInterceptor;
        List<String> rawOrigins = Arrays.stream(corsAllowedOrigins.split(","))
                .map(String::strip)
                .filter(origin -> !origin.isEmpty())
                .distinct()
                .toList();
        this.allowAnyOrigin = rawOrigins.contains("*");
        this.allowedOrigins = this.allowAnyOrigin
                ? new String[0]
                : rawOrigins.stream()
                .map(WebConfig::normalizeOrigin)
                .distinct()
                .toArray(String[]::new);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/auth/login", "/open/**");
        registry.addInterceptor(openApiAuthInterceptor)
                .addPathPatterns("/open/**");
    }

    /**
     * 配置跨域规则，支持外部业务前端携带 Authorization 头访问统一身份接口。
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        CorsRegistration registration = registry.addMapping("/**")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("X-Request-Id", "Content-Disposition")
                .maxAge(3600);
        if (allowAnyOrigin) {
            registration.allowedOriginPatterns("*");
        } else if (allowedOrigins.length > 0) {
            registration.allowedOrigins(allowedOrigins);
        }
    }

    private static String normalizeOrigin(String raw) {
        String normalized = raw.endsWith("/") ? raw.substring(0, raw.length() - 1) : raw;
        URI uri = URI.create(normalized);
        String scheme = uri.getScheme();
        if ((!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))
                || uri.getHost() == null
                || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null) {
            throw new IllegalArgumentException(
                    "cors.allowed-origins 必须配置为完整 Origin（如 https://course.example.edu）或 *");
        }
        return normalized;
    }
}
