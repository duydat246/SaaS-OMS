package com.saasoms.modules.auth.service;

import com.saasoms.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoginRateLimiterTest {

    private static final String IP = "5.5.5.5";
    private static final String EMAIL = "a@example.com";

    private final MutableClock clock = new MutableClock();
    private final LoginRateLimiter limiter = new LoginRateLimiter(clock);

    private void fail(int times, String ip, String email) {
        for (int i = 0; i < times; i++) {
            limiter.recordLoginFailure(ip, email);
        }
    }

    private void assertLoginBlocked(String ip, String email) {
        BusinessException ex = assertThrows(BusinessException.class, () -> limiter.checkLogin(ip, email));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    private void assertLoginAllowed(String ip, String email) {
        assertThatCode(() -> limiter.checkLogin(ip, email)).doesNotThrowAnyException();
    }

    @Test
    void login_fiveFailures_blocksPair() {
        fail(4, IP, EMAIL);
        assertLoginAllowed(IP, EMAIL);

        fail(1, IP, EMAIL);

        BusinessException ex = assertThrows(BusinessException.class, () -> limiter.checkLogin(IP, EMAIL));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(ex.getMessage()).isEqualTo("Bạn đã thử quá nhiều lần. Vui lòng thử lại sau 15 phút.");
    }

    @Test
    void login_blockedMessage_roundsRemainingMinutesUp() {
        fail(5, IP, EMAIL);
        clock.advance(Duration.ofMinutes(10).plusSeconds(1));

        BusinessException ex = assertThrows(BusinessException.class, () -> limiter.checkLogin(IP, EMAIL));

        assertThat(ex.getMessage()).isEqualTo("Bạn đã thử quá nhiều lần. Vui lòng thử lại sau 5 phút.");
    }

    @Test
    void login_otherEmailSameIp_notBlockedByPair() {
        fail(5, IP, EMAIL);

        assertLoginBlocked(IP, EMAIL);
        assertLoginAllowed(IP, "b@example.com");
    }

    @Test
    void login_sameEmailOtherIp_notBlocked() {
        fail(5, IP, EMAIL);

        assertLoginAllowed("6.6.6.6", EMAIL);
    }

    @Test
    void login_thirtyFailures_blocksIp() {
        for (int i = 0; i < 29; i++) {
            limiter.recordLoginFailure(IP, "user" + i + "@example.com");
        }
        assertLoginAllowed(IP, "new@example.com");

        limiter.recordLoginFailure(IP, "user29@example.com");

        assertLoginBlocked(IP, "new@example.com");
        assertLoginAllowed("6.6.6.6", "new@example.com");
    }

    @Test
    void login_success_resetsPair() {
        fail(4, IP, EMAIL);

        limiter.recordLoginSuccess(IP, EMAIL);
        fail(4, IP, EMAIL);

        assertLoginAllowed(IP, EMAIL);
    }

    @Test
    void login_success_doesNotResetIpCounter() {
        for (int i = 0; i < 29; i++) {
            limiter.recordLoginFailure(IP, "user" + i + "@example.com");
        }

        limiter.recordLoginSuccess(IP, EMAIL);
        limiter.recordLoginFailure(IP, "other@example.com");

        assertLoginBlocked(IP, EMAIL);
    }

    @Test
    void login_windowExpired_allows() {
        fail(5, IP, EMAIL);
        assertLoginBlocked(IP, EMAIL);

        clock.advance(Duration.ofMinutes(15));

        assertLoginAllowed(IP, EMAIL);
        fail(4, IP, EMAIL);
        assertLoginAllowed(IP, EMAIL);
    }

    @Test
    void login_emailCase_sameKey() {
        limiter.recordLoginFailure(IP, "User@Example.com");
        limiter.recordLoginFailure(IP, "  user@example.COM ");
        fail(3, IP, "user@example.com");

        assertLoginBlocked(IP, "USER@EXAMPLE.COM");
    }

    /** Collation mặc định bỏ dấu nên các biến thể này cùng khớp một tài khoản khi tra DB. */
    @Test
    void login_accentedEmail_sameKey() {
        limiter.recordLoginFailure(IP, "úser@example.com");
        limiter.recordLoginFailure(IP, "ùser@example.com");
        limiter.recordLoginFailure(IP, "ÚSER@EXAMPLE.COM");
        // "u" + dấu sắc kết hợp U+0301, khác dạng dựng sẵn ở trên.
        limiter.recordLoginFailure(IP, "úser@example.com");
        limiter.recordLoginFailure(IP, "ưser@éxample.com");

        assertLoginBlocked(IP, "user@example.com");
    }

    @Test
    void login_accentedEmailSuccess_resetsPlainEmailCounter() {
        fail(4, IP, "user@example.com");

        limiter.recordLoginSuccess(IP, "úser@example.com");
        fail(4, IP, "user@example.com");

        assertLoginAllowed(IP, "user@example.com");
    }

    @Test
    void register_elevenRequests_blocks() {
        for (int i = 0; i < 10; i++) {
            limiter.checkRegister(IP);
        }

        BusinessException ex = assertThrows(BusinessException.class, () -> limiter.checkRegister(IP));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(ex.getMessage())
                .isEqualTo("Có quá nhiều yêu cầu đăng ký từ địa chỉ này. Vui lòng thử lại sau 60 phút.");
        assertThatCode(() -> limiter.checkRegister("6.6.6.6")).doesNotThrowAnyException();
    }

    @Test
    void register_windowExpired_allows() {
        for (int i = 0; i < 10; i++) {
            limiter.checkRegister(IP);
        }
        assertThrows(BusinessException.class, () -> limiter.checkRegister(IP));

        clock.advance(Duration.ofHours(1));

        assertThatCode(() -> limiter.checkRegister(IP)).doesNotThrowAnyException();
    }

    @Test
    void register_doesNotShareCounterWithLogin() {
        fail(30, IP, EMAIL);

        assertThatCode(() -> limiter.checkRegister(IP)).doesNotThrowAnyException();
    }

    @Test
    void cleanup_removesExpired() {
        fail(1, IP, EMAIL);
        limiter.checkRegister(IP);
        assertThat(limiter.trackedKeys()).isEqualTo(3);

        clock.advance(Duration.ofMinutes(15));
        limiter.cleanup();

        // Cửa sổ đăng ký 1 giờ chưa hết, hai khoá login 15 phút đã hết.
        assertThat(limiter.trackedKeys()).isEqualTo(1);
    }

    @Test
    void capReached_doesNotBlockNewKey() {
        LoginRateLimiter small = new LoginRateLimiter(clock, 2);
        small.recordLoginFailure(IP, EMAIL);
        assertThat(small.trackedKeys()).isEqualTo(2);

        for (int i = 0; i < 40; i++) {
            small.recordLoginFailure("6.6.6.6", "b@example.com");
            small.checkRegister("7.7.7.7");
        }

        assertThat(small.trackedKeys()).isEqualTo(2);
        assertThatCode(() -> small.checkLogin("6.6.6.6", "b@example.com")).doesNotThrowAnyException();
    }

    @Test
    void capReached_existingKeyStillCounted() {
        LoginRateLimiter small = new LoginRateLimiter(clock, 2);
        for (int i = 0; i < 5; i++) {
            small.recordLoginFailure(IP, EMAIL);
        }

        assertThrows(BusinessException.class, () -> small.checkLogin(IP, EMAIL));
    }

    @Test
    void capReached_expiredKeys_cleanedInPlace() {
        LoginRateLimiter small = new LoginRateLimiter(clock, 2);
        small.recordLoginFailure(IP, EMAIL);

        clock.advance(Duration.ofMinutes(15));
        for (int i = 0; i < 5; i++) {
            small.recordLoginFailure("6.6.6.6", "b@example.com");
        }

        assertThat(small.trackedKeys()).isEqualTo(2);
        assertThrows(BusinessException.class, () -> small.checkLogin("6.6.6.6", "b@example.com"));
    }

    @Test
    void concurrent_hundredThreadsSameKey_noLostCount() throws Exception {
        int threads = 100;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    try {
                        limiter.checkRegister(IP);
                        return true;
                    } catch (BusinessException ex) {
                        return false;
                    }
                }));
            }
            start.countDown();

            int allowed = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    allowed++;
                }
            }

            assertThat(allowed).isEqualTo(10);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrent_failures_noLostCount() throws Exception {
        int threads = 30;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                String email = "user" + i + "@example.com";
                results.add(pool.submit(() -> {
                    start.await();
                    limiter.recordLoginFailure(IP, email);
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> result : results) {
                result.get();
            }

            assertLoginBlocked(IP, "new@example.com");
        } finally {
            pool.shutdownNow();
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-04T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
