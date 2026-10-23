package com.saasoms.modules.product.repository;

import com.saasoms.modules.product.entity.Product;
import com.saasoms.modules.product.entity.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByIdAndShopId(Long id, Long shopId);

    boolean existsByIdAndShopId(Long id, Long shopId);

    long countByShopIdAndStatus(Long shopId, ProductStatus status);

    long countByShopIdAndStatusIn(Long shopId, Collection<ProductStatus> statuses);

    @Query("""
            SELECT p FROM Product p
            WHERE p.shop.id = :shopId
              AND p.status IN :statuses
              AND (:category IS NULL OR p.category = :category)
              AND (:platformSource IS NULL OR p.platformSource = :platformSource)
              AND (:search IS NULL OR
                   LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(COALESCE(p.category, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR EXISTS (
                       SELECT s.id FROM Sku s
                       WHERE s.product = p
                         AND LOWER(s.skuCode) LIKE LOWER(CONCAT('%', :search, '%'))
                   ))
            """)
    Page<Product> search(
            @Param("shopId") Long shopId,
            @Param("statuses") Collection<ProductStatus> statuses,
            @Param("category") String category,
            @Param("platformSource") String platformSource,
            @Param("search") String search,
            Pageable pageable);

    @Query("""
            SELECT DISTINCT p.category FROM Product p
            WHERE p.shop.id = :shopId
              AND p.status <> :archivedStatus
              AND p.category IS NOT NULL
              AND p.category <> ''
            ORDER BY p.category ASC
            """)
    List<String> findDistinctCategories(
            @Param("shopId") Long shopId,
            @Param("archivedStatus") ProductStatus archivedStatus);

    @Query("""
            SELECT COUNT(p) FROM Product p
            WHERE p.shop.id = :shopId
              AND p.status <> :archivedStatus
              AND NOT EXISTS (
                  SELECT s.id FROM Sku s
                  WHERE s.product = p
                    AND s.stock > 0
              )
            """)
    long countOutOfStock(
            @Param("shopId") Long shopId,
            @Param("archivedStatus") ProductStatus archivedStatus);

    @Query("""
            SELECT COALESCE(SUM(s.stock), 0) FROM Sku s
            WHERE s.shop.id = :shopId
              AND s.product.status <> :archivedStatus
            """)
    Long sumTotalStock(
            @Param("shopId") Long shopId,
            @Param("archivedStatus") ProductStatus archivedStatus);
}
