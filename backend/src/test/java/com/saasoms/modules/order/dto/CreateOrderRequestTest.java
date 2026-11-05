package com.saasoms.modules.order.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CreateOrderRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void shippingFee_tooManyDigits_isInvalid() {
        CreateOrderRequest request = validRequest();
        request.setShippingFee(new BigDecimal("12345678901.00"));

        assertThat(messages(request))
                .containsExactly("Phí vận chuyển tối đa 10 chữ số phần nguyên và 2 chữ số thập phân");
    }

    @Test
    void discount_threeDecimals_isInvalid() {
        CreateOrderRequest request = validRequest();
        request.setDiscount(new BigDecimal("1.234"));

        assertThat(messages(request))
                .containsExactly("Giảm giá tối đa 10 chữ số phần nguyên và 2 chữ số thập phân");
    }

    @Test
    void moneyAtLimit_isValid() {
        CreateOrderRequest request = validRequest();
        request.setShippingFee(new BigDecimal("9999999999.99"));
        request.setDiscount(new BigDecimal("9999999999.99"));

        assertThat(messages(request)).isEmpty();
    }

    private List<String> messages(CreateOrderRequest request) {
        Set<ConstraintViolation<CreateOrderRequest>> violations = validator.validate(request);
        return violations.stream().map(ConstraintViolation::getMessage).toList();
    }

    private CreateOrderRequest validRequest() {
        CreateOrderRequest.OrderItemRequest item = new CreateOrderRequest.OrderItemRequest();
        item.setSkuId(1L);
        item.setQuantity(1);

        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomerName("Nguyễn Văn A");
        request.setCustomerPhone("0901234567");
        request.setItems(List.of(item));
        return request;
    }
}
