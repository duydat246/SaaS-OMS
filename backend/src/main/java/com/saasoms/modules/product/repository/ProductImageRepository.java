package com.saasoms.modules.product.repository;

import com.saasoms.modules.product.entity.ProductImage;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    Optional<ProductImage> findByIdAndShopId(Long id, Long shopId);

    List<ProductImage> findByShopIdAndProductIdOrderBySortOrderAscIdAsc(Long shopId, Long productId);

    /**
     * Locking read: thấy bản đã commit mới nhất, không phụ thuộc snapshot REPEATABLE READ mà câu đọc
     * thường trước đó đã chốt. Chỉ gọi sau khi đã khoá sản phẩm, để giữ thứ tự khoá products rồi
     * product_images. Bắt buộc gọi trong transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM ProductImage i WHERE i.shop.id = :shopId AND i.product.id = :productId "
            + "ORDER BY i.sortOrder ASC, i.id ASC")
    List<ProductImage> findForUpdateByShopIdAndProductId(@Param("shopId") Long shopId,
                                                         @Param("productId") Long productId);

    List<ProductImage> findByShopIdAndProductIdInOrderBySortOrderAscIdAsc(Long shopId,
                                                                          Collection<Long> productIds);

    List<ProductImage> findByShopIdAndIdIn(Long shopId, Collection<Long> ids);

    List<ProductImage> findByProductIsNullAndCreatedAtBefore(LocalDateTime cutoff);
}
