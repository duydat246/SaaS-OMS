package com.saasoms.common.config;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.handler.MappedInterceptor;
import org.springframework.web.util.ServletRequestPathUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OpenInViewConfigTest {

    @Test
    void publicApi_excludedFromOpenInView() {
        MappedInterceptor interceptor = registeredInterceptor();

        assertThat(interceptor.matches(request("/api/public/shops/cua-hang-a"))).isFalse();
        assertThat(interceptor.matches(request("/api/public/shops/cua-hang-a/images/55"))).isFalse();
    }

    @Test
    void adminApi_keepsOpenInView() {
        MappedInterceptor interceptor = registeredInterceptor();

        assertThat(interceptor.matches(request("/api/orders"))).isTrue();
        assertThat(interceptor.matches(request("/api/products/1"))).isTrue();
        assertThat(interceptor.matches(request("/api/shop"))).isTrue();
        assertThat(interceptor.matches(request("/api/media/products/1"))).isTrue();
        assertThat(interceptor.matches(request("/auth/login"))).isTrue();
    }

    /** Giữ nguyên ý nghĩa của thuộc tính mà auto-config của Boot vốn đọc. */
    @Test
    void openInViewPropertyFalse_registersNothing() {
        contextRunner().withPropertyValues("spring.jpa.open-in-view=false")
                .run(context -> assertThat(context).doesNotHaveBean(OpenEntityManagerInViewInterceptor.class));
    }

    @Test
    void openInViewPropertyMissing_registersInterceptor() {
        contextRunner().run(context -> assertThat(context).hasSingleBean(OpenEntityManagerInViewInterceptor.class));
    }

    private static ApplicationContextRunner contextRunner() {
        return new ApplicationContextRunner()
                .withUserConfiguration(OpenInViewConfig.class)
                .withBean(EntityManagerFactory.class, () -> mock(EntityManagerFactory.class));
    }

    private static MappedInterceptor registeredInterceptor() {
        OpenInViewConfig config = new OpenInViewConfig();
        ExposedRegistry registry = new ExposedRegistry();
        config.openInViewConfigurer(new OpenEntityManagerInViewInterceptor()).addInterceptors(registry);

        List<Object> interceptors = registry.interceptors();
        assertThat(interceptors).hasSize(1);
        assertThat(interceptors.get(0)).isInstanceOf(MappedInterceptor.class);
        return (MappedInterceptor) interceptors.get(0);
    }

    private static MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        ServletRequestPathUtils.parseAndCache(request);
        return request;
    }

    private static final class ExposedRegistry extends InterceptorRegistry {
        List<Object> interceptors() {
            return getInterceptors();
        }
    }
}
