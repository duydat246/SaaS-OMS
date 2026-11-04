package com.saasoms.modules.auth.service;

import com.saasoms.modules.auth.repository.RefreshTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RefreshTokenCleanupJobTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private RefreshTokenCleanupJob job;

    @Test
    void cleanup_deletesTokensExpiredBeforeNow() {
        job.cleanup();

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(refreshTokenRepository).deleteExpiredBefore(cutoff.capture());
        assertThat(cutoff.getValue()).isCloseTo(LocalDateTime.now(), within(5, ChronoUnit.SECONDS));
    }
}
