package com.saasoms.modules.storefront.controller;

import com.saasoms.common.exception.BusinessException;
import com.saasoms.common.exception.GlobalExceptionHandler;
import com.saasoms.common.response.PageResponse;
import com.saasoms.modules.storefront.dto.StorefrontProductDetailResponse;
import com.saasoms.modules.storefront.dto.StorefrontProductSummaryResponse;
import com.saasoms.modules.storefront.dto.StorefrontShopResponse;
import com.saasoms.modules.storefront.service.StorefrontService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class StorefrontControllerTest {

    private static final String BASE = "/api/public/shops/cua-hang-a";
    private static final List<String> INTERNAL_FIELDS = List.of("tenantId", "shopId", "skuCode", "stock",
            "totalStock", "externalId", "platformSource", "fileName", "fileSize", "status", "usedInOrders");

    @Mock
    private StorefrontService storefrontService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new StorefrontController(storefrontService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ------------------------------------------------------------------ SHOP

    @Test
    void shop_returnsContractShapeWithNullPhoneField() throws Exception {
        when(storefrontService.getShop("cua-hang-a")).thenReturn(
                StorefrontShopResponse.builder().slug("cua-hang-a").name("Cửa hàng A").build());

        ResultActions result = mockMvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.slug").value("cua-hang-a"))
                .andExpect(jsonPath("$.data.name").value("Cửa hàng A"))
                .andExpect(jsonPath("$.data.contactPhone").value(nullValue()))
                .andExpect(jsonPath("$.data.id").doesNotExist());
        assertNoInternalFields(result, "$.data");
    }

    @Test
    void shop_notFound_returns404WithoutDataField() throws Exception {
        when(storefrontService.getShop("khong-co"))
                .thenThrow(BusinessException.notFound("Không tìm thấy cửa hàng"));

        mockMvc.perform(get("/api/public/shops/khong-co"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Không tìm thấy cửa hàng"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void categories_returnsStringArray() throws Exception {
        when(storefrontService.getCategories("cua-hang-a")).thenReturn(List.of("Giày", "Thời trang"));

        mockMvc.perform(get(BASE + "/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0]").value("Giày"))
                .andExpect(jsonPath("$.data[1]").value("Thời trang"));
    }

    // --------------------------------------------------------------- PRODUCTS

    @Test
    void products_returnsContractShapeWithoutInternalFields() throws Exception {
        stubProducts(List.of(StorefrontProductSummaryResponse.builder()
                .id(12L)
                .name("Áo thun")
                .imageUrl("/api/public/shops/cua-hang-a/images/55")
                .minPrice(new BigDecimal("150000.00"))
                .maxPrice(new BigDecimal("180000.00"))
                .inStock(true)
                .build()));

        ResultActions result = mockMvc.perform(get(BASE + "/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(12))
                .andExpect(jsonPath("$.data.content[0].category").value(nullValue()))
                .andExpect(jsonPath("$.data.content[0].minPrice").value(150000.00))
                .andExpect(jsonPath("$.data.content[0].inStock").value(true))
                .andExpect(jsonPath("$.data.meta.size").value(20));
        assertNoInternalFields(result, "$.data.content[0]");
    }

    @Test
    void products_defaults_page0Size20SortUpdatedAtDescThenId() throws Exception {
        stubProducts(List.of());

        mockMvc.perform(get(BASE + "/products")).andExpect(status().isOk());

        Pageable pageable = capturedPageable();
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort()).isEqualTo(
                Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id")));
    }

    @Test
    void products_sizeAndPageOutOfRange_areClamped() throws Exception {
        stubProducts(List.of());

        mockMvc.perform(get(BASE + "/products").param("size", "1000").param("page", "-5"))
                .andExpect(status().isOk());

        Pageable pageable = capturedPageable();
        assertThat(pageable.getPageSize()).isEqualTo(100);
        assertThat(pageable.getPageNumber()).isZero();
    }

    @Test
    void products_sizeZero_clampedTo1() throws Exception {
        stubProducts(List.of());

        mockMvc.perform(get(BASE + "/products").param("size", "0")).andExpect(status().isOk());

        assertThat(capturedPageable().getPageSize()).isEqualTo(1);
    }

    @Test
    void products_hugePage_doesNotOverflowOffset() throws Exception {
        stubProducts(List.of());

        mockMvc.perform(get(BASE + "/products").param("size", "100").param("page", String.valueOf(Integer.MAX_VALUE)))
                .andExpect(status().isOk());

        assertThat(capturedPageable().getOffset()).isLessThanOrEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void products_sortOutsideWhitelist_fallsBack() throws Exception {
        for (String sort : new String[]{"price,asc", "skuCode", ",", "", "id,asc"}) {
            org.mockito.Mockito.clearInvocations(storefrontService);
            stubProducts(List.of());

            mockMvc.perform(get(BASE + "/products").param("sort", sort)).andExpect(status().isOk());

            assertThat(capturedPageable().getSort()).as(sort).isEqualTo(
                    Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id")));
        }
    }

    @Test
    void products_sortAllowed_appliesDirectionAndTieBreaker() throws Exception {
        stubProducts(List.of());

        mockMvc.perform(get(BASE + "/products").param("sort", "name,asc")).andExpect(status().isOk());

        assertThat(capturedPageable().getSort()).isEqualTo(
                Sort.by(Sort.Order.asc("name"), Sort.Order.desc("id")));
    }

    @Test
    void products_pageNotInteger_returns400() throws Exception {
        mockMvc.perform(get(BASE + "/products").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verifyNoInteractions(storefrontService);
    }

    @Test
    void products_sizeNotInteger_returns400() throws Exception {
        mockMvc.perform(get(BASE + "/products").param("size", "1.5"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(storefrontService);
    }

    @Test
    void products_passesSearchAndCategory() throws Exception {
        stubProducts(List.of());

        mockMvc.perform(get(BASE + "/products").param("search", "áo").param("category", "Thời trang"))
                .andExpect(status().isOk());

        verify(storefrontService).getProducts(eq("cua-hang-a"), eq("áo"), eq("Thời trang"), any());
    }

    // ----------------------------------------------------------------- DETAIL

    @Test
    void product_returnsContractShapeWithoutInternalFields() throws Exception {
        when(storefrontService.getProduct("cua-hang-a", 12L)).thenReturn(StorefrontProductDetailResponse.builder()
                .id(12L)
                .name("Áo thun")
                .imageUrl("/api/public/shops/cua-hang-a/images/55")
                .minPrice(new BigDecimal("150000.00"))
                .maxPrice(new BigDecimal("150000.00"))
                .inStock(true)
                .images(List.of(StorefrontProductDetailResponse.ImageItem.builder()
                        .id(55L).url("/api/public/shops/cua-hang-a/images/55").primary(true).build()))
                .skus(List.of(StorefrontProductDetailResponse.SkuItem.builder()
                        .id(7L).price(new BigDecimal("150000.00")).inStock(true).build()))
                .build());

        ResultActions result = mockMvc.perform(get(BASE + "/products/12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(12))
                .andExpect(jsonPath("$.data.description").value(nullValue()))
                .andExpect(jsonPath("$.data.category").value(nullValue()))
                .andExpect(jsonPath("$.data.images[0].id").value(55))
                .andExpect(jsonPath("$.data.images[0].url").value("/api/public/shops/cua-hang-a/images/55"))
                .andExpect(jsonPath("$.data.images[0].primary").value(true))
                .andExpect(jsonPath("$.data.skus[0].id").value(7))
                .andExpect(jsonPath("$.data.skus[0].variant").value(nullValue()))
                .andExpect(jsonPath("$.data.skus[0].price").value(150000.00))
                .andExpect(jsonPath("$.data.skus[0].inStock").value(true));
        assertNoInternalFields(result, "$.data");
        assertNoInternalFields(result, "$.data.images[0]");
        assertNoInternalFields(result, "$.data.skus[0]");
    }

    @Test
    void product_idNotNumber_returns400() throws Exception {
        mockMvc.perform(get(BASE + "/products/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verifyNoInteractions(storefrontService);
    }

    @Test
    void product_notFound_returns404() throws Exception {
        when(storefrontService.getProduct("cua-hang-a", 12L))
                .thenThrow(BusinessException.notFound("Không tìm thấy sản phẩm"));

        mockMvc.perform(get(BASE + "/products/12"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy sản phẩm"));
    }

    // ------------------------------------------------------------------ IMAGE

    @Test
    void image_returnsContentAndCacheHeaders() throws Exception {
        when(storefrontService.getImage("cua-hang-a", 55L)).thenReturn(
                new StorefrontService.StorefrontImage(new ByteArrayResource(new byte[]{1, 2, 3}), "image/webp"));

        mockMvc.perform(get(BASE + "/images/55"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/webp"))
                .andExpect(content().bytes(new byte[]{1, 2, 3}))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "public, max-age=3600"));
    }

    @Test
    void image_acceptImageWebp_returnsContent() throws Exception {
        when(storefrontService.getImage("cua-hang-a", 55L)).thenReturn(
                new StorefrontService.StorefrontImage(new ByteArrayResource(new byte[]{1}), "image/jpeg"));

        mockMvc.perform(get(BASE + "/images/55").accept("image/webp"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG));
    }

    @Test
    void image_invalidStoredContentType_fallsBackToOctetStream() throws Exception {
        when(storefrontService.getImage("cua-hang-a", 55L)).thenReturn(
                new StorefrontService.StorefrontImage(new ByteArrayResource(new byte[]{1}), "khong hop le"));

        mockMvc.perform(get(BASE + "/images/55"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM));
    }

    /** Thẻ img gửi Accept kiểu ảnh: thân lỗi JSON không được làm lượt thương lượng nội dung thất bại thành 500. */
    @Test
    void image_notFound_acceptImageWebp_returns404Json() throws Exception {
        when(storefrontService.getImage("cua-hang-a", 55L))
                .thenThrow(BusinessException.notFound("Không tìm thấy ảnh"));

        mockMvc.perform(get(BASE + "/images/55").accept("image/webp"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Không tìm thấy ảnh"));
    }

    @Test
    void image_idNotNumber_acceptImageWebp_returns400Json() throws Exception {
        mockMvc.perform(get(BASE + "/images/abc").accept("image/webp"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false));

        verifyNoInteractions(storefrontService);
    }

    // ----------------------------------------------------------------- HELPER

    private void stubProducts(List<StorefrontProductSummaryResponse> content) {
        when(storefrontService.getProducts(eq("cua-hang-a"), any(), any(), any()))
                .thenReturn(PageResponse.of(new PageImpl<>(content, PageRequest.of(0, 20), content.size())));
    }

    private Pageable capturedPageable() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(storefrontService).getProducts(eq("cua-hang-a"), isNull(), isNull(), captor.capture());
        return captor.getValue();
    }

    private static void assertNoInternalFields(ResultActions result, String root) throws Exception {
        for (String field : INTERNAL_FIELDS) {
            result.andExpect(jsonPath(root + "." + field).doesNotExist());
        }
    }
}
