package com.saasoms.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test standalone MockMvc không nạp application.yml, nên cấu hình Jackson được kiểm tra riêng ở đây,
 * dựng ObjectMapper từ đúng file cấu hình đang chạy.
 */
class JacksonConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class));

    record Body(Integer quantity, Long id) {
    }

    @Test
    void fractionalInteger_isRejected() {
        runner.run(context -> {
            ObjectMapper mapper = context.getBean(ObjectMapper.class);
            assertThatThrownBy(() -> mapper.readValue("{\"quantity\":1.5}", Body.class))
                    .isInstanceOf(MismatchedInputException.class);
            assertThatThrownBy(() -> mapper.readValue("{\"id\":1.5}", Body.class))
                    .isInstanceOf(MismatchedInputException.class);
        });
    }

    @Test
    void wholeNumberWrittenAsFloat_isRejected() {
        runner.run(context -> {
            ObjectMapper mapper = context.getBean(ObjectMapper.class);
            assertThatThrownBy(() -> mapper.readValue("{\"quantity\":1.0}", Body.class))
                    .isInstanceOf(MismatchedInputException.class);
        });
    }

    @Test
    void integer_isAccepted() {
        runner.run(context -> {
            ObjectMapper mapper = context.getBean(ObjectMapper.class);
            assertThat(mapper.readValue("{\"quantity\":2}", Body.class).quantity()).isEqualTo(2);
        });
    }
}
