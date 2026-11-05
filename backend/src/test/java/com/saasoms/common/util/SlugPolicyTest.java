package com.saasoms.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlugPolicyTest {

    @Test
    void fromName_vietnameseName_removesAccents() {
        assertThat(SlugPolicy.fromName("Cửa Hàng Đẹp")).isEqualTo("cua-hang-dep");
    }

    @Test
    void fromName_tooShortAfterNormalize_fallsBackToShop() {
        assertThat(SlugPolicy.fromName("Đồ")).isEqualTo("shop");
    }

    @Test
    void fromName_threeCharsAfterNormalize_keepsValue() {
        assertThat(SlugPolicy.fromName("Đồng")).isEqualTo("dong");
    }

    @Test
    void fromName_onlySpecialChars_fallsBackToShop() {
        assertThat(SlugPolicy.fromName("!!! @@@ ###")).isEqualTo("shop");
    }

    @Test
    void fromName_blankOrNull_fallsBackToShop() {
        assertThat(SlugPolicy.fromName("   ")).isEqualTo("shop");
        assertThat(SlugPolicy.fromName(null)).isEqualTo("shop");
    }

    @Test
    void fromName_100Chars_cutsTo90AndStaysValid() {
        String slug = SlugPolicy.fromName("a".repeat(100));

        assertThat(slug).hasSize(90);
        assertThat(SlugPolicy.isValid(slug)).isTrue();
    }

    @Test
    void fromName_cutEndsAtDash_removesTrailingDash() {
        String slug = SlugPolicy.fromName("a".repeat(89) + " " + "b".repeat(10));

        assertThat(slug).isEqualTo("a".repeat(89));
    }

    @Test
    void fromName_repeatedSeparators_collapsesToOneDash() {
        assertThat(SlugPolicy.fromName("  Áo -- thun & Quần  ")).isEqualTo("ao-thun-quan");
    }

    @Test
    void isValid_acceptsValidSlug() {
        assertThat(SlugPolicy.isValid("cua-hang-a")).isTrue();
        assertThat(SlugPolicy.isValid("abc")).isTrue();
        assertThat(SlugPolicy.isValid("a".repeat(100))).isTrue();
    }

    @Test
    void isValid_rejectsInvalidSlug() {
        assertThat(SlugPolicy.isValid(null)).isFalse();
        assertThat(SlugPolicy.isValid("")).isFalse();
        assertThat(SlugPolicy.isValid("ab")).isFalse();
        assertThat(SlugPolicy.isValid("-1")).isFalse();
        assertThat(SlugPolicy.isValid("-abc")).isFalse();
        assertThat(SlugPolicy.isValid("abc-")).isFalse();
        assertThat(SlugPolicy.isValid("a--b")).isFalse();
        assertThat(SlugPolicy.isValid("ABC")).isFalse();
        assertThat(SlugPolicy.isValid("cửa-hàng")).isFalse();
        assertThat(SlugPolicy.isValid("abc\n")).isFalse();
        assertThat(SlugPolicy.isValid("a".repeat(101))).isFalse();
    }

    /** Nhóm lặp trong regex khớp bằng đệ quy; chuỗi dài phải bị chặn trước, không được tràn stack. */
    @Test
    void isValid_veryLongRepeatedGroup_returnsFalseWithoutStackOverflow() {
        assertThat(SlugPolicy.isValid("a-".repeat(500_000) + "a")).isFalse();
    }
}
