package com.saasoms.modules.auth.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RegisterRequestTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();
    private static final String PASSWORD_MESSAGE = "Mật khẩu phải có từ 8 đến 72 ký tự";

    private static RegisterRequest valid() {
        RegisterRequest request = new RegisterRequest();
        request.setShopName("Shop test");
        request.setEmail("a@example.com");
        request.setPassword("12345678");
        request.setFullName("Người dùng");
        return request;
    }

    private static List<String> messages(Object request) {
        return VALIDATOR.validate(request).stream().map(ConstraintViolation::getMessage).toList();
    }

    @Test
    void password_sevenChars_rejected() {
        RegisterRequest request = valid();
        request.setPassword("1234567");

        assertThat(messages(request)).containsExactly(PASSWORD_MESSAGE);
    }

    @Test
    void password_eightChars_accepted() {
        assertThat(messages(valid())).isEmpty();
    }

    @Test
    void password_seventyTwoChars_accepted() {
        RegisterRequest request = valid();
        request.setPassword("a".repeat(72));

        assertThat(messages(request)).isEmpty();
    }

    @Test
    void password_seventyThreeChars_rejected() {
        RegisterRequest request = valid();
        request.setPassword("a".repeat(73));

        assertThat(messages(request)).containsExactly(PASSWORD_MESSAGE);
    }

    @Test
    void email_151Chars_rejected() {
        RegisterRequest request = valid();
        request.setEmail("a".repeat(60) + "@" + "b".repeat(60) + "." + "c".repeat(29));

        assertThat(request.getEmail()).hasSize(151);
        assertThat(messages(request)).containsExactly("Email tối đa 150 ký tự");
    }

    @Test
    void fullName_101Chars_rejected() {
        RegisterRequest request = valid();
        request.setFullName("a".repeat(101));

        assertThat(messages(request)).containsExactly("Họ tên tối đa 100 ký tự");
    }

    @Test
    void platform_21Chars_rejected() {
        RegisterRequest request = valid();
        request.setPlatform("a".repeat(21));

        assertThat(messages(request)).containsExactly("Nền tảng tối đa 20 ký tự");
    }

    @Test
    void loginRequest_email151Chars_rejected() {
        LoginRequest request = new LoginRequest();
        request.setEmail("a".repeat(60) + "@" + "b".repeat(60) + "." + "c".repeat(29));
        request.setPassword("abc123");

        assertThat(messages(request)).containsExactly("Email tối đa 150 ký tự");
    }

    @Test
    void loginRequest_shortPassword_accepted() {
        LoginRequest request = new LoginRequest();
        request.setEmail("a@example.com");
        request.setPassword("abc123");

        assertThat(messages(request)).isEmpty();
    }
}
