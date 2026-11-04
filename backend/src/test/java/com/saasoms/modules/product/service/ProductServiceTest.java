package com.saasoms.modules.product.service;

import com.saasoms.common.exception.BusinessException;
import com.saasoms.common.response.PageResponse;
import com.saasoms.common.tenant.TenantContext;
import com.saasoms.modules.product.dto.ProductRequest;
import com.saasoms.modules.product.dto.ProductResponse;
import com.saasoms.modules.product.dto.SkuRequest;
import com.saasoms.modules.product.dto.UpdateProductStatusRequest;
import com.saasoms.modules.product.entity.Product;
import com.saasoms.modules.product.entity.ProductStatus;
import com.saasoms.modules.product.entity.Sku;
import com.saasoms.modules.product.repository.ProductRepository;
import com.saasoms.modules.product.repository.SkuRepository;
import com.saasoms.modules.shop.entity.Shop;
import com.saasoms.modules.shop.repository.ShopRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    private static final Long TENANT_ID = 1L;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private SkuRepository skuRepository;

    @Mock
    private ShopRepository shopRepository;

    @Mock
    private ProductImageService productImageService;

    @InjectMocks
    private ProductService productService;

    private Shop shop;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_ID);
        shop = Shop.builder()
                .id(TENANT_ID)
                .name("Shop test")
                .slug("shop-test")
                .platform("MANUAL")
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ------------------------------------------------------------------ READ

    @Test
    void getProducts_defaultExcludesArchived() {
        when(productRepository.search(any(), any(), any(), any(), any(), any()))
                .thenReturn(Page.empty());

        productService.getProducts(null, null, null, null, PageRequest.of(0, 20));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<ProductStatus>> statusesCaptor =
                ArgumentCaptor.forClass(Collection.class);

        verify(productRepository).search(
                eq(TENANT_ID), statusesCaptor.capture(), isNull(), isNull(), isNull(), any(Pageable.class));

        assertThat(statusesCaptor.getValue())
                .containsExactlyInAnyOrder(ProductStatus.ACTIVE, ProductStatus.INACTIVE)
                .doesNotContain(ProductStatus.ARCHIVED);
    }

    @Test
    void getProducts_withArchivedStatus_passesOnlyArchived() {
        when(productRepository.search(any(), any(), any(), any(), any(), any()))
                .thenReturn(Page.empty());

        productService.getProducts(ProductStatus.ARCHIVED, null, null, null, PageRequest.of(0, 20));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<ProductStatus>> statusesCaptor =
                ArgumentCaptor.forClass(Collection.class);

        verify(productRepository).search(
                eq(TENANT_ID), statusesCaptor.capture(), isNull(), isNull(), isNull(), any(Pageable.class));

        assertThat(statusesCaptor.getValue()).containsExactly(ProductStatus.ARCHIVED);
    }

    @Test
    void getProducts_mapsSkusWithoutNPlusOne() {
        Product first = product(10L, ProductStatus.ACTIVE);
        Product second = product(11L, ProductStatus.ACTIVE);

        Page<Product> page = new PageImpl<>(List.of(first, second), PageRequest.of(0, 20), 2);
        when(productRepository.search(any(), any(), any(), any(), any(), any())).thenReturn(page);
        when(skuRepository.findByShopIdAndProductIdIn(eq(TENANT_ID), anyCollection()))
                .thenReturn(List.of(
                        sku(100L, first, "SKU-A", "10.00", 5),
                        sku(101L, first, "SKU-B", "20.00", 7),
                        sku(102L, second, "SKU-C", "5.00", 0)));

        PageResponse<ProductResponse> response =
                productService.getProducts(null, null, null, null, PageRequest.of(0, 20));

        assertThat(response.getContent()).hasSize(2);

        ProductResponse firstResponse = response.getContent().get(0);
        assertThat(firstResponse.getSkuCount()).isEqualTo(2);
        assertThat(firstResponse.getMinPrice()).isEqualByComparingTo("10.00");
        assertThat(firstResponse.getMaxPrice()).isEqualByComparingTo("20.00");
        assertThat(firstResponse.getTotalStock()).isEqualTo(12);

        ProductResponse secondResponse = response.getContent().get(1);
        assertThat(secondResponse.getSkuCount()).isEqualTo(1);
        assertThat(secondResponse.getTotalStock()).isZero();

        verify(skuRepository, times(1)).findByShopIdAndProductIdIn(eq(TENANT_ID), anyCollection());
    }

    @Test
    void getProduct_notFound_throws404() {
        when(productRepository.findByIdAndShopId(10L, TENANT_ID)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> productService.getProduct(10L));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getProduct_wrongTenant_throws404() {
        TenantContext.set(2L);
        when(productRepository.findByIdAndShopId(10L, 2L)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> productService.getProduct(10L));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(productRepository).findByIdAndShopId(10L, 2L);
    }

    // ----------------------------------------------------------------- WRITE

    @Test
    void createProduct_duplicateSkuCode_throwsConflictBeforeAnyWrite() {
        when(shopRepository.findById(TENANT_ID)).thenReturn(Optional.of(shop));
        when(skuRepository.findByShopIdAndSkuCodeIn(eq(TENANT_ID), anyCollection()))
                .thenReturn(List.of(sku(500L, product(99L, ProductStatus.ACTIVE), "SKU-1", "1.00", 1)));

        ProductRequest request = request("Áo thun", List.of(
                new SkuRequest(null, "SKU-1", "M", new BigDecimal("1.00"), 1, null)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> productService.createProduct(request));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getMessage()).contains("SKU-1");
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void createProduct_duplicateSkuCodeOfArchivedProduct_messageMentionsRestore() {
        when(shopRepository.findById(TENANT_ID)).thenReturn(Optional.of(shop));
        when(skuRepository.findByShopIdAndSkuCodeIn(eq(TENANT_ID), anyCollection()))
                .thenReturn(List.of(sku(500L, product(99L, ProductStatus.ARCHIVED), "SKU-OLD", "1.00", 1)));

        ProductRequest request = request("Áo thun", List.of(
                new SkuRequest(null, "SKU-OLD", "M", new BigDecimal("1.00"), 1, null)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> productService.createProduct(request));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getMessage()).contains("đã xoá").contains("khôi phục");
    }

    @Test
    void createProduct_duplicateCodeInRequest_throwsBadRequestBeforeAnyWrite() {
        when(shopRepository.findById(TENANT_ID)).thenReturn(Optional.of(shop));

        ProductRequest request = request("Áo thun", List.of(
                new SkuRequest(null, "SKU-1", "M", new BigDecimal("1.00"), 1, null),
                new SkuRequest(null, "sku-1", "L", new BigDecimal("1.00"), 1, null)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> productService.createProduct(request));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void getProducts_searchWithLikeWildcards_isEscaped() {
        when(productRepository.search(any(), any(), any(), any(), any(), any()))
                .thenReturn(Page.empty());

        productService.getProducts(null, null, null, " 100%_a!b ", PageRequest.of(0, 20));

        verify(productRepository).search(
                eq(TENANT_ID), anyCollection(), isNull(), isNull(), eq("100!%!_a!!b"), any(Pageable.class));
    }

    @Test
    void getProduct_skusAreSortedById() {
        Product product = product(10L, ProductStatus.ACTIVE);
        when(productRepository.findByIdAndShopId(10L, TENANT_ID)).thenReturn(Optional.of(product));
        when(skuRepository.findByShopIdAndProductId(TENANT_ID, 10L)).thenReturn(List.of(
                sku(102L, product, "SKU-C", "1.00", 1),
                sku(100L, product, "SKU-A", "1.00", 1),
                sku(101L, product, "SKU-B", "1.00", 1)));

        ProductResponse response = productService.getProduct(10L);

        assertThat(response.getSkus()).extracting(ProductResponse.SkuInfo::getId)
                .containsExactly(100L, 101L, 102L);
    }

    @Test
    void getProduct_totalStockAboveIntRange_doesNotOverflow() {
        Product product = product(10L, ProductStatus.ACTIVE);
        when(productRepository.findByIdAndShopId(10L, TENANT_ID)).thenReturn(Optional.of(product));
        when(skuRepository.findByShopIdAndProductId(TENANT_ID, 10L)).thenReturn(List.of(
                sku(100L, product, "SKU-A", "1.00", Integer.MAX_VALUE),
                sku(101L, product, "SKU-B", "1.00", Integer.MAX_VALUE)));

        ProductResponse response = productService.getProduct(10L);

        assertThat(response.getTotalStock()).isEqualTo(2L * Integer.MAX_VALUE);
    }

    @Test
    void updateProduct_sameSkuIdTwice_throwsBadRequest() {
        Product product = product(10L, ProductStatus.ACTIVE);
        when(productRepository.findForUpdate(10L, TENANT_ID)).thenReturn(Optional.of(product));
        stubLockedSkus(sku(100L, product, "SKU-A", "1.00", 1));

        ProductRequest request = request("Áo thun", List.of(
                new SkuRequest(100L, "SKU-A", null, new BigDecimal("2.00"), 2, null),
                new SkuRequest(100L, "SKU-B", null, new BigDecimal("3.00"), 3, null)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> productService.updateProduct(10L, request));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(skuRepository, never()).saveAll(anyCollection());
    }

    @Test
    void updateProduct_onlySkuChanged_bumpsUpdatedAt() {
        Product product = product(10L, ProductStatus.ACTIVE);
        LocalDateTime old = LocalDateTime.now().minusDays(1);
        product.setUpdatedAt(old);
        when(productRepository.findForUpdate(10L, TENANT_ID)).thenReturn(Optional.of(product));
        stubLockedSkus(sku(100L, product, "SKU-A", "1.00", 1));

        ProductRequest request = request("Sản phẩm 10", List.of(
                new SkuRequest(100L, "SKU-A", null, new BigDecimal("2.00"), 1, null)));

        ProductResponse response = productService.updateProduct(10L, request);

        assertThat(product.getUpdatedAt()).isAfter(old);
        assertThat(response.getUpdatedAt()).isAfter(old);
    }

    @Test
    void updateProduct_otherTenant_throws404() {
        TenantContext.set(2L);
        when(productRepository.findForUpdate(10L, 2L)).thenReturn(Optional.empty());

        ProductRequest request = request("Áo thun", List.of(
                new SkuRequest(null, "SKU-1", null, new BigDecimal("1.00"), 1, null)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> productService.updateProduct(10L, request));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(skuRepository, never()).findIdsByShopIdAndProductId(any(), any());
        verify(skuRepository, never()).findForUpdate(any(), anyCollection());
    }

    @Test
    void restoreProduct_inactiveProduct_staysInactive() {
        Product product = product(10L, ProductStatus.INACTIVE);
        when(productRepository.findForUpdate(10L, TENANT_ID)).thenReturn(Optional.of(product));
        when(skuRepository.findByShopIdAndProductId(TENANT_ID, 10L)).thenReturn(List.of());

        ProductResponse response = productService.restoreProduct(10L);

        assertThat(response.getStatus()).isEqualTo(ProductStatus.INACTIVE);
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void createProduct_blankSkuCode_generatesCode() {
        when(shopRepository.findById(TENANT_ID)).thenReturn(Optional.of(shop));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(10L);
            }
            return saved;
        });
        when(skuRepository.existsByShopIdAndSkuCode(eq(TENANT_ID), anyString())).thenReturn(false);
        when(skuRepository.save(any(Sku.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductRequest request = request("Áo thun", List.of(
                new SkuRequest(null, null, "Mặc định", new BigDecimal("1.00"), 2, null)));

        ProductResponse response = productService.createProduct(request);

        assertThat(response.getSkus()).hasSize(1);
        assertThat(response.getSkus().get(0).getSkuCode()).startsWith("SKU-");
    }

    @Test
    void createProduct_emptySkus_throwsBadRequest() {
        when(shopRepository.findById(TENANT_ID)).thenReturn(Optional.of(shop));

        ProductRequest request = request("Áo thun", List.of());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> productService.createProduct(request));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getMessage()).contains("ít nhất 1 SKU");
    }

    @Test
    void updateProduct_skuOfAnotherProduct_throwsBadRequest() {
        Product product = product(10L, ProductStatus.ACTIVE);
        when(productRepository.findForUpdate(10L, TENANT_ID)).thenReturn(Optional.of(product));
        stubLockedSkus(sku(100L, product, "SKU-A", "1.00", 1));

        ProductRequest request = request("Áo thun", List.of(
                new SkuRequest(999L, "SKU-X", null, new BigDecimal("2.00"), 2, null)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> productService.updateProduct(10L, request));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getMessage()).contains("SKU không thuộc sản phẩm này");
    }

    @Test
    void updateProduct_deleteSkuUsedInOrder_throwsConflict() {
        Product product = product(10L, ProductStatus.ACTIVE);
        Sku removed = sku(100L, product, "SKU-OLD", "1.00", 1);
        Sku kept = sku(101L, product, "SKU-KEEP", "2.00", 3);

        when(productRepository.findForUpdate(10L, TENANT_ID)).thenReturn(Optional.of(product));
        stubLockedSkus(removed, kept);
        when(skuRepository.findByShopIdAndSkuCodeIn(eq(TENANT_ID), anyCollection()))
                .thenReturn(List.of());
        when(skuRepository.findUsedSkuIds(eq(TENANT_ID), anyCollection()))
                .thenReturn(List.of(100L));

        ProductRequest request = request("Áo thun", List.of(
                new SkuRequest(101L, "SKU-KEEP", null, new BigDecimal("2.00"), 3, null)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> productService.updateProduct(10L, request));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getMessage()).contains("SKU-OLD");
        verify(skuRepository, never()).deleteAll(anyCollection());
    }

    @Test
    void updateProduct_archivedProduct_throwsBadRequest() {
        Product product = product(10L, ProductStatus.ARCHIVED);
        when(productRepository.findForUpdate(10L, TENANT_ID)).thenReturn(Optional.of(product));

        ProductRequest request = request("Áo thun", List.of(
                new SkuRequest(null, "SKU-1", null, new BigDecimal("1.00"), 1, null)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> productService.updateProduct(10L, request));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getMessage()).contains("khôi phục trước khi sửa");
    }

    @Test
    void updateStatus_rejectsArchivedStatus() {
        Product product = product(10L, ProductStatus.ACTIVE);
        when(productRepository.findForUpdate(10L, TENANT_ID)).thenReturn(Optional.of(product));

        UpdateProductStatusRequest request = new UpdateProductStatusRequest(ProductStatus.ARCHIVED);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> productService.updateStatus(10L, request));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getMessage()).contains("Dùng chức năng xoá");
    }

    @Test
    void updateStatus_nullStatus_throwsBadRequest() {
        UpdateProductStatusRequest request = new UpdateProductStatusRequest(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> productService.updateStatus(10L, request));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getMessage()).contains("Trạng thái không được để trống");
        verifyNoInteractions(productRepository);
    }

    @Test
    void archiveProduct_setsArchived() {
        Product product = product(10L, ProductStatus.ACTIVE);
        when(productRepository.findForUpdate(10L, TENANT_ID)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        productService.archiveProduct(10L);

        assertThat(product.getStatus()).isEqualTo(ProductStatus.ARCHIVED);
        verify(productRepository).save(product);
    }

    @Test
    void restoreProduct_setsActive() {
        Product product = product(10L, ProductStatus.ARCHIVED);
        when(productRepository.findForUpdate(10L, TENANT_ID)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(skuRepository.findByShopIdAndProductId(TENANT_ID, 10L)).thenReturn(List.of());

        ProductResponse response = productService.restoreProduct(10L);

        assertThat(product.getStatus()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(response.getStatus()).isEqualTo(ProductStatus.ACTIVE);
    }

    // ------------------------------------------------------------ STOCK CHECK

    @Test
    void updateProduct_stockChanged_throwsConflictWithCurrentStock() {
        Product product = activeProduct();
        Sku locked = sku(100L, product, "SKU-A", "1.00", 7);
        stubLockedSkus(locked);

        ProductRequest request = request("Áo thun", List.of(
                new SkuRequest(100L, "SKU-A", null, new BigDecimal("1.00"), 10, 10)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> productService.updateProduct(10L, request));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getMessage()).isEqualTo(
                "Tồn kho đã thay đổi: \"SKU-A\" hiện còn 7 (lúc mở form là 10). "
                        + "Vui lòng kiểm tra lại trước khi lưu.");
        assertThat(locked.getStock()).isEqualTo(7);
        verify(skuRepository, never()).saveAll(anyCollection());
        verify(skuRepository, never()).deleteAll(anyCollection());
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void updateProduct_stockChanged_blocksDeletingOtherSku() {
        Product product = activeProduct();
        Sku removed = sku(100L, product, "SKU-OLD", "1.00", 1);
        Sku kept = sku(101L, product, "SKU-A", "1.00", 7);
        stubLockedSkus(removed, kept);

        ProductRequest request = request("Áo thun", List.of(
                new SkuRequest(101L, "SKU-A", null, new BigDecimal("1.00"), 10, 10)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> productService.updateProduct(10L, request));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verify(skuRepository, never()).findUsedSkuIds(any(), anyCollection());
        verify(skuRepository, never()).deleteAll(anyCollection());
    }

    @Test
    void updateProduct_stockChanged_messageUsesCurrentSkuCode() {
        Product product = activeProduct();
        Sku locked = sku(100L, product, "SKU-A", "1.00", 7);
        stubLockedSkus(locked);

        ProductRequest request = request("Áo thun", List.of(
                new SkuRequest(100L, "SKU-MOI", null, new BigDecimal("1.00"), 10, 10)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> productService.updateProduct(10L, request));

        assertThat(ex.getMessage()).contains("\"SKU-A\"").doesNotContain("SKU-MOI");
        assertThat(locked.getSkuCode()).isEqualTo("SKU-A");
    }

    @Test
    void updateProduct_expectedMatches_updatesStock() {
        Product product = activeProduct();
        Sku locked = sku(100L, product, "SKU-A", "1.00", 1000);
        stubLockedSkus(locked);

        ProductRequest request = request("Áo thun", List.of(
                new SkuRequest(100L, "SKU-A", null, new BigDecimal("1.00"), 2000, 1000)));

        productService.updateProduct(10L, request);

        assertThat(locked.getStock()).isEqualTo(2000);
        verify(skuRepository).saveAll(anyCollection());
    }

    @Test
    void updateProduct_locksSkusBeforeLoading() {
        Product product = activeProduct();
        stubLockedSkus(sku(100L, product, "SKU-A", "1.00", 7));

        ProductRequest request = request("Áo thun", List.of(
                new SkuRequest(100L, "SKU-A", null, new BigDecimal("1.00"), 10, 10)));

        assertThrows(BusinessException.class, () -> productService.updateProduct(10L, request));

        InOrder order = inOrder(skuRepository);
        order.verify(skuRepository).findIdsByShopIdAndProductId(TENANT_ID, 10L);
        order.verify(skuRepository).findForUpdate(TENANT_ID, List.of(100L));
        order.verify(skuRepository).findByShopIdAndSkuCodeIn(eq(TENANT_ID), anyCollection());
        verify(skuRepository, never()).findByShopIdAndProductId(any(), any());
    }

    @Test
    void updateProduct_locksSkusBeingDeleted() {
        Product product = activeProduct();
        Sku removed = sku(100L, product, "SKU-OLD", "1.00", 1);
        Sku kept = sku(101L, product, "SKU-KEEP", "2.00", 3);
        stubLockedSkus(removed, kept);
        when(skuRepository.findUsedSkuIds(eq(TENANT_ID), anyCollection())).thenReturn(List.of());

        ProductRequest request = request("Áo thun", List.of(
                new SkuRequest(101L, "SKU-KEEP", null, new BigDecimal("2.00"), 3, 3)));

        productService.updateProduct(10L, request);

        verify(skuRepository).findForUpdate(TENANT_ID, List.of(100L, 101L));
        verify(skuRepository).deleteAll(List.of(removed));
    }

    @Test
    void updateProduct_expectedStockNull_sameStock_succeeds() {
        Product product = activeProduct();
        Sku locked = sku(100L, product, "SKU-A", "1.00", 1000);
        stubLockedSkus(locked);

        ProductRequest request = request("Áo thun", List.of(
                new SkuRequest(100L, "SKU-A", null, new BigDecimal("5.00"), 1000, null)));

        productService.updateProduct(10L, request);

        assertThat(locked.getStock()).isEqualTo(1000);
        assertThat(locked.getPrice()).isEqualByComparingTo("5.00");
        verify(skuRepository).saveAll(anyCollection());
    }

    @Test
    void updateProduct_expectedStockNull_differentStock_throwsConflict() {
        Product product = activeProduct();
        Sku locked = sku(100L, product, "SKU-A", "1.00", 7);
        stubLockedSkus(locked);

        ProductRequest request = request("Áo thun", List.of(
                new SkuRequest(100L, "SKU-A", null, new BigDecimal("1.00"), 10, null)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> productService.updateProduct(10L, request));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getMessage()).isEqualTo(
                "Tồn kho của \"SKU-A\" hiện là 7, khác giá trị gửi lên. "
                        + "Vui lòng tải lại trang để xem số mới nhất.");
        assertThat(locked.getStock()).isEqualTo(7);
        verify(skuRepository, never()).saveAll(anyCollection());
    }

    @Test
    void updateProduct_newSku_ignoresExpectedStock() {
        Product product = activeProduct();
        stubLockedSkus(sku(100L, product, "SKU-A", "1.00", 7));

        ProductRequest request = request("Áo thun", List.of(
                new SkuRequest(100L, "SKU-A", null, new BigDecimal("1.00"), 7, 7),
                new SkuRequest(null, "SKU-B", null, new BigDecimal("1.00"), 4, 999)));

        productService.updateProduct(10L, request);

        assertThat(savedSkus()).extracting(Sku::getSkuCode, Sku::getStock)
                .containsExactlyInAnyOrder(tuple("SKU-A", 7), tuple("SKU-B", 4));
    }

    @Test
    void updateProduct_multipleSkusChanged_listsAll() {
        Product product = activeProduct();
        stubLockedSkus(
                sku(100L, product, "SKU-A", "1.00", 7),
                sku(101L, product, "SKU-B", "1.00", 2),
                sku(102L, product, "SKU-C", "1.00", 5));

        ProductRequest request = request("Áo thun", List.of(
                new SkuRequest(100L, "SKU-A", null, new BigDecimal("1.00"), 10, 10),
                new SkuRequest(101L, "SKU-B", null, new BigDecimal("1.00"), 9, null),
                new SkuRequest(102L, "SKU-C", null, new BigDecimal("1.00"), 8, 5)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> productService.updateProduct(10L, request));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getMessage()).isEqualTo(
                "Tồn kho đã thay đổi: \"SKU-A\" hiện còn 7 (lúc mở form là 10). "
                        + "Vui lòng kiểm tra lại trước khi lưu.; "
                        + "Tồn kho của \"SKU-B\" hiện là 2, khác giá trị gửi lên. "
                        + "Vui lòng tải lại trang để xem số mới nhất.");
    }

    @Test
    void updateProduct_noExistingSkus_skipsLock() {
        activeProduct();
        when(skuRepository.findIdsByShopIdAndProductId(TENANT_ID, 10L)).thenReturn(List.of());

        ProductRequest request = request("Áo thun", List.of(
                new SkuRequest(null, "SKU-B", null, new BigDecimal("1.00"), 4, null)));

        productService.updateProduct(10L, request);

        verify(skuRepository, never()).findForUpdate(any(), anyCollection());
        assertThat(savedSkus()).extracting(Sku::getStock).containsExactly(4);
    }

    @Test
    void createProduct_ignoresExpectedStock() {
        when(shopRepository.findById(TENANT_ID)).thenReturn(Optional.of(shop));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });
        when(skuRepository.save(any(Sku.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductRequest request = request("Áo thun", List.of(
                new SkuRequest(null, "SKU-1", null, new BigDecimal("1.00"), 2, 999)));

        ProductResponse response = productService.createProduct(request);

        assertThat(response.getSkus()).hasSize(1);
        assertThat(response.getSkus().get(0).getStock()).isEqualTo(2);
        verify(skuRepository, never()).findForUpdate(any(), anyCollection());
    }

    // ------------------------------------------------------------ PRODUCT LOCK

    @Test
    void updateProduct_locksProductBeforeSkus() {
        Product product = activeProduct();
        stubLockedSkus(sku(100L, product, "SKU-A", "1.00", 7));

        ProductRequest request = request("Áo thun", List.of(
                new SkuRequest(100L, "SKU-A", null, new BigDecimal("1.00"), 7, 7)));

        productService.updateProduct(10L, request);

        InOrder order = inOrder(productRepository, skuRepository);
        order.verify(productRepository).findForUpdate(10L, TENANT_ID);
        order.verify(skuRepository).findIdsByShopIdAndProductId(TENANT_ID, 10L);
        order.verify(skuRepository).findForUpdate(TENANT_ID, List.of(100L));
        verify(productRepository, never()).findByIdAndShopId(any(), any());
    }

    @Test
    void updateProduct_archivedAfterLock_throws400() {
        when(productRepository.findForUpdate(10L, TENANT_ID))
                .thenReturn(Optional.of(product(10L, ProductStatus.ARCHIVED)));

        ProductRequest request = request("Áo thun", List.of(
                new SkuRequest(null, "SKU-1", null, new BigDecimal("1.00"), 1, null)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> productService.updateProduct(10L, request));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getMessage()).contains("khôi phục trước khi sửa");
        verifyNoInteractions(skuRepository, productImageService);
    }

    @Test
    void updateStatus_usesLockedRead() {
        Product product = activeProduct();

        productService.updateStatus(10L, new UpdateProductStatusRequest(ProductStatus.INACTIVE));

        assertThat(product.getStatus()).isEqualTo(ProductStatus.INACTIVE);
        verify(productRepository).findForUpdate(10L, TENANT_ID);
        verify(productRepository, never()).findByIdAndShopId(any(), any());
    }

    @Test
    void archiveProduct_usesLockedRead() {
        Product product = activeProduct();

        productService.archiveProduct(10L);

        assertThat(product.getStatus()).isEqualTo(ProductStatus.ARCHIVED);
        verify(productRepository).findForUpdate(10L, TENANT_ID);
        verify(productRepository, never()).findByIdAndShopId(any(), any());
    }

    @Test
    void restoreProduct_usesLockedRead() {
        when(productRepository.findForUpdate(10L, TENANT_ID))
                .thenReturn(Optional.of(product(10L, ProductStatus.ARCHIVED)));

        ProductResponse response = productService.restoreProduct(10L);

        assertThat(response.getStatus()).isEqualTo(ProductStatus.ACTIVE);
        verify(productRepository).findForUpdate(10L, TENANT_ID);
        verify(productRepository, never()).findByIdAndShopId(any(), any());
    }

    @Test
    void getProduct_doesNotLock() {
        Product product = product(10L, ProductStatus.ACTIVE);
        when(productRepository.findByIdAndShopId(10L, TENANT_ID)).thenReturn(Optional.of(product));

        productService.getProduct(10L);

        verify(productRepository, never()).findForUpdate(any(), any());
    }

    // ---------------------------------------------------------- KNOWN SKU IDS

    @Test
    void updateProduct_unknownSkuInDb_throws409NoWrite() {
        Product product = activeProduct();
        stubLockedSkus(sku(100L, product, "SKU-A", "1.00", 7), sku(101L, product, "SKU-MOI", "1.00", 3));

        ProductRequest request = request("Áo thun", List.of(
                new SkuRequest(100L, "SKU-A", null, new BigDecimal("1.00"), 7, 7)));
        request.setKnownSkuIds(List.of(100L));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> productService.updateProduct(10L, request));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getMessage()).isEqualTo(
                "Sản phẩm có biến thể mới được thêm ở nơi khác (SKU-MOI). Vui lòng kiểm tra lại trước khi lưu.");
        verify(skuRepository, never()).deleteAll(anyCollection());
        verify(skuRepository, never()).saveAll(anyCollection());
        verify(productRepository, never()).save(any(Product.class));
        verifyNoInteractions(productImageService);
    }

    @Test
    void updateProduct_knownSkuAbsent_deletes() {
        Product product = activeProduct();
        Sku removed = sku(100L, product, "SKU-OLD", "1.00", 1);
        stubLockedSkus(removed, sku(101L, product, "SKU-KEEP", "2.00", 3));
        when(skuRepository.findUsedSkuIds(eq(TENANT_ID), anyCollection())).thenReturn(List.of());

        ProductRequest request = request("Áo thun", List.of(
                new SkuRequest(101L, "SKU-KEEP", null, new BigDecimal("2.00"), 3, 3)));
        request.setKnownSkuIds(Arrays.asList(100L, null, 101L));

        productService.updateProduct(10L, request);

        verify(skuRepository).deleteAll(List.of(removed));
    }

    @Test
    void updateProduct_knownSkuAbsentUsedInOrder_throws409() {
        Product product = activeProduct();
        stubLockedSkus(sku(100L, product, "SKU-OLD", "1.00", 1), sku(101L, product, "SKU-KEEP", "2.00", 3));
        when(skuRepository.findUsedSkuIds(eq(TENANT_ID), anyCollection())).thenReturn(List.of(100L));

        ProductRequest request = request("Áo thun", List.of(
                new SkuRequest(101L, "SKU-KEEP", null, new BigDecimal("2.00"), 3, 3)));
        request.setKnownSkuIds(List.of(100L, 101L));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> productService.updateProduct(10L, request));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getMessage()).contains("đã dùng trong đơn hàng").contains("SKU-OLD");
        verify(skuRepository, never()).deleteAll(anyCollection());
    }

    @Test
    void updateProduct_nullKnownSkuIds_keepsLegacyDelete() {
        Product product = activeProduct();
        Sku removed = sku(100L, product, "SKU-OLD", "1.00", 1);
        stubLockedSkus(removed, sku(101L, product, "SKU-KEEP", "2.00", 3));
        when(skuRepository.findUsedSkuIds(eq(TENANT_ID), anyCollection())).thenReturn(List.of());

        ProductRequest request = request("Áo thun", List.of(
                new SkuRequest(101L, "SKU-KEEP", null, new BigDecimal("2.00"), 3, 3)));

        productService.updateProduct(10L, request);

        verify(skuRepository).deleteAll(List.of(removed));
    }

    @Test
    void updateProduct_submittedKnownIdGone_throws409() {
        Product product = activeProduct();
        stubLockedSkus(sku(100L, product, "SKU-A", "1.00", 7));

        ProductRequest request = request("Áo thun", List.of(
                new SkuRequest(100L, "SKU-A", null, new BigDecimal("1.00"), 7, 7),
                new SkuRequest(999L, "SKU-GONE", null, new BigDecimal("1.00"), 1, 1)));
        request.setKnownSkuIds(List.of(100L, 999L));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> productService.updateProduct(10L, request));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getMessage()).isEqualTo(
                "Biến thể (ID 999) đã bị xoá ở nơi khác. Vui lòng kiểm tra lại trước khi lưu.");
        verify(skuRepository, never()).saveAll(anyCollection());
    }

    @Test
    void updateProduct_submittedUnknownIdGone_throws400() {
        Product product = activeProduct();
        stubLockedSkus(sku(100L, product, "SKU-A", "1.00", 7));

        ProductRequest request = request("Áo thun", List.of(
                new SkuRequest(100L, "SKU-A", null, new BigDecimal("1.00"), 7, 7),
                new SkuRequest(999L, "SKU-X", null, new BigDecimal("1.00"), 1, 1)));
        request.setKnownSkuIds(List.of(100L));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> productService.updateProduct(10L, request));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("SKU không thuộc sản phẩm này");
    }

    @Test
    void updateProduct_knownIdsOtherTenant_notLeaked() {
        Product product = activeProduct();
        stubLockedSkus(sku(100L, product, "SKU-A", "1.00", 7));

        ProductRequest request = request("Áo thun", List.of(
                new SkuRequest(100L, "SKU-A", null, new BigDecimal("1.00"), 7, 7)));
        // 555 là SKU của shop khác: chỉ là số client gửi, không được dùng để tra cứu hay xoá.
        request.setKnownSkuIds(List.of(100L, 555L));

        productService.updateProduct(10L, request);

        verify(skuRepository).findForUpdate(TENANT_ID, List.of(100L));
        verify(skuRepository, never()).findByShopIdAndIdIn(any(), anyCollection());
        verify(skuRepository, never()).deleteAll(anyCollection());
    }

    @Test
    void createProduct_ignoresKnownSkuIds() {
        when(shopRepository.findById(TENANT_ID)).thenReturn(Optional.of(shop));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });
        when(skuRepository.save(any(Sku.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductRequest request = request("Áo thun", List.of(
                new SkuRequest(null, "SKU-1", null, new BigDecimal("1.00"), 2, null)));
        request.setKnownSkuIds(List.of(555L));

        assertThat(productService.createProduct(request).getSkus()).hasSize(1);
    }

    // ---------------------------------------------------------------- FIXTURE

    private Product activeProduct() {
        Product product = product(10L, ProductStatus.ACTIVE);
        when(productRepository.findForUpdate(10L, TENANT_ID)).thenReturn(Optional.of(product));
        return product;
    }

    private void stubLockedSkus(Sku... skus) {
        List<Long> ids = Arrays.stream(skus).map(Sku::getId).toList();
        when(skuRepository.findIdsByShopIdAndProductId(TENANT_ID, 10L)).thenReturn(ids);
        when(skuRepository.findForUpdate(TENANT_ID, ids)).thenReturn(List.of(skus));
    }

    @SuppressWarnings("unchecked")
    private List<Sku> savedSkus() {
        ArgumentCaptor<List<Sku>> captor = ArgumentCaptor.forClass(List.class);
        verify(skuRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    private Product product(Long id, ProductStatus status) {
        return Product.builder()
                .id(id)
                .shop(shop)
                .name("Sản phẩm " + id)
                .category("Danh mục")
                .platformSource("MANUAL")
                .status(status)
                .build();
    }

    private Sku sku(Long id, Product product, String skuCode, String price, int stock) {
        return Sku.builder()
                .id(id)
                .product(product)
                .shop(shop)
                .skuCode(skuCode)
                .variant("Mặc định")
                .price(new BigDecimal(price))
                .stock(stock)
                .build();
    }

    private ProductRequest request(String name, List<SkuRequest> skus) {
        return new ProductRequest(name, "Mô tả", "Danh mục", "MANUAL", null, null, null, skus, null);
    }
}
