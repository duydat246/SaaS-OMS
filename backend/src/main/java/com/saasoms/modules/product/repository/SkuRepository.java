package com.saasoms.modules.product.repository;

import com.saasoms.modules.product.entity.Sku;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SkuRepository extends JpaRepository<Sku, Long> {

    List<Sku> findByShopIdAndProductIdIn(Long shopId, Collection<Long> productIds);

    List<Sku> findByShopIdAndIdIn(Long shopId, Collection<Long> ids);

    List<Sku> findByShopIdAndProductId(Long shopId, Long productId);

    /**
     * Khoá biến thể theo tenant để trừ/hoàn tồn kho an toàn khi nhiều đơn
     * được xác nhận cùng lúc. Bắt buộc gọi trong transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Sku s WHERE s.shop.id = :shopId AND s.id IN :ids")
    List<Sku> findForUpdate(@Param("shopId") Long shopId, @Param("ids") Collection<Long> ids);


    Optional<Sku> findByIdAndShopId(Long id, Long shopId);

    Optional<Sku> findByShopIdAndSkuCode(Long shopId, String skuCode);

    List<Sku> findByShopIdAndSkuCodeIn(Long shopId, Collection<String> skuCodes);

    boolean existsByShopIdAndSkuCode(Long shopId, String skuCode);

    boolean existsByShopIdAndSkuCodeAndIdNot(Long shopId, String skuCode, Long id);

    @Query(value = """
            SELECT DISTINCT oi.sku_id FROM order_items oi
            WHERE oi.sku_id IN (:skuIds)
              AND oi.order_id IN (SELECT o.id FROM orders o WHERE o.tenant_id = :tenantId)
            """, nativeQuery = true)
    List<Long> findUsedSkuIds(@Param("tenantId") Long tenantId, @Param("skuIds") Collection<Long> skuIds);
}
