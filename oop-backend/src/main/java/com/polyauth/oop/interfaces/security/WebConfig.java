package com.polyauth.oop.interfaces.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * OOP 后端 Web 拦截器配置。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final OpenApiAuthInterceptor openApiAuthInterceptor;

    /**
     * 注入认证与鉴权拦截器。
     */
    public WebConfig(AuthInterceptor authInterceptor,
                     OpenApiAuthInterceptor openApiAuthInterceptor) {
        this.authInterceptor = authInterceptor;
        this.openApiAuthInterceptor = openApiAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/auth/login", "/open/**");
        registry.addInterceptor(openApiAuthInterceptor)
                .addPathPatterns("/open/**");
    }
}
