package com.saasoms.common.media;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

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
