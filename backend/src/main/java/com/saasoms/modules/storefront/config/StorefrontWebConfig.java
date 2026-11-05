package com.saasoms.modules.storefront.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class StorefrontWebConfig implements WebMvcConfigurer {

    private final StorefrontRateLimitInterceptor storefrontRateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(storefrontRateLimitInterceptor).addPathPatterns("/api/public/**");
    }
}
