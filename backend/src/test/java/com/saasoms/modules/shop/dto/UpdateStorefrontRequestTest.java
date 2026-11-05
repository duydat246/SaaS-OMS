package com.saasoms.modules.shop.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateStorefrontRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void validRequest_noViolations() {
        assertThat(validator.validate(request(true, "cua-hang-a", "0901234567"))).isEmpty();
    }

    @Test
    void contactPhone_nullEmptyOrPlusPrefix_isAccepted() {
        assertThat(validator.validate(request(true, "cua-hang-a", null))).isEmpty();
        assertThat(validator.validate(request(true, "cua-hang-a", ""))).isEmpty();
        assertThat(validator.validate(request(true, "cua-hang-a", "+84901234567"))).isEmpty();
    }

    @Test
    void contactPhone_invalid_isRejected() {
        for (String phone : new String[]{"1234567", "1234567890123456", "090 123 4567", "abc", " ", "0901234567\n"}) {
            assertThat(fields(request(true, "cua-hang-a", phone))).as(phone).containsExactly("contactPhone");
        }
    }

    @Test
    void storefrontEnabled_null_isRejected() {
        assertThat(fields(request(null, "cua-hang-a", null))).containsExactly("storefrontEnabled");
    }

    @Test
    void slug_invalid_isRejected() {
        for (String slug : new String[]{"ab", "-1", "abc-", "a--b", "ABC", "cửa-hàng", "a".repeat(101)}) {
            assertThat(fields(request(true, slug, null))).as(slug).containsExactly("slug");
        }
        assertThat(fields(request(true, null, null))).containsExactly("slug");
    }

    @Test
    void slug_veryLongRepeatedGroup_isRejectedWithoutStackOverflow() {
        assertThat(fields(request(true, "a-".repeat(500_000) + "a", null))).containsExactly("slug");
    }

    @Test
    void messages_areVietnamese() {
        Set<ConstraintViolation<UpdateStorefrontRequest>> violations =
                validator.validate(request(true, "ABC", "abc"));

        assertThat(violations).extracting(ConstraintViolation::getMessage).containsExactlyInAnyOrder(
                "Địa chỉ cửa hàng dài 3-100 ký tự, chỉ gồm chữ thường không dấu, số và dấu gạch ngang",
                "Số điện thoại liên hệ gồm 8-15 chữ số, có thể bắt đầu bằng dấu +");
    }

    private Set<String> fields(UpdateStorefrontRequest request) {
        return validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    private static UpdateStorefrontRequest request(Boolean enabled, String slug, String phone) {
        UpdateStorefrontRequest request = new UpdateStorefrontRequest();
        request.setStorefrontEnabled(enabled);
        request.setSlug(slug);
        request.setContactPhone(phone);
        return request;
    }
}
