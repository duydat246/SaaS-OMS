package com.saasoms.modules.order.repository;

import com.saasoms.modules.order.entity.Order;
import com.saasoms.modules.order.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByIdAndShopId(Long id, Long shopId);

    Page<Order> findByShopId(Long shopId, Pageable pageable);

    Page<Order> findByShopIdAndStatus(Long shopId, OrderStatus status, Pageable pageable);

    @Query("""
        SELECT o FROM Order o
        WHERE o.shop.id = :shopId
          AND (:status IS NULL OR o.status = :status)
          AND (:search IS NULL OR
               LOWER(o.customer.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
               OR CAST(o.id AS string) LIKE CONCAT('%', :search, '%'))
        """)
    Page<Order> search(
            @Param("shopId") Long shopId,
            @Param("status") OrderStatus status,
            @Param("search") String search,
            Pageable pageable);

    long countByShop_Id(Long shopId);

    long countByShop_IdAndStatus(Long shopId, OrderStatus status);

    @Query("""
            SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o
            WHERE o.shop.id = :shopId AND o.status = :status
            """)
    BigDecimal sumTotalAmountByShopIdAndStatus(
            @Param("shopId") Long shopId,
            @Param("status") OrderStatus status);
}
