package com.saasoms.common.ratelimit;

import com.saasoms.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Giới hạn truy cập API công khai theo IP, đếm trong bộ nhớ theo cửa sổ cố định 1 phút.
 * Một trang danh sách tải nhiều ảnh, nên ảnh và API đếm riêng để ảnh không ăn hết hạn mức API.
 * Khởi động lại backend thì mất bộ đếm; chấp nhận được vì VPS chỉ chạy một instance.
 */
@Slf4j
@Component
public class PublicRateLimiter {

    private static final int MAX_API_REQUESTS = 300;
    private static final int MAX_IMAGE_REQUESTS = 1200;
    private static final long WINDOW_MILLIS = Duration.ofMinutes(1).toMillis();
    private static final int DEFAULT_MAX_KEYS = 20_000;
    private static final long CAP_WARN_INTERVAL_MS = Duration.ofMinutes(1).toMillis();
    private static final long CAP_SWEEP_INTERVAL_MS = Duration.ofSeconds(1).toMillis();
    private static final String BLOCKED_MESSAGE = "Bạn truy cập quá nhanh, vui lòng thử lại sau ít phút";

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    // Khởi tạo 0, không dùng Long.MIN_VALUE: phép now - last sẽ tràn âm và điều kiện khoảng cách luôn sai.
    private final AtomicLong lastCapWarnAt = new AtomicLong(0);
    private final AtomicLong lastCapSweepAt = new AtomicLong(0);
    private final Clock clock;
    private final int maxKeys;

    public PublicRateLimiter() {
        this(Clock.systemUTC());
    }

    PublicRateLimiter(Clock clock) {
        this(clock, DEFAULT_MAX_KEYS);
    }

    PublicRateLimiter(Clock clock, int maxKeys) {
        this.clock = clock;
        this.maxKeys = maxKeys;
    }

    public void checkApi(String ip) {
        check("api:" + ip, MAX_API_REQUESTS);
    }

    public void checkImage(String ip) {
        check("image:" + ip, MAX_IMAGE_REQUESTS);
    }

    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.MINUTES)
    public void cleanup() {
        long now = clock.millis();
        windows.values().removeIf(window -> window.isExpired(now));
    }

    int trackedKeys() {
        return windows.size();
    }

    private void check(String key, int limit) {
        long now = clock.millis();
        if (!windows.containsKey(key) && !hasRoomForNewKey(now)) {
            // Đầy khoá thì cho qua: chặn ở đây sẽ làm khách thật bị 429 vì người khác spam IP mới.
            warnCapReached(now);
            return;
        }
        Window window = windows.compute(key, (k, current) -> current == null || current.isExpired(now)
                ? new Window(1, now + WINDOW_MILLIS)
                : new Window(current.count() + 1, current.expiresAt()));
        if (window.count() > limit) {
            throw new BusinessException(BLOCKED_MESSAGE, HttpStatus.TOO_MANY_REQUESTS);
        }
    }

    /** Đường công khai không cần đăng nhập nên dễ bị spam: khi đầy chỉ quét tối đa mỗi giây một lần. */
    private boolean hasRoomForNewKey(long now) {
        if (windows.size() < maxKeys) {
            return true;
        }
        long last = lastCapSweepAt.get();
        if (now - last >= CAP_SWEEP_INTERVAL_MS && lastCapSweepAt.compareAndSet(last, now)) {
            windows.values().removeIf(window -> window.isExpired(now));
        }
        return windows.size() < maxKeys;
    }

    /** Khi bị spam khoá mới, cảnh báo tối đa mỗi phút một lần để không làm đầy đĩa vì log. */
    private void warnCapReached(long now) {
        long last = lastCapWarnAt.get();
        if (now - last >= CAP_WARN_INTERVAL_MS && lastCapWarnAt.compareAndSet(last, now)) {
            log.warn("Bộ giới hạn truy cập công khai đã đầy {} khoá, tạm không theo dõi khoá mới", maxKeys);
        }
    }

    private record Window(int count, long expiresAt) {
        boolean isExpired(long now) {
            return now >= expiresAt;
        }
    }
}
