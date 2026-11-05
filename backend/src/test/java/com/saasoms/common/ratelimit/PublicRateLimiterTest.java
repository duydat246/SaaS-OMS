package com.saasoms.common.ratelimit;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.saasoms.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PublicRateLimiterTest {

    private static final String IP = "203.0.113.1";
    private static final int API_LIMIT = 300;
    private static final int IMAGE_LIMIT = 1200;

    private final MutableClock clock = new MutableClock();
    private final PublicRateLimiter limiter = new PublicRateLimiter(clock);

    @Test
    void checkApi_overLimit_throws429Vietnamese() {
        hitApi(IP, API_LIMIT);

        BusinessException ex = assertThrows(BusinessException.class, () -> limiter.checkApi(IP));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(ex.getMessage()).isEqualTo("Bạn truy cập quá nhanh, vui lòng thử lại sau ít phút");
    }

    @Test
    void checkImage_overLimit_throws429() {
        hitImage(IP, IMAGE_LIMIT);

        BusinessException ex = assertThrows(BusinessException.class, () -> limiter.checkImage(IP));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void apiAndImage_countedSeparately() {
        hitApi(IP, API_LIMIT);

        assertThatCode(() -> limiter.checkImage(IP)).doesNotThrowAnyException();
    }

    @Test
    void imageHits_doNotConsumeApiQuota() {
        hitImage(IP, IMAGE_LIMIT);

        assertThatCode(() -> limiter.checkApi(IP)).doesNotThrowAnyException();
    }

    @Test
    void windowExpired_reopens() {
        hitApi(IP, API_LIMIT);
        assertThrows(BusinessException.class, () -> limiter.checkApi(IP));

        clock.advance(Duration.ofMinutes(1));

        assertThatCode(() -> limiter.checkApi(IP)).doesNotThrowAnyException();
    }

    @Test
    void windowNotExpired_staysBlocked() {
        hitApi(IP, API_LIMIT);

        clock.advance(Duration.ofSeconds(59));

        assertThrows(BusinessException.class, () -> limiter.checkApi(IP));
    }

    @Test
    void twoIps_areIndependent() {
        hitApi(IP, API_LIMIT);

        assertThatCode(() -> limiter.checkApi("203.0.113.2")).doesNotThrowAnyException();
    }

    @Test
    void capReached_newKeyNotTrackedAndDoesNotThrow() {
        PublicRateLimiter small = new PublicRateLimiter(clock, 2);
        small.checkApi("10.0.0.1");
        small.checkApi("10.0.0.2");

        assertThatCode(() -> {
            for (int i = 0; i < API_LIMIT + 10; i++) {
                small.checkApi("10.0.0.3");
            }
        }).doesNotThrowAnyException();
        assertThat(small.trackedKeys()).isEqualTo(2);
    }

    @Test
    void capReached_knownKeyStillLimited() {
        PublicRateLimiter small = new PublicRateLimiter(clock, 1);
        for (int i = 0; i < API_LIMIT; i++) {
            small.checkApi(IP);
        }

        assertThrows(BusinessException.class, () -> small.checkApi(IP));
    }

    /** Khoá mới phải được đếm thật sau khi quét khoá hết hạn, nên vượt ngưỡng phải ra 429. */
    @Test
    void capReached_expiredKeysFreeRoom_newKeyIsCounted() {
        PublicRateLimiter small = new PublicRateLimiter(clock, 1);
        small.checkApi("10.0.0.1");

        clock.advance(Duration.ofMinutes(1));
        for (int i = 0; i < API_LIMIT; i++) {
            small.checkApi("10.0.0.2");
        }

        assertThrows(BusinessException.class, () -> small.checkApi("10.0.0.2"));
        assertThat(small.trackedKeys()).isEqualTo(1);
    }

    @Test
    void capReached_warnsAtMostOncePerMinute() {
        Logger logger = (Logger) LoggerFactory.getLogger(PublicRateLimiter.class);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
        try {
            PublicRateLimiter small = new PublicRateLimiter(clock, 1);
            small.checkApi("10.0.0.1");

            small.checkApi("10.0.0.2");
            small.checkApi("10.0.0.3");
            assertThat(logs.list).hasSize(1);
            assertThat(logs.list.get(0).getLevel()).isEqualTo(Level.WARN);

            clock.advance(Duration.ofSeconds(59));
            small.checkApi("10.0.0.4");
            assertThat(logs.list).hasSize(1);
        } finally {
            logger.detachAppender(logs);
        }
    }

    @Test
    void cleanup_removesOnlyExpiredKeys() {
        limiter.checkApi("10.0.0.1");
        clock.advance(Duration.ofSeconds(30));
        limiter.checkApi("10.0.0.2");

        clock.advance(Duration.ofSeconds(30));
        limiter.cleanup();

        assertThat(limiter.trackedKeys()).isEqualTo(1);
    }

    private void hitApi(String ip, int times) {
        for (int i = 0; i < times; i++) {
            limiter.checkApi(ip);
        }
    }

    private void hitImage(String ip, int times) {
        for (int i = 0; i < times; i++) {
            limiter.checkImage(ip);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-05T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
