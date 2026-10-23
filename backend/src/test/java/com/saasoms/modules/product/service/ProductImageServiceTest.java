package com.saasoms.modules.product.service;

import com.saasoms.common.exception.BusinessException;
import com.saasoms.common.media.LocalMediaStorage;
import com.saasoms.common.media.MediaProperties;
import com.saasoms.common.media.MediaUrlSigner;
import com.saasoms.modules.product.dto.ProductImageResponse;
import com.saasoms.modules.product.entity.Product;
import com.saasoms.modules.product.entity.ProductImage;
import com.saasoms.modules.product.entity.ProductStatus;
import com.saasoms.modules.product.repository.ProductImageRepository;
import com.saasoms.modules.product.repository.ProductRepository;
import com.saasoms.modules.shop.entity.Shop;
import com.saasoms.modules.shop.repository.ShopRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductImageServiceTest {

    private static final Long TENANT_ID = 1L;
    private static final Long PRODUCT_ID = 10L;

    @Mock
    private ProductImageRepository productImageRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ShopRepository shopRepository;

    @Mock
    private LocalMediaStorage localMediaStorage;

    private ProductImageService productImageService;
    private Shop shop;

    @BeforeEach
    void setUp() {
        MediaProperties mediaProperties = new MediaProperties();
        mediaProperties.setStorageDir("target/test-uploads");
        mediaProperties.setSigningSecret("test-secret-key-at-least-32-characters-long");
        mediaProperties.setUrlTtlSeconds(3600);

        productImageService = new ProductImageService(
                productImageRepository,
                productRepository,
                shopRepository,
                localMediaStorage,
                new MediaUrlSigner(mediaProperties));

        shop = Shop.builder()
                .id(TENANT_ID)
                .name("Shop test")
                .slug("shop-test")
                .platform("MANUAL")
                .build();
    }

    // ---------------------------------------------------------------- UPLOAD

    @Test
    void upload_emptyFile_throwsBadRequestAndSkipsStorage() {
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[0]);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> productImageService.upload(TENANT_ID, file));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getMessage()).contains("rỗng");
        verifyNoInteractions(localMediaStorage);
    }

    @Test
    void upload_wrongContentType_throwsBadRequestAndSkipsStorage() {
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain",
                pngBytes());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> productImageService.upload(TENANT_ID, file));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getMessage()).contains("Chỉ chấp nhận");
        verifyNoInteractions(localMediaStorage);
    }

    @Test
    void upload_magicBytesMismatch_throwsBadRequestAndSkipsStorage() {
        MockMultipartFile file = new MockMultipartFile("file", "fake.png", "image/png",
                "khong-phai-anh".getBytes(StandardCharsets.UTF_8));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> productImageService.upload(TENANT_ID, file));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getMessage()).contains("Chỉ chấp nhận");
        verifyNoInteractions(localMediaStorage);
    }

    @Test
    void upload_fileLargerThan5Mb_throwsBadRequestAndSkipsStorage() {
        byte[] tooLarge = new byte[5 * 1024 * 1024 + 1];
        MockMultipartFile file = new MockMultipartFile("file", "big.png", "image/png", tooLarge);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> productImageService.upload(TENANT_ID, file));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getMessage()).contains("5MB");
        verifyNoInteractions(localMediaStorage);
    }

    @Test
    void upload_validPng_storesDraftImageWithSignedUrl() {
        byte[] content = pngBytes();
        MockMultipartFile file = new MockMultipartFile("file", "ao-thun.png", "image/png", content);

        when(localMediaStorage.store(eq(TENANT_ID), any(byte[].class), eq("png")))
                .thenReturn("1/abc123.png");
        when(shopRepository.findById(TENANT_ID)).thenReturn(Optional.of(shop));
        when(productImageRepository.save(any(ProductImage.class))).thenAnswer(invocation -> {
            ProductImage saved = invocation.getArgument(0);
            saved.setId(12L);
            return saved;
        });

        ProductImageResponse response = productImageService.upload(TENANT_ID, file);

        assertThat(response.getId()).isEqualTo(12L);
        assertThat(response.getContentType()).isEqualTo("image/png");
        assertThat(response.getFileName()).isEqualTo("ao-thun.png");
        assertThat(response.getFileSize()).isEqualTo((long) content.length);
        assertThat(response.getSortOrder()).isZero();
        assertThat(response.isPrimary()).isFalse();
        assertThat(response.getUrl()).startsWith("/api/media/products/12?t=1&e=").contains("&s=");
    }

    // ---------------------------------------------------------------- DELETE

    @Test
    void delete_imageOfAnotherTenant_throwsNotFound() {
        when(productImageRepository.findByIdAndShopId(99L, TENANT_ID)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> productImageService.delete(TENANT_ID, 99L));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(productImageRepository, never()).delete(any(ProductImage.class));
        verifyNoInteractions(localMediaStorage);
    }

    @Test
    void delete_primaryImage_promotesNextImage() {
        Product product = product();
        ProductImage primary = image(12L, product, true, 0);
        ProductImage next = image(13L, product, false, 1);

        when(productImageRepository.findByIdAndShopId(12L, TENANT_ID)).thenReturn(Optional.of(primary));
        when(productImageRepository.findByShopIdAndProductIdOrderBySortOrderAscIdAsc(TENANT_ID, PRODUCT_ID))
                .thenReturn(List.of(next));

        productImageService.delete(TENANT_ID, 12L);

        assertThat(next.getIsPrimary()).isTrue();
        verify(productImageRepository).save(next);
        verify(localMediaStorage).delete("1/img-12.png");
    }

    @Test
    void delete_lastImageOfProduct_clearsLegacyImageUrl() {
        Product product = product();
        product.setImageUrl("https://example.com/cu.jpg");
        ProductImage only = image(12L, product, true, 0);

        when(productImageRepository.findByIdAndShopId(12L, TENANT_ID)).thenReturn(Optional.of(only));
        when(productImageRepository.findByShopIdAndProductIdOrderBySortOrderAscIdAsc(TENANT_ID, PRODUCT_ID))
                .thenReturn(List.of());
        when(productRepository.findByIdAndShopId(PRODUCT_ID, TENANT_ID)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenAnswer(invocation -> invocation.getArgument(0));

        productImageService.delete(TENANT_ID, 12L);

        assertThat(product.getImageUrl()).isNull();
        verify(productRepository).save(product);
        verify(localMediaStorage).delete("1/img-12.png");
    }

    // ------------------------------------------------------------------ SYNC

    @Test
    void sync_nullImageIds_keepsExistingImages() {
        Product product = product();
        List<ProductImage> existing = List.of(image(12L, product, true, 0), image(13L, product, false, 1));

        when(productImageRepository.findByShopIdAndProductIdOrderBySortOrderAscIdAsc(TENANT_ID, PRODUCT_ID))
                .thenReturn(existing);

        List<ProductImageResponse> responses =
                productImageService.syncProductImages(TENANT_ID, product, null, null);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).isPrimary()).isTrue();
        verify(productImageRepository, never()).saveAll(anyList());
        verify(productImageRepository, never()).deleteAll(anyList());
    }

    @Test
    void sync_imageOfAnotherProduct_throwsNotFound() {
        Product product = product();
        Product other = Product.builder()
                .id(99L)
                .shop(shop)
                .name("Sản phẩm khác")
                .status(ProductStatus.ACTIVE)
                .build();

        when(productImageRepository.findByShopIdAndIdIn(TENANT_ID, List.of(15L)))
                .thenReturn(List.of(image(15L, other, false, 0)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> productImageService.syncProductImages(TENANT_ID, product, List.of(15L), null));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void sync_missingImage_throwsNotFound() {
        Product product = product();
        when(productImageRepository.findByShopIdAndIdIn(eq(TENANT_ID), anyCollection()))
                .thenReturn(List.of());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> productImageService.syncProductImages(TENANT_ID, product, List.of(77L), null));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ex.getMessage()).contains("77");
    }

    @Test
    void sync_duplicateImageIds_throwsBadRequest() {
        Product product = product();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> productImageService.syncProductImages(TENANT_ID, product, List.of(12L, 12L), null));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getMessage()).contains("lặp");
        verifyNoInteractions(productImageRepository);
    }

    @Test
    void sync_tooManyImages_throwsBadRequest() {
        Product product = product();
        List<Long> elevenIds = List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> productImageService.syncProductImages(TENANT_ID, product, elevenIds, null));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getMessage()).contains("10 ảnh");
    }

    @Test
    void sync_primaryImageNotInList_throwsBadRequest() {
        Product product = product();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> productImageService.syncProductImages(TENANT_ID, product, List.of(12L), 99L));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getMessage()).contains("Ảnh chính");
        verifyNoInteractions(productImageRepository);
    }

    @Test
    void sync_validList_assignsOrderAndPrimary() {
        Product product = product();
        ProductImage first = image(15L, null, false, 0);
        ProductImage second = image(12L, null, false, 0);

        when(productImageRepository.findByShopIdAndIdIn(eq(TENANT_ID), anyCollection()))
                .thenReturn(List.of(first, second));
        when(productImageRepository.findByShopIdAndProductIdOrderBySortOrderAscIdAsc(TENANT_ID, PRODUCT_ID))
                .thenReturn(List.of());

        List<ProductImageResponse> responses = productImageService.syncProductImages(
                TENANT_ID, product, List.of(15L, 12L), 12L);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).getId()).isEqualTo(15L);
        assertThat(responses.get(0).getSortOrder()).isZero();
        assertThat(responses.get(0).isPrimary()).isFalse();
        assertThat(responses.get(1).getId()).isEqualTo(12L);
        assertThat(responses.get(1).getSortOrder()).isEqualTo(1);
        assertThat(responses.get(1).isPrimary()).isTrue();
        assertThat(first.getProduct()).isSameAs(product);
        verify(productImageRepository).saveAll(anyList());
    }

    @Test
    void sync_emptyList_removesAllImages() {
        Product product = product();
        ProductImage old = image(12L, product, true, 0);

        when(productImageRepository.findByShopIdAndProductIdOrderBySortOrderAscIdAsc(TENANT_ID, PRODUCT_ID))
                .thenReturn(List.of(old));

        List<ProductImageResponse> responses =
                productImageService.syncProductImages(TENANT_ID, product, List.of(), null);

        assertThat(responses).isEmpty();
        verify(productImageRepository).deleteAll(List.of(old));
        verify(localMediaStorage).delete("1/img-12.png");
    }

    @Test
    void serveSignedImage_wrongTenant_throwsForbidden() {
        ProductImage image = image(12L, product(), false, 0);
        when(productImageRepository.findById(12L)).thenReturn(Optional.of(image));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> productImageService.serveSignedImage(12L, 2L, System.currentTimeMillis() / 1000 + 600, "abc"));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(localMediaStorage);
    }

    // --------------------------------------------------------------- FIXTURE

    private Product product() {
        return Product.builder()
                .id(PRODUCT_ID)
                .shop(shop)
                .name("Áo thun")
                .platformSource("MANUAL")
                .status(ProductStatus.ACTIVE)
                .build();
    }

    private ProductImage image(Long id, Product product, boolean primary, int sortOrder) {
        return ProductImage.builder()
                .id(id)
                .shop(shop)
                .product(product)
                .storageKey("1/img-" + id + ".png")
                .fileName("img-" + id + ".png")
                .contentType("image/png")
                .fileSize(100L)
                .sortOrder(sortOrder)
                .isPrimary(primary)
                .build();
    }

    private static byte[] pngBytes() {
        return new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D
        };
    }
}
