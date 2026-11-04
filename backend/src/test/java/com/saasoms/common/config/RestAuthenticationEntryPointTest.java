package com.saasoms.common.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class RestAuthenticationEntryPointTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void commence_writesApiResponse401Utf8() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new RestAuthenticationEntryPoint(objectMapper).commence(
                new MockHttpServletRequest(), response, new InsufficientAuthenticationException("thiếu token"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).contains("application/json");
        assertThat(response.getCharacterEncoding()).isEqualToIgnoringCase("UTF-8");

        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(body.get("success").asBoolean()).isFalse();
        assertThat(body.get("message").asText())
                .isEqualTo("Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại");
        assertThat(body.has("data")).isFalse();
        // Byte thô phải là UTF-8, không phải ISO-8859-1 mặc định của servlet.
        assertThat(new String(response.getContentAsByteArray(), StandardCharsets.UTF_8))
                .contains("Phiên đăng nhập");
    }
}
