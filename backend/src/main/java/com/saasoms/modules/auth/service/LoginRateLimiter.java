package com.saasoms.modules.auth.service;

import com.saasoms.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * Giới hạn số lần thử đăng nhập và đăng ký, đếm trong bộ nhớ theo cửa sổ cố định.
 * Khởi động lại backend thì mất bộ đếm; chấp nhận được vì VPS chỉ chạy một instance.
 * Không có khoá chỉ theo email, để kẻ tấn công không khoá được tài khoản của nạn nhân.
 */
@Slf4j
@Component
public class LoginRateLimiter {

    private static final int MAX_FAILURES_PER_PAIR = 5;
    private static final int MAX_FAILURES_PER_IP = 30;
    private static final int MAX_REGISTERS_PER_IP = 10;
    private static final Duration LOGIN_WINDOW = Duration.ofMinutes(15);
    private static final Duration REGISTER_WINDOW = Duration.ofHours(1);
    private static final int DEFAULT_MAX_KEYS = 20_000;
    private static final long CAP_WARN_INTERVAL_MS = Duration.ofMinutes(1).toMillis();
    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");

    private static final String LOGIN_BLOCKED_MESSAGE = "Bạn đã thử quá nhiều lần. Vui lòng thử lại sau %d phút.";
    private static final String REGISTER_BLOCKED_MESSAGE =
            "Có quá nhiều yêu cầu đăng ký từ địa chỉ này. Vui lòng thử lại sau %d phút.";

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong lastCapWarnAt = new AtomicLong(Long.MIN_VALUE);
    private final Clock clock;
    private final int maxKeys;

    public LoginRateLimiter() {
        this(Clock.systemUTC());
    }

    LoginRateLimiter(Clock clock) {
        this(clock, DEFAULT_MAX_KEYS);
    }

    LoginRateLimiter(Clock clock, int maxKeys) {
        this.clock = clock;
        this.maxKeys = maxKeys;
    }

    /** Gọi trước khi tra DB và trước BCrypt. Ném 429 nếu cặp (IP, email) hoặc IP đã vượt ngưỡng. */
    public void checkLogin(String ip, String email) {
        long now = clock.millis();
        long blockedUntil = Math.max(
                blockedUntil(pairKey(ip, email), MAX_FAILURES_PER_PAIR, now),
                blockedUntil(ipKey(ip), MAX_FAILURES_PER_IP, now));

        if (blockedUntil > now) {
            throw tooManyRequests(LOGIN_BLOCKED_MESSAGE, blockedUntil - now);
        }
    }

    public void recordLoginFailure(String ip, String email) {
        increment(pairKey(ip, email), LOGIN_WINDOW);
        increment(ipKey(ip), LOGIN_WINDOW);
    }

    /** Đăng nhập đúng chỉ xoá bộ đếm của cặp; bộ đếm theo IP giữ nguyên. */
    public void recordLoginSuccess(String ip, String email) {
        windows.remove(pairKey(ip, email));
    }

    /** Đếm mọi yêu cầu đăng ký đã qua validation, gồm cả yêu cầu thất bại sau đó. */
    public void checkRegister(String ip) {
        Window window = increment(registerKey(ip), REGISTER_WINDOW);
        if (window != null && window.count() > MAX_REGISTERS_PER_IP) {
            throw tooManyRequests(REGISTER_BLOCKED_MESSAGE, window.expiresAt() - clock.millis());
        }
    }

    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    public void cleanup() {
        long now = clock.millis();
        windows.values().removeIf(window -> window.isExpired(now));
    }

    int trackedKeys() {
        return windows.size();
    }

    private long blockedUntil(String key, int limit, long now) {
        Window window = windows.get(key);
        if (window == null || window.isExpired(now) || window.count() < limit) {
            return Long.MIN_VALUE;
        }
        return window.expiresAt();
    }

    /** Trả về null khi đã đầy và khoá này không được theo dõi. */
    private Window increment(String key, Duration windowLength) {
        long now = clock.millis();
        if (!windows.containsKey(key) && !hasRoomForNewKey(now)) {
            warnCapReached(now);
            return null;
        }
        return windows.compute(key, (k, current) -> current == null || current.isExpired(now)
                ? new Window(1, now + windowLength.toMillis())
                : new Window(current.count() + 1, current.expiresAt()));
    }

    private boolean hasRoomForNewKey(long now) {
        if (windows.size() < maxKeys) {
            return true;
        }
        windows.values().removeIf(window -> window.isExpired(now));
        return windows.size() < maxKeys;
    }

    /** Khi bị spam khoá mới, cảnh báo tối đa mỗi phút một lần để không làm đầy đĩa vì log. */
    private void warnCapReached(long now) {
        long last = lastCapWarnAt.get();
        if (now - last >= CAP_WARN_INTERVAL_MS && lastCapWarnAt.compareAndSet(last, now)) {
            log.warn("Bộ giới hạn đăng nhập đã đầy {} khoá, tạm không theo dõi khoá mới", maxKeys);
        }
    }

    private static BusinessException tooManyRequests(String template, long remainingMillis) {
        long minutes = Math.max(1, (remainingMillis + 59_999) / 60_000);
        return new BusinessException(template.formatted(minutes), HttpStatus.TOO_MANY_REQUESTS);
    }

    private static String pairKey(String ip, String email) {
        return "pair:" + ip + "|" + normalizeEmail(email);
    }

    /**
     * Collation mặc định của users.email bỏ dấu nên "úser@x.com" khớp cùng tài khoản với "user@x.com".
     * Khoá phải gộp các biến thể này, nếu không giới hạn theo cặp bị lách bằng cách đổi dấu.
     */
    private static String normalizeEmail(String email) {
        String decomposed = Normalizer.normalize(email.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return COMBINING_MARKS.matcher(decomposed).replaceAll("");
    }

    private static String ipKey(String ip) {
        return "ip:" + ip;
    }

    private static String registerKey(String ip) {
        return "register:" + ip;
    }

    private record Window(int count, long expiresAt) {
        boolean isExpired(long now) {
            return now >= expiresAt;
        }
    }
}
