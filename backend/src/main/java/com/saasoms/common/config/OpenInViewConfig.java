package com.saasoms.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Tắt open-in-view riêng cho API công khai, các API khác giữ nguyên. Khi bật, kết nối DB chỉ được trả
 * sau khi ghi xong response, nên stream ảnh cho client chậm có thể chiếm hết pool.
 * Khai báo bean này làm JpaWebConfiguration của Boot lùi lại, nên chỉ có một interceptor.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "spring.jpa", name = "open-in-view", havingValue = "true", matchIfMissing = true)
public class OpenInViewConfig {

    private static final String PUBLIC_API_PATTERN = "/api/public/**";

    @Bean
    public OpenEntityManagerInViewInterceptor openEntityManagerInViewInterceptor() {
        return new OpenEntityManagerInViewInterceptor();
    }

    @Bean
    public WebMvcConfigurer openInViewConfigurer(OpenEntityManagerInViewInterceptor interceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addWebRequestInterceptor(interceptor).excludePathPatterns(PUBLIC_API_PATTERN);
            }
        };
    }
}
