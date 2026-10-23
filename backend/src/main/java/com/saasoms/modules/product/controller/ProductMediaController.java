package com.saasoms.modules.product.controller;

import com.saasoms.common.exception.BusinessException;
import com.saasoms.common.media.MediaUrlSigner;
import com.saasoms.modules.product.service.ProductImageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Phục vụ ảnh theo URL đã ký — không yêu cầu JWT.
 */
@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
@Tag(name = "Product Images")
public class ProductMediaController {

    private static final MediaType FALLBACK_TYPE = MediaType.APPLICATION_OCTET_STREAM;
    private static final String INVALID_LINK_MESSAGE = "Liên kết ảnh không hợp lệ hoặc đã hết hạn";

    private final ProductImageService productImageService;
    private final MediaUrlSigner mediaUrlSigner;

    @GetMapping("/products/{imageId}")
    @Operation(summary = "Tải nội dung ảnh sản phẩm bằng liên kết có chữ ký")
    public ResponseEntity<byte[]> serve(@PathVariable Long imageId,
                                        @RequestParam(required = false) String t,
                                        @RequestParam(required = false) String e,
                                        @RequestParam(required = false) String s) {
        long tenantId = parseTimestamp(t);
        long expiresAt = parseTimestamp(e);

        ProductImageService.MediaContent media =
                productImageService.serveSignedImage(imageId, tenantId, expiresAt, s);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(parseMediaType(media.contentType()));
        headers.set("X-Content-Type-Options", "nosniff");
        headers.setCacheControl(CacheControl.maxAge(mediaUrlSigner.urlTtlSeconds(), TimeUnit.SECONDS)
                .cachePrivate()
                .getHeaderValue());
        headers.setContentDisposition(ContentDisposition.inline()
                .filename(resolveFileName(media.fileName()), StandardCharsets.UTF_8)
                .build());

        return ResponseEntity.ok()
                .headers(headers)
                .body(media.content());
    }

    private static long parseTimestamp(String raw) {
        if (raw == null || raw.isBlank()) {
            throw BusinessException.forbidden(INVALID_LINK_MESSAGE);
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            throw BusinessException.forbidden(INVALID_LINK_MESSAGE);
        }
    }

    private static MediaType parseMediaType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return FALLBACK_TYPE;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (RuntimeException ex) {
            return FALLBACK_TYPE;
        }
    }

    private static String resolveFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "image";
        }
        return fileName;
    }
}
