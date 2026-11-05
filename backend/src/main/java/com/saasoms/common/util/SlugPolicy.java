package com.saasoms.common.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Quy tắc địa chỉ cửa hàng dùng chung cho đăng ký, cài đặt và API công khai.
 */
public final class SlugPolicy {

    /**
     * Lookahead chặn độ dài trước khi vào nhóm lặp: Java khớp nhóm lặp bằng đệ quy, chuỗi rất dài
     * dạng "a-a-a-..." sẽ gây StackOverflowError nếu kiểm tra độ dài sau đó.
     */
    public static final String REGEX = "^(?=.{3,100}$)[a-z0-9]+(-[a-z0-9]+)*$";

    /** Cột slug dài 100; chừa chỗ cho hậu tố "-n" khi tên bị trùng. */
    private static final int MAX_GENERATED_LENGTH = 90;
    private static final int MIN_LENGTH = 3;
    private static final String FALLBACK = "shop";

    private static final Pattern VALID = Pattern.compile(REGEX);
    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");

    private SlugPolicy() {
    }

    public static boolean isValid(String slug) {
        return slug != null && VALID.matcher(slug).matches();
    }

    /** Sinh slug từ tên cửa hàng. Kết quả luôn hợp lệ theo {@link #REGEX}, chưa kiểm tra trùng. */
    public static String fromName(String name) {
        if (name == null) {
            return FALLBACK;
        }
        // "đ" không phải chữ có dấu tổ hợp trong Unicode nên NFD không tách được, phải đổi tay.
        String lower = name.toLowerCase(Locale.ROOT).replace('đ', 'd');
        String slug = trimDashes(COMBINING_MARKS.matcher(Normalizer.normalize(lower, Normalizer.Form.NFD))
                .replaceAll("")
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("[\\s-]+", "-"));

        if (slug.length() > MAX_GENERATED_LENGTH) {
            slug = trimDashes(slug.substring(0, MAX_GENERATED_LENGTH));
        }
        return slug.length() < MIN_LENGTH ? FALLBACK : slug;
    }

    private static String trimDashes(String value) {
        return value.replaceAll("^-|-$", "");
    }
}
