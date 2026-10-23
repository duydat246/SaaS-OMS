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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
    void createProduct_duplicateSkuCode_throwsConflict() {
        when(shopRepository.findById(TENANT_ID)).thenReturn(Optional.of(shop));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(10L);
            }
            return saved;
        });
        when(skuRepository.existsByShopIdAndSkuCode(TENANT_ID, "SKU-1")).thenReturn(true);
        when(skuRepository.findByShopIdAndSkuCode(TENANT_ID, "SKU-1"))
                .thenReturn(Optional.of(sku(500L, product(99L, ProductStatus.ACTIVE), "SKU-1", "1.00", 1)));

        ProductRequest request = request("Áo thun", List.of(
                new SkuRequest(null, "SKU-1", "M", new BigDecimal("1.00"), 1)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> productService.createProduct(request));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getMessage()).contains("SKU-1");
    }

    @Test
    void createProduct_duplicateSkuCodeOfArchivedProduct_messageMentionsRestore() {
        when(shopRepository.findById(TENANT_ID)).thenReturn(Optional.of(shop));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(10L);
            }
            return saved;
        });
        when(skuRepository.existsByShopIdAndSkuCode(TENANT_ID, "SKU-OLD")).thenReturn(true);
        when(skuRepository.findByShopIdAndSkuCode(TENANT_ID, "SKU-OLD"))
                .thenReturn(Optional.of(sku(500L, product(99L, ProductStatus.ARCHIVED), "SKU-OLD", "1.00", 1)));

        ProductRequest request = request("Áo thun", List.of(
                new SkuRequest(null, "SKU-OLD", "M", new BigDecimal("1.00"), 1)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> productService.createProduct(request));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getMessage()).contains("đã xoá").contains("khôi phục");
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
                new SkuRequest(null, null, "Mặc định", new BigDecimal("1.00"), 2)));

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
        when(productRepository.findByIdAndShopId(10L, TENANT_ID)).thenReturn(Optional.of(product));
        when(skuRepository.findByShopIdAndProductId(TENANT_ID, 10L))
                .thenReturn(List.of(sku(100L, product, "SKU-A", "1.00", 1)));

        ProductRequest request = request("Áo thun", List.of(
                new SkuRequest(999L, "SKU-X", null, new BigDecimal("2.00"), 2)));

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

        when(productRepository.findByIdAndShopId(10L, TENANT_ID)).thenReturn(Optional.of(product));
        when(skuRepository.findByShopIdAndProductId(TENANT_ID, 10L))
                .thenReturn(List.of(removed, kept));
        when(skuRepository.findByShopIdAndSkuCodeIn(eq(TENANT_ID), anyCollection()))
                .thenReturn(List.of());
        when(skuRepository.findUsedSkuIds(eq(TENANT_ID), anyCollection()))
                .thenReturn(List.of(100L));

        ProductRequest request = request("Áo thun", List.of(
                new SkuRequest(101L, "SKU-KEEP", null, new BigDecimal("2.00"), 3)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> productService.updateProduct(10L, request));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getMessage()).contains("SKU-OLD");
        verify(skuRepository, never()).deleteAll(anyCollection());
    }

    @Test
    void updateProduct_archivedProduct_throwsBadRequest() {
        Product product = product(10L, ProductStatus.ARCHIVED);
        when(productRepository.findByIdAndShopId(10L, TENANT_ID)).thenReturn(Optional.of(product));

        ProductRequest request = request("Áo thun", List.of(
                new SkuRequest(null, "SKU-1", null, new BigDecimal("1.00"), 1)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> productService.updateProduct(10L, request));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getMessage()).contains("khôi phục trước khi sửa");
    }

    @Test
    void updateStatus_rejectsArchivedStatus() {
        Product product = product(10L, ProductStatus.ACTIVE);
        when(productRepository.findByIdAndShopId(10L, TENANT_ID)).thenReturn(Optional.of(product));

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
        when(productRepository.findByIdAndShopId(10L, TENANT_ID)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        productService.archiveProduct(10L);

        assertThat(product.getStatus()).isEqualTo(ProductStatus.ARCHIVED);
        verify(productRepository).save(product);
    }

    @Test
    void restoreProduct_setsActive() {
        Product product = product(10L, ProductStatus.ARCHIVED);
        when(productRepository.findByIdAndShopId(10L, TENANT_ID)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(skuRepository.findByShopIdAndProductId(TENANT_ID, 10L)).thenReturn(List.of());

        ProductResponse response = productService.restoreProduct(10L);

        assertThat(product.getStatus()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(response.getStatus()).isEqualTo(ProductStatus.ACTIVE);
    }

    // ---------------------------------------------------------------- FIXTURE

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
        return new ProductRequest(name, "Mô tả", "Danh mục", "MANUAL", null, skus);
    }
}
