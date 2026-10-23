package com.saasoms.common.media;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

/**
 * Ký và xác thực URL ảnh bằng HMAC-SHA256.
 * Chuỗi cần ký: {@code imageId:tenantId:expiresAtEpochSecond}.
 */
@Component
@RequiredArgsConstructor
public class MediaUrlSigner {

    private static final String ALGORITHM = "HmacSHA256";
    private static final long DEFAULT_TTL_SECONDS = 3600L;

    private final MediaProperties mediaProperties;

    public String sign(Long imageId, Long tenantId, long expiresAtEpochSecond) {
        return encode(hmac(payload(imageId, tenantId, expiresAtEpochSecond)));
    }

    public boolean verify(Long imageId, Long tenantId, long expiresAtEpochSecond, String signature) {
        if (imageId == null || tenantId == null || signature == null || signature.isBlank()) {
            return false;
        }
        if (expiresAtEpochSecond <= Instant.now().getEpochSecond()) {
            return false;
        }

        byte[] provided;
        try {
            provided = Base64.getUrlDecoder().decode(signature.trim());
        } catch (IllegalArgumentException ex) {
            return false;
        }

        byte[] expected = hmac(payload(imageId, tenantId, expiresAtEpochSecond));
        return MessageDigest.isEqual(expected, provided);
    }

    /** Thời điểm hết hạn (epoch giây) tính từ cấu hình TTL. */
    public long expiresAt() {
        return Instant.now().getEpochSecond() + urlTtlSeconds();
    }

    public long urlTtlSeconds() {
        long configured = mediaProperties.getUrlTtlSeconds();
        return configured > 0 ? configured : DEFAULT_TTL_SECONDS;
    }

    private static String payload(Long imageId, Long tenantId, long expiresAtEpochSecond) {
        return imageId + ":" + tenantId + ":" + expiresAtEpochSecond;
    }

    private String encode(byte[] signature) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
    }

    private byte[] hmac(String payload) {
        byte[] secret = secretBytes();
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Không ký được liên kết ảnh", ex);
        }
    }

    private byte[] secretBytes() {
        String secret = mediaProperties.getSigningSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("Thiếu cấu hình app.media.signing-secret");
        }
        return secret.getBytes(StandardCharsets.UTF_8);
    }
}
