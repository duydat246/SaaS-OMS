package com.saasoms.common.media;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediaUrlSignerTest {

    private static final Long IMAGE_ID = 12L;
    private static final Long TENANT_ID = 1L;

    private MediaUrlSigner signer;

    @BeforeEach
    void setUp() {
        MediaProperties properties = new MediaProperties();
        properties.setSigningSecret("test-secret-key-at-least-32-characters-long");
        properties.setUrlTtlSeconds(600);
        signer = new MediaUrlSigner(properties);
    }

    private static MediaUrlSigner signerWithSecret(String secret) {
        MediaProperties properties = new MediaProperties();
        properties.setSigningSecret(secret);
        return new MediaUrlSigner(properties);
    }

    @Test
    void validateSecret_unresolvedPlaceholder_throws() {
        assertThatThrownBy(() -> signerWithSecret("${APP_MEDIA_SECRET}").validateSecret())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validateSecret_blank_throws() {
        assertThatThrownBy(() -> signerWithSecret("  ").validateSecret())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validateSecret_realValue_passes() {
        assertThatCode(() -> signer.validateSecret()).doesNotThrowAnyException();
    }

    @Test
    void signAndVerify_validSignature_returnsTrue() {
        long expiresAt = signer.expiresAt();
        String signature = signer.sign(IMAGE_ID, TENANT_ID, expiresAt);

        assertThat(signature).doesNotContain("=");
        assertThat(signer.verify(IMAGE_ID, TENANT_ID, expiresAt, signature)).isTrue();
    }

    @Test
    void verify_wrongImageId_returnsFalse() {
        long expiresAt = signer.expiresAt();
        String signature = signer.sign(IMAGE_ID, TENANT_ID, expiresAt);

        assertThat(signer.verify(13L, TENANT_ID, expiresAt, signature)).isFalse();
    }

    @Test
    void verify_wrongTenant_returnsFalse() {
        long expiresAt = signer.expiresAt();
        String signature = signer.sign(IMAGE_ID, TENANT_ID, expiresAt);

        assertThat(signer.verify(IMAGE_ID, 2L, expiresAt, signature)).isFalse();
    }

    @Test
    void verify_tamperedSignature_returnsFalse() {
        long expiresAt = signer.expiresAt();
        String signature = signer.sign(IMAGE_ID, TENANT_ID, expiresAt);

        assertThat(signer.verify(IMAGE_ID, TENANT_ID, expiresAt, signature + "x")).isFalse();
        assertThat(signer.verify(IMAGE_ID, TENANT_ID, expiresAt, "not-base64-$$$")).isFalse();
        assertThat(signer.verify(IMAGE_ID, TENANT_ID, expiresAt, null)).isFalse();
    }

    @Test
    void verify_expiredSignature_returnsFalse() {
        long expiredAt = Instant.now().getEpochSecond() - 5;
        String signature = signer.sign(IMAGE_ID, TENANT_ID, expiredAt);

        assertThat(signer.verify(IMAGE_ID, TENANT_ID, expiredAt, signature)).isFalse();
    }

    @Test
    void expiresAt_usesConfiguredTtl() {
        long now = Instant.now().getEpochSecond();
        long expiresAt = signer.expiresAt();

        assertThat(expiresAt).isBetween(now + 595, now + 605);
        assertThat(signer.urlTtlSeconds()).isEqualTo(600);
    }
}
