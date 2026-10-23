package com.saasoms.modules.product.repository;

import com.saasoms.modules.product.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    Optional<ProductImage> findByIdAndShopId(Long id, Long shopId);

    List<ProductImage> findByShopIdAndProductIdOrderBySortOrderAscIdAsc(Long shopId, Long productId);

    List<ProductImage> findByShopIdAndProductIdInOrderBySortOrderAscIdAsc(Long shopId,
                                                                          Collection<Long> productIds);

    List<ProductImage> findByShopIdAndIdIn(Long shopId, Collection<Long> ids);

    List<ProductImage> findByProductIsNullAndCreatedAtBefore(LocalDateTime cutoff);
}
