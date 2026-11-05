package com.saasoms.modules.storefront.config;

import com.saasoms.common.ratelimit.PublicRateLimiter;
import com.saasoms.common.util.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Giới hạn truy cập API công khai theo IP. Lỗi 429 ném ra đây đi qua handler chung.
 */
@Component
@RequiredArgsConstructor
public class StorefrontRateLimitInterceptor implements HandlerInterceptor {

    private static final String IMAGE_PATTERN_SUFFIX = "/images/{imageId}";

    private final PublicRateLimiter publicRateLimiter;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        String ip = ClientIpResolver.resolve(request);
        if (isImageRoute(request)) {
            publicRateLimiter.checkImage(ip);
        } else {
            publicRateLimiter.checkApi(ip);
        }
        return true;
    }

    /** Dựa vào route đã khớp, không dựa vào URI thô: URI có thể mã hoá hoặc thêm đoạn để đổi nhóm đếm. */
    private static boolean isImageRoute(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return pattern != null && pattern.toString().endsWith(IMAGE_PATTERN_SUFFIX);
    }
}
