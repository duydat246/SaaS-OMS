package com.saasoms.modules.storefront.service;

import com.saasoms.common.exception.BusinessException;
import com.saasoms.common.media.LocalMediaStorage;
import com.saasoms.common.response.PageResponse;
import com.saasoms.common.tenant.TenantContext;
import com.saasoms.modules.product.entity.Product;
import com.saasoms.modules.product.entity.ProductImage;
import com.saasoms.modules.product.entity.ProductStatus;
import com.saasoms.modules.product.entity.Sku;
import com.saasoms.modules.product.repository.ProductImageRepository;
import com.saasoms.modules.product.repository.ProductRepository;
import com.saasoms.modules.product.repository.SkuRepository;
import com.saasoms.modules.shop.entity.Shop;
import com.saasoms.modules.shop.repository.ShopRepository;
import com.saasoms.modules.storefront.dto.StorefrontProductDetailResponse;
import com.saasoms.modules.storefront.dto.StorefrontProductSummaryResponse;
import com.saasoms.modules.storefront.dto.StorefrontShopResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StorefrontServiceTest {

    private static final Long SHOP_ID = 1L;
    private static final Long LOGGED_IN_TENANT_ID = 99L;
    private static final String SLUG = "cua-hang-a";
    private static final Pageable PAGEABLE = PageRequest.of(0, 20);

    @Mock
    private ShopRepository shopRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private SkuRepository skuRepository;
    @Mock
    private ProductImageRepository productImageRepository;
    @Mock
    private LocalMediaStorage localMediaStorage;

    @InjectMocks
    private StorefrontService storefrontService;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ------------------------------------------------------------------ SHOP

    @Test
    void getShop_published_returnsPublicFields() {
        stubPublishedShop();

        StorefrontShopResponse response = storefrontService.getShop(SLUG);

        assertThat(response.getSlug()).isEqualTo(SLUG);
        assertThat(response.getName()).isEqualTo("Cửa hàng A");
        assertThat(response.getContactPhone()).isEqualTo("0901234567");
    }

    @Test
    void getShop_unknownSlug_throws404() {
        when(shopRepository.findBySlug(SLUG)).thenReturn(Optional.empty());

        assertShopNotFound(() -> storefrontService.getShop(SLUG));
    }

    @Test
    void getShop_disabled_throws404() {
        Shop shop = publishedShop();
        shop.setStorefrontEnabled(false);
        when(shopRepository.findBySlug(SLUG)).thenReturn(Optional.of(shop));

        assertShopNotFound(() -> storefrontService.getShop(SLUG));
    }

    @Test
    void getShop_inactive_throws404() {
        Shop shop = publishedShop();
        shop.setStatus("SUSPENDED");
        when(shopRepository.findBySlug(SLUG)).thenReturn(Optional.of(shop));

        assertShopNotFound(() -> storefrontService.getShop(SLUG));
    }

    @Test
    void getShop_invalidSlug_throws404WithoutQuery() {
        for (String slug : new String[]{"ab", "-1", "ABC", "a--b", "a".repeat(101), "a-".repeat(500_000) + "a"}) {
            assertShopNotFound(() -> storefrontService.getShop(slug));
        }

        verifyNoInteractions(shopRepository);
    }

    // ------------------------------------------------------------- CATEGORIES

    @Test
    void getCategories_usesSlugShop() {
        stubPublishedShop();
        when(productRepository.findPublishedCategories(SHOP_ID)).thenReturn(List.of("Giày", "Thời trang"));

        assertThat(storefrontService.getCategories(SLUG)).containsExactly("Giày", "Thời trang");
    }

    @Test
    void getCategories_disabledShop_throws404WithoutQuery() {
        Shop shop = publishedShop();
        shop.setStorefrontEnabled(false);
        when(shopRepository.findBySlug(SLUG)).thenReturn(Optional.of(shop));

        assertShopNotFound(() -> storefrontService.getCategories(SLUG));

        verifyNoInteractions(productRepository);
    }

    // --------------------------------------------------------------- PRODUCTS

    /** Chủ shop khác đang đăng nhập xem trang này: context là tenant của họ, dữ liệu vẫn phải của slug. */
    @Test
    void getProducts_otherTenantContext_usesSlugShop() {
        TenantContext.set(LOGGED_IN_TENANT_ID);
        stubPublishedShop();
        stubProductPage(List.of(product(12L, null)));

        storefrontService.getProducts(SLUG, null, null, PAGEABLE);

        verify(productRepository).searchPublished(SHOP_ID, null, null, PAGEABLE);
        verify(skuRepository).findByShopIdAndProductIdIn(SHOP_ID, List.of(12L));
        verify(productImageRepository)
                .findByShopIdAndProductIdInOrderBySortOrderAscIdAsc(SHOP_ID, List.of(12L));
        verify(productRepository, never()).searchPublished(eq(LOGGED_IN_TENANT_ID), any(), any(), any());
        verify(skuRepository, never()).findByShopIdAndProductIdIn(eq(LOGGED_IN_TENANT_ID), any());
        verify(productImageRepository, never())
                .findByShopIdAndProductIdInOrderBySortOrderAscIdAsc(eq(LOGGED_IN_TENANT_ID), any());
    }

    @Test
    void getCategories_otherTenantContext_usesSlugShop() {
        TenantContext.set(LOGGED_IN_TENANT_ID);
        stubPublishedShop();
        when(productRepository.findPublishedCategories(SHOP_ID)).thenReturn(List.of("Giày"));

        assertThat(storefrontService.getCategories(SLUG)).containsExactly("Giày");

        verify(productRepository, never()).findPublishedCategories(LOGGED_IN_TENANT_ID);
    }

    @Test
    void getImage_otherTenantContext_usesSlugShop() {
        TenantContext.set(LOGGED_IN_TENANT_ID);
        stubPublishedShop();
        when(productImageRepository.findPublishedByIdAndShopId(55L, SHOP_ID))
                .thenReturn(Optional.of(image(55L, product(12L, null), true)));
        when(localMediaStorage.openResource("1/a.jpg"))
                .thenReturn(Optional.of(new ByteArrayResource(new byte[]{1})));

        assertThat(storefrontService.getImage(SLUG, 55L).contentType()).isEqualTo("image/jpeg");

        verify(productImageRepository, never()).findPublishedByIdAndShopId(55L, LOGGED_IN_TENANT_ID);
    }

    @Test
    void getProducts_disabledShop_throws404WithoutQuery() {
        Shop shop = publishedShop();
        shop.setStorefrontEnabled(false);
        when(shopRepository.findBySlug(SLUG)).thenReturn(Optional.of(shop));

        assertShopNotFound(() -> storefrontService.getProducts(SLUG, null, null, PAGEABLE));

        verifyNoInteractions(productRepository, skuRepository, productImageRepository);
    }

    @Test
    void getProducts_mapsInStockWithoutStockCount() {
        stubPublishedShop();
        Product product = product(12L, null);
        stubProductPage(List.of(product));
        when(skuRepository.findByShopIdAndProductIdIn(SHOP_ID, List.of(12L))).thenReturn(List.of(
                sku(7L, product, "180000.00", 0),
                sku(8L, product, "150000.00", 3)));

        StorefrontProductSummaryResponse item = onlyItem();

        assertThat(item.isInStock()).isTrue();
        assertThat(item.getMinPrice()).isEqualByComparingTo("150000.00");
        assertThat(item.getMaxPrice()).isEqualByComparingTo("180000.00");
    }

    @Test
    void getProducts_allSkusOutOfStock_inStockFalse() {
        stubPublishedShop();
        Product product = product(12L, null);
        stubProductPage(List.of(product));
        when(skuRepository.findByShopIdAndProductIdIn(SHOP_ID, List.of(12L)))
                .thenReturn(List.of(sku(7L, product, "0.00", 0)));

        StorefrontProductSummaryResponse item = onlyItem();

        assertThat(item.isInStock()).isFalse();
        assertThat(item.getMinPrice()).isEqualByComparingTo("0");
    }

    @Test
    void getProducts_noSkus_pricesNullAndOutOfStock() {
        stubPublishedShop();
        stubProductPage(List.of(product(12L, null)));

        StorefrontProductSummaryResponse item = onlyItem();

        assertThat(item.getMinPrice()).isNull();
        assertThat(item.getMaxPrice()).isNull();
        assertThat(item.isInStock()).isFalse();
    }

    @Test
    void getProducts_primaryImage_usedForImageUrl() {
        stubPublishedShop();
        Product product = product(12L, "https://cdn.example.com/cu.jpg");
        stubProductPage(List.of(product));
        when(productImageRepository.findByShopIdAndProductIdInOrderBySortOrderAscIdAsc(SHOP_ID, List.of(12L)))
                .thenReturn(List.of(image(54L, product, false), image(55L, product, true)));

        assertThat(onlyItem().getImageUrl()).isEqualTo("/api/public/shops/cua-hang-a/images/55");
    }

    @Test
    void getProducts_noPrimaryImage_usesFirstImage() {
        stubPublishedShop();
        Product product = product(12L, null);
        stubProductPage(List.of(product));
        when(productImageRepository.findByShopIdAndProductIdInOrderBySortOrderAscIdAsc(SHOP_ID, List.of(12L)))
                .thenReturn(List.of(image(54L, product, false), image(55L, product, false)));

        assertThat(onlyItem().getImageUrl()).isEqualTo("/api/public/shops/cua-hang-a/images/54");
    }

    @Test
    void getProducts_legacyAbsoluteImageUrl_isKept() {
        stubPublishedShop();
        stubProductPage(List.of(product(12L, "https://cdn.example.com/cu.jpg")));

        assertThat(onlyItem().getImageUrl()).isEqualTo("https://cdn.example.com/cu.jpg");
    }

    @Test
    void getProducts_legacyRelativeImageUrl_returnsNull() {
        for (String legacy : new String[]{"/api/media/products/1?t=1", "javascript:alert(1)", "uploads/a.jpg"}) {
            stubPublishedShop();
            stubProductPage(List.of(product(12L, legacy)));

            assertThat(onlyItem().getImageUrl()).as(legacy).isNull();
        }
    }

    @Test
    void getProducts_batchQueries_calledOncePerPage() {
        stubPublishedShop();
        Product first = product(12L, null);
        Product second = product(13L, null);
        stubProductPage(List.of(first, second));
        when(skuRepository.findByShopIdAndProductIdIn(SHOP_ID, List.of(12L, 13L))).thenReturn(List.of(
                sku(7L, first, "100.00", 1),
                sku(8L, second, "200.00", 0)));

        PageResponse<StorefrontProductSummaryResponse> page =
                storefrontService.getProducts(SLUG, null, null, PAGEABLE);

        assertThat(page.getContent()).extracting(StorefrontProductSummaryResponse::isInStock)
                .containsExactly(true, false);
        verify(skuRepository, times(1)).findByShopIdAndProductIdIn(SHOP_ID, List.of(12L, 13L));
        verify(productImageRepository, times(1))
                .findByShopIdAndProductIdInOrderBySortOrderAscIdAsc(SHOP_ID, List.of(12L, 13L));
    }

    @Test
    void getProducts_emptyPage_skipsBatchQueries() {
        stubPublishedShop();
        stubProductPage(List.of());

        assertThat(storefrontService.getProducts(SLUG, null, null, PAGEABLE).getContent()).isEmpty();

        verifyNoInteractions(skuRepository, productImageRepository);
    }

    @Test
    void getProducts_search_escapesLikeWildcards() {
        stubPublishedShop();
        when(productRepository.searchPublished(SHOP_ID, null, "50!%!_a!!\\", PAGEABLE))
                .thenReturn(new PageImpl<>(List.of(), PAGEABLE, 0));

        storefrontService.getProducts(SLUG, "  50%_a!\\  ", null, PAGEABLE);

        verify(productRepository).searchPublished(SHOP_ID, null, "50!%!_a!!\\", PAGEABLE);
    }

    @Test
    void getProducts_longSearch_cutTo100Chars() {
        stubPublishedShop();
        String cut = "a".repeat(100);
        when(productRepository.searchPublished(SHOP_ID, null, cut, PAGEABLE))
                .thenReturn(new PageImpl<>(List.of(), PAGEABLE, 0));

        storefrontService.getProducts(SLUG, "a".repeat(300), null, PAGEABLE);

        verify(productRepository).searchPublished(SHOP_ID, null, cut, PAGEABLE);
    }

    /** Cắt sau khi escape có thể bỏ lại ký tự thoát lẻ cuối chuỗi, làm MySQL báo lỗi LIKE. */
    @Test
    void getProducts_longSearchOfWildcards_cutsBeforeEscaping() {
        stubPublishedShop();
        String escaped = "!%".repeat(100);
        when(productRepository.searchPublished(SHOP_ID, null, escaped, PAGEABLE))
                .thenReturn(new PageImpl<>(List.of(), PAGEABLE, 0));

        storefrontService.getProducts(SLUG, "%".repeat(300), null, PAGEABLE);

        verify(productRepository).searchPublished(SHOP_ID, null, escaped, PAGEABLE);
    }

    @Test
    void getProducts_blankFilters_treatedAsAbsent() {
        stubPublishedShop();
        stubProductPage(List.of());

        storefrontService.getProducts(SLUG, "   ", " ", PAGEABLE);

        verify(productRepository).searchPublished(SHOP_ID, null, null, PAGEABLE);
    }

    // ----------------------------------------------------------------- DETAIL

    @Test
    void getProduct_otherShop_throws404() {
        stubPublishedShop();
        when(productRepository.findByIdAndShopId(12L, SHOP_ID)).thenReturn(Optional.empty());

        assertProductNotFound();
    }

    @Test
    void getProduct_inactive_throws404() {
        assertStatusHidden(ProductStatus.INACTIVE);
    }

    @Test
    void getProduct_archived_throws404() {
        assertStatusHidden(ProductStatus.ARCHIVED);
    }

    @Test
    void getProduct_disabledShop_throws404WithoutQuery() {
        Shop shop = publishedShop();
        shop.setStorefrontEnabled(false);
        when(shopRepository.findBySlug(SLUG)).thenReturn(Optional.of(shop));

        assertShopNotFound(() -> storefrontService.getProduct(SLUG, 12L));

        verifyNoInteractions(productRepository, skuRepository, productImageRepository);
    }

    @Test
    void getProduct_otherTenantContext_usesSlugShop() {
        TenantContext.set(LOGGED_IN_TENANT_ID);
        stubPublishedShop();
        when(productRepository.findByIdAndShopId(12L, SHOP_ID)).thenReturn(Optional.of(product(12L, null)));

        assertThat(storefrontService.getProduct(SLUG, 12L).getId()).isEqualTo(12L);

        verify(skuRepository).findByShopIdAndProductId(SHOP_ID, 12L);
        verify(productImageRepository).findByShopIdAndProductIdOrderBySortOrderAscIdAsc(SHOP_ID, 12L);
        verify(productRepository, never()).findByIdAndShopId(12L, LOGGED_IN_TENANT_ID);
        verify(skuRepository, never()).findByShopIdAndProductId(LOGGED_IN_TENANT_ID, 12L);
        verify(productImageRepository, never())
                .findByShopIdAndProductIdOrderBySortOrderAscIdAsc(LOGGED_IN_TENANT_ID, 12L);
    }

    @Test
    void getProduct_mapsSkusInStock() {
        stubPublishedShop();
        Product product = product(12L, null);
        when(productRepository.findByIdAndShopId(12L, SHOP_ID)).thenReturn(Optional.of(product));
        when(skuRepository.findByShopIdAndProductId(SHOP_ID, 12L)).thenReturn(List.of(
                sku(9L, product, "180000.00", 0),
                sku(7L, product, "150000.00", 2)));

        StorefrontProductDetailResponse detail = storefrontService.getProduct(SLUG, 12L);

        assertThat(detail.getSkus()).extracting(StorefrontProductDetailResponse.SkuItem::getId)
                .containsExactly(7L, 9L);
        assertThat(detail.getSkus()).extracting(StorefrontProductDetailResponse.SkuItem::isInStock)
                .containsExactly(true, false);
        assertThat(detail.getSkus().get(0).getVariant()).isEqualTo("Đỏ / M");
        assertThat(detail.getSkus().get(0).getPrice()).isEqualByComparingTo("150000.00");
        assertThat(detail.isInStock()).isTrue();
        assertThat(detail.getMinPrice()).isEqualByComparingTo("150000.00");
        assertThat(detail.getMaxPrice()).isEqualByComparingTo("180000.00");
    }

    @Test
    void getProduct_mapsImagesWithStableUrls() {
        stubPublishedShop();
        Product product = product(12L, null);
        product.setDescription("<script>mô tả</script>");
        when(productRepository.findByIdAndShopId(12L, SHOP_ID)).thenReturn(Optional.of(product));
        when(productImageRepository.findByShopIdAndProductIdOrderBySortOrderAscIdAsc(SHOP_ID, 12L))
                .thenReturn(List.of(image(54L, product, false), image(55L, product, true)));

        StorefrontProductDetailResponse detail = storefrontService.getProduct(SLUG, 12L);

        assertThat(detail.getDescription()).isEqualTo("<script>mô tả</script>");
        assertThat(detail.getImageUrl()).isEqualTo("/api/public/shops/cua-hang-a/images/55");
        assertThat(detail.getImages()).extracting(StorefrontProductDetailResponse.ImageItem::getUrl)
                .containsExactly("/api/public/shops/cua-hang-a/images/54", "/api/public/shops/cua-hang-a/images/55");
        assertThat(detail.getImages()).extracting(StorefrontProductDetailResponse.ImageItem::isPrimary)
                .containsExactly(false, true);
    }

    @Test
    void getProduct_noImages_returnsEmptyList() {
        stubPublishedShop();
        when(productRepository.findByIdAndShopId(12L, SHOP_ID))
                .thenReturn(Optional.of(product(12L, "/api/media/products/1?t=1")));

        StorefrontProductDetailResponse detail = storefrontService.getProduct(SLUG, 12L);

        assertThat(detail.getImages()).isEmpty();
        assertThat(detail.getImageUrl()).isNull();
    }

    // ------------------------------------------------------------------ IMAGE

    @Test
    void getImage_published_returnsStreamingResource() {
        stubPublishedShop();
        Resource resource = new ByteArrayResource(new byte[]{1});
        when(productImageRepository.findPublishedByIdAndShopId(55L, SHOP_ID))
                .thenReturn(Optional.of(image(55L, product(12L, null), true)));
        when(localMediaStorage.openResource("1/a.jpg")).thenReturn(Optional.of(resource));

        StorefrontService.StorefrontImage image = storefrontService.getImage(SLUG, 55L);

        assertThat(image.resource()).isSameAs(resource);
        assertThat(image.contentType()).isEqualTo("image/jpeg");
    }

    /** Ảnh nháp, ảnh của shop khác, ảnh của sản phẩm không ACTIVE: query công khai không trả về. */
    @Test
    void getImage_notPublished_throws404() {
        stubPublishedShop();
        when(productImageRepository.findPublishedByIdAndShopId(55L, SHOP_ID)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () -> storefrontService.getImage(SLUG, 55L));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ex.getMessage()).isEqualTo("Không tìm thấy ảnh");
        verifyNoInteractions(localMediaStorage);
    }

    @Test
    void getImage_fileMissing_throws404() {
        stubPublishedShop();
        when(productImageRepository.findPublishedByIdAndShopId(55L, SHOP_ID))
                .thenReturn(Optional.of(image(55L, product(12L, null), true)));
        when(localMediaStorage.openResource("1/a.jpg")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () -> storefrontService.getImage(SLUG, 55L));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ex.getMessage()).isEqualTo("Không tìm thấy ảnh");
    }

    @Test
    void getImage_shopDisabled_throwsShop404WithoutQuery() {
        Shop shop = publishedShop();
        shop.setStorefrontEnabled(false);
        when(shopRepository.findBySlug(SLUG)).thenReturn(Optional.of(shop));

        assertThrows(BusinessException.class, () -> storefrontService.getImage(SLUG, 55L));

        verifyNoInteractions(productImageRepository, localMediaStorage);
    }

    // ----------------------------------------------------------------- HELPER

    private StorefrontProductSummaryResponse onlyItem() {
        return storefrontService.getProducts(SLUG, null, null, PAGEABLE).getContent().get(0);
    }

    private void assertStatusHidden(ProductStatus status) {
        stubPublishedShop();
        Product product = product(12L, null);
        product.setStatus(status);
        when(productRepository.findByIdAndShopId(12L, SHOP_ID)).thenReturn(Optional.of(product));

        assertProductNotFound();
        verifyNoInteractions(skuRepository, productImageRepository);
    }

    private void assertProductNotFound() {
        BusinessException ex = assertThrows(BusinessException.class, () -> storefrontService.getProduct(SLUG, 12L));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ex.getMessage()).isEqualTo("Không tìm thấy sản phẩm");
    }

    private void assertShopNotFound(org.junit.jupiter.api.function.Executable call) {
        BusinessException ex = assertThrows(BusinessException.class, call);
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ex.getMessage()).isEqualTo("Không tìm thấy cửa hàng");
    }

    private void stubPublishedShop() {
        when(shopRepository.findBySlug(SLUG)).thenReturn(Optional.of(publishedShop()));
    }

    private void stubProductPage(List<Product> products) {
        when(productRepository.searchPublished(SHOP_ID, null, null, PAGEABLE))
                .thenReturn(new PageImpl<>(products, PAGEABLE, products.size()));
    }

    private static Shop publishedShop() {
        return Shop.builder()
                .id(SHOP_ID)
                .name("Cửa hàng A")
                .slug(SLUG)
                .platform("MANUAL")
                .storefrontEnabled(true)
                .contactPhone("0901234567")
                .build();
    }

    private static Product product(Long id, String legacyImageUrl) {
        return Product.builder().id(id).name("Áo thun").category("Thời trang").imageUrl(legacyImageUrl).build();
    }

    private static Sku sku(Long id, Product product, String price, int stock) {
        return Sku.builder().id(id).product(product).skuCode("SKU-" + id).variant("Đỏ / M")
                .price(new BigDecimal(price)).stock(stock).build();
    }

    private static ProductImage image(Long id, Product product, boolean primary) {
        return ProductImage.builder().id(id).product(product).storageKey("1/a.jpg").contentType("image/jpeg")
                .isPrimary(primary).build();
    }
}
