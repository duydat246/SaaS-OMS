package com.saasoms.modules.product.controller;

import com.saasoms.common.exception.BusinessException;
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
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProductControllerTest {

    private static final String NULL_SKU_BODY = """
            {"name":"x","skus":[null]}
            """;
    private static final String NULL_SKU_MESSAGE = "Danh sách SKU không được chứa phần tử rỗng";

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

    @Test
    void list_sort_hasIdTieBreakerForStablePaging() throws Exception {
        when(productService.getProducts(any(), any(), any(), any(), any()))
                .thenReturn(emptyPageResponse());

        mockMvc.perform(get("/api/products").param("sort", "name,asc"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(productService).getProducts(any(), any(), any(), any(), pageableCaptor.capture());

        assertThat(pageableCaptor.getValue().getSort().getOrderFor("id")).isNotNull();
    }

    @Test
    void list_sizeZeroAndNegativePage_areClamped() throws Exception {
        when(productService.getProducts(any(), any(), any(), any(), any()))
                .thenReturn(emptyPageResponse());

        mockMvc.perform(get("/api/products").param("size", "0").param("page", "-1"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(productService).getProducts(any(), any(), any(), any(), pageableCaptor.capture());

        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(1);
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
    }

    @Test
    void list_hugePage_offsetStaysWithinIntegerRange() throws Exception {
        when(productService.getProducts(any(), any(), any(), any(), any()))
                .thenReturn(emptyPageResponse());

        mockMvc.perform(get("/api/products").param("page", "2147483647"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(productService).getProducts(any(), any(), any(), any(), pageableCaptor.capture());

        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
        assertThat(pageableCaptor.getValue().getOffset()).isLessThanOrEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void list_validPage_isUnchanged() throws Exception {
        when(productService.getProducts(any(), any(), any(), any(), any()))
                .thenReturn(emptyPageResponse());

        mockMvc.perform(get("/api/products").param("page", "3").param("size", "50"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(productService).getProducts(any(), any(), any(), any(), pageableCaptor.capture());

        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(3);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(50);
    }

    @Test
    void create_nullSkuElement_returns400() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(NULL_SKU_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(NULL_SKU_MESSAGE));
    }

    @Test
    void update_nullSkuElement_returns400() throws Exception {
        mockMvc.perform(put("/api/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(NULL_SKU_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(NULL_SKU_MESSAGE));
    }

    @Test
    void list_invalidStatus_returns400() throws Exception {
        mockMvc.perform(get("/api/products").param("status", "FOO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("status")));
    }

    @Test
    void get_nonNumericId_returns400() throws Exception {
        mockMvc.perform(get("/api/products/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void create_concurrentDuplicateSku_returns409() throws Exception {
        when(productService.createProduct(any(ProductRequest.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry"));

        String body = """
                {"name":"Áo","skus":[{"skuCode":"SKU-1","price":1,"stock":1}]}
                """;

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", not(containsString("Duplicate"))));
    }

    @Test
    void create_descriptionTooLong_returns400() throws Exception {
        String body = """
                {"name":"Áo","description":"%s","skus":[{"skuCode":"SKU-1","price":1,"stock":1}]}
                """.formatted("a".repeat(10_001));

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Mô tả")));
    }

    @Test
    void create_negativePriceOrStock_returns400() throws Exception {
        String body = """
                {"name":"Áo","skus":[{"skuCode":"SKU-1","price":-1,"stock":-1}]}
                """;

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void create_priceOverflowingDecimal12_2_returns400() throws Exception {
        String body = """
                {"name":"Áo","skus":[{"skuCode":"SKU-1","price":12345678901,"stock":1}]}
                """;

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Giá")));
    }

    // ---------------------------------------------------------- EXPECTED STOCK

    @Test
    void update_negativeExpectedStock_returns400() throws Exception {
        String body = """
                {"name":"Áo","skus":[{"id":1,"skuCode":"SKU-1","price":1,"stock":1,"expectedStock":-1}]}
                """;

        mockMvc.perform(put("/api/products/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Tồn kho gốc phải lớn hơn hoặc bằng 0"));

        verifyNoInteractions(productService);
    }

    @Test
    void update_expectedStockWrongType_returns400() throws Exception {
        String body = """
                {"name":"Áo","skus":[{"id":1,"skuCode":"SKU-1","price":1,"stock":1,"expectedStock":"abc"}]}
                """;

        mockMvc.perform(put("/api/products/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verifyNoInteractions(productService);
    }

    @Test
    void update_withoutExpectedStock_returns200() throws Exception {
        when(productService.updateProduct(eq(10L), any(ProductRequest.class))).thenReturn(sampleResponse());

        String body = """
                {"name":"Áo","skus":[{"id":1,"skuCode":"SKU-1","price":1,"stock":1}]}
                """;

        mockMvc.perform(put("/api/products/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        ArgumentCaptor<ProductRequest> captor = ArgumentCaptor.forClass(ProductRequest.class);
        verify(productService).updateProduct(eq(10L), captor.capture());
        assertThat(captor.getValue().getSkus().get(0).getExpectedStock()).isNull();
    }

    @Test
    void update_withExpectedStock_passesValueToService() throws Exception {
        when(productService.updateProduct(eq(10L), any(ProductRequest.class))).thenReturn(sampleResponse());

        String body = """
                {"name":"Áo","skus":[{"id":1,"skuCode":"SKU-1","price":1,"stock":1,"expectedStock":0}]}
                """;

        mockMvc.perform(put("/api/products/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        ArgumentCaptor<ProductRequest> captor = ArgumentCaptor.forClass(ProductRequest.class);
        verify(productService).updateProduct(eq(10L), captor.capture());
        assertThat(captor.getValue().getSkus().get(0).getExpectedStock()).isZero();
    }

    @Test
    void update_stockConflict_returns409WithMessage() throws Exception {
        String message = "Tồn kho đã thay đổi: \"SKU-1\" hiện còn 7 (lúc mở form là 10). "
                + "Vui lòng kiểm tra lại trước khi lưu.";
        when(productService.updateProduct(eq(10L), any(ProductRequest.class)))
                .thenThrow(BusinessException.conflict(message));

        String body = """
                {"name":"Áo","skus":[{"id":1,"skuCode":"SKU-1","price":1,"stock":10,"expectedStock":10}]}
                """;

        mockMvc.perform(put("/api/products/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(message));
    }

    @Test
    void update_lockFailure_returns409() throws Exception {
        when(productService.updateProduct(eq(10L), any(ProductRequest.class)))
                .thenThrow(new CannotAcquireLockException("Deadlock found when trying to get lock"));

        String body = """
                {"name":"Áo","skus":[{"id":1,"skuCode":"SKU-1","price":1,"stock":1}]}
                """;

        mockMvc.perform(put("/api/products/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message")
                        .value("Dữ liệu đang được cập nhật bởi thao tác khác, vui lòng thử lại"));
    }

    @Test
    void update_knownSkuIdsTooLong_returns400() throws Exception {
        String ids = java.util.stream.LongStream.rangeClosed(1, 1001)
                .mapToObj(Long::toString)
                .collect(java.util.stream.Collectors.joining(","));
        String body = """
                {"name":"Áo","skus":[{"id":1,"skuCode":"SKU-1","price":1,"stock":1}],"knownSkuIds":[%s]}
                """.formatted(ids);

        mockMvc.perform(put("/api/products/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Danh sách biến thể đã biết tối đa 1000 phần tử"));

        verifyNoInteractions(productService);
    }

    @Test
    void update_knownSkuIdsWithNullElement_passesToService() throws Exception {
        when(productService.updateProduct(eq(10L), any(ProductRequest.class))).thenReturn(sampleResponse());

        String body = """
                {"name":"Áo","skus":[{"id":1,"skuCode":"SKU-1","price":1,"stock":1}],"knownSkuIds":[1,null]}
                """;

        mockMvc.perform(put("/api/products/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
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
