package com.saasoms.common.media;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình lưu trữ ảnh cục bộ (app.media.*).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.media")
public class MediaProperties {

    /** Thư mục gốc lưu tệp ảnh, mặc định "uploads". */
    private String storageDir = "uploads";

    /** Khoá bí mật dùng ký URL ảnh (HMAC-SHA256). */
    private String signingSecret;

    /** Thời gian sống của URL ảnh đã ký, tính bằng giây. */
    private long urlTtlSeconds = 3600;
}
