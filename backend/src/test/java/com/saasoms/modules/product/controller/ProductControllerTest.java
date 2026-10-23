package com.saasoms.modules.product.controller;

import com.saasoms.common.exception.GlobalExceptionHandler;
import com.saasoms.common.response.PageResponse;
import com.saasoms.modules.product.dto.ProductRequest;
import com.saasoms.modules.product.dto.ProductResponse;
import com.saasoms.modules.product.entity.ProductStatus;
import com.saasoms.modules.product.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProductControllerTest {

    @Mock
    private ProductService productService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ProductController controller = new ProductController(productService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void create_invalidBody_returns400() throws Exception {
        String body = """
                {"description":"Thiếu tên và SKU"}
                """;

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("không được để trống")));
    }

    @Test
    void create_validBody_returns201() throws Exception {
        when(productService.createProduct(any(ProductRequest.class))).thenReturn(sampleResponse());

        String body = """
                {
                  "name": "Áo thun nam",
                  "description": "Áo cotton",
                  "category": "Áo",
                  "platformSource": "MANUAL",
                  "imageUrl": "https://example.com/ao.jpg",
                  "skus": [
                    {"skuCode": "SKU-1", "variant": "M", "price": 100000, "stock": 5}
                  ]
                }
                """;

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Đã tạo sản phẩm"))
                .andExpect(jsonPath("$.data.id").value(10));

        verify(productService).createProduct(any(ProductRequest.class));
    }

    @Test
    void list_sizeOver100_isClamped() throws Exception {
        when(productService.getProducts(any(), any(), any(), any(), any()))
                .thenReturn(emptyPageResponse());

        mockMvc.perform(get("/api/products").param("size", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(productService).getProducts(any(), any(), any(), any(), pageableCaptor.capture());

        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void list_invalidSortField_fallsBackToUpdatedAt() throws Exception {
        when(productService.getProducts(any(), any(), any(), any(), any()))
                .thenReturn(emptyPageResponse());

        mockMvc.perform(get("/api/products").param("sort", "hack,asc"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(productService).getProducts(any(), any(), any(), any(), pageableCaptor.capture());

        Sort.Order order = pageableCaptor.getValue().getSort().getOrderFor("updatedAt");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void list_validSort_asc_isRespected() throws Exception {
        when(productService.getProducts(any(), any(), any(), any(), any()))
                .thenReturn(emptyPageResponse());

        mockMvc.perform(get("/api/products").param("sort", "name,asc"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(productService).getProducts(any(), any(), any(), any(), pageableCaptor.capture());

        Sort.Order order = pageableCaptor.getValue().getSort().getOrderFor("name");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    // ---------------------------------------------------------------- FIXTURE

    private PageResponse<ProductResponse> emptyPageResponse() {
        return PageResponse.of(new PageImpl<ProductResponse>(List.of()));
    }

    private ProductResponse sampleResponse() {
        return ProductResponse.builder()
                .id(10L)
                .name("Áo thun nam")
                .category("Áo")
                .platformSource("MANUAL")
                .status(ProductStatus.ACTIVE)
                .skuCount(1)
                .totalStock(5)
                .skus(List.of())
                .build();
    }
}
