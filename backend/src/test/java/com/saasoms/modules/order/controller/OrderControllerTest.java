package com.saasoms.modules.order.controller;

import com.saasoms.common.exception.GlobalExceptionHandler;
import com.saasoms.modules.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    @Mock
    private OrderService orderService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new OrderController(orderService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void list_sizeOver100_isClampedTo100() throws Exception {
        assertThat(pageableFor("/api/orders?size=101").getPageSize()).isEqualTo(100);
    }

    @Test
    void list_sizeZero_isClampedTo1() throws Exception {
        assertThat(pageableFor("/api/orders?size=0").getPageSize()).isEqualTo(1);
    }

    @Test
    void list_negativePage_isClampedTo0() throws Exception {
        assertThat(pageableFor("/api/orders?page=-1").getPageNumber()).isZero();
    }

    @Test
    void list_defaultSort_hasIdTieBreaker() throws Exception {
        assertThat(pageableFor("/api/orders").getSort())
                .isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
    }

    @Test
    void list_hugePage_doesNotOverflowOffset() throws Exception {
        Pageable pageable = pageableFor("/api/orders?page=2147483647&size=100");

        assertThat(pageable.getOffset()).isLessThanOrEqualTo(Integer.MAX_VALUE);
    }

    private Pageable pageableFor(String url) throws Exception {
        mockMvc.perform(get(url)).andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(orderService).getOrders(isNull(), isNull(), captor.capture());
        return captor.getValue();
    }
}
