package com.saasoms.modules.auth.service;

import com.saasoms.modules.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Refresh token hết hạn chỉ bị xoá khi người dùng dùng lại nó, nên phần lớn nằm mãi trong bảng.
 * Dọn mỗi ngày một lần.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupJob {

    private final RefreshTokenRepository refreshTokenRepository;

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void cleanup() {
        int deleted = refreshTokenRepository.deleteExpiredBefore(LocalDateTime.now());
        log.info("Đã dọn {} refresh token hết hạn", deleted);
    }
}
