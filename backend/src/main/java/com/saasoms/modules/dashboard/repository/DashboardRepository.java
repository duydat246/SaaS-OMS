package com.saasoms.modules.dashboard.repository;

import com.saasoms.modules.order.entity.Order;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Chỉ đọc, không kế thừa JpaRepository để không lộ thao tác ghi.
 * {@code from}, {@code to} là wall clock của JVM, cùng hệ quy chiếu với giá trị ứng dụng ghi vào orders.
 */
public interface DashboardRepository extends Repository<Order, Long> {

    /**
     * Native vì JPQL không có DATE_ADD theo phút. Lọc trước trong derived table rồi GROUP BY alias,
     * để biểu thức ngày không phải lặp lại và không vướng ONLY_FULL_GROUP_BY.
     */
    @Query(value = """
            SELECT d.bucket AS bucket,
                   SUM(CASE WHEN d.status = 'DELIVERED' THEN d.total_amount ELSE 0 END) AS deliveredRevenue,
                   SUM(CASE WHEN d.status <> 'DELIVERED' THEN d.total_amount ELSE 0 END) AS openAmount
            FROM (SELECT o.status, o.total_amount,
                         DATE_FORMAT(DATE_ADD(COALESCE(o.ordered_at, o.created_at), INTERVAL :offsetMinutes MINUTE), '%Y-%m-%d') AS bucket
                  FROM orders o
                  WHERE o.tenant_id = :shopId AND o.status <> 'CANCELLED'
                    AND COALESCE(o.ordered_at, o.created_at) >= :from
                    AND COALESCE(o.ordered_at, o.created_at) <  :to) d
            GROUP BY d.bucket ORDER BY d.bucket
            """, nativeQuery = true)
    List<DayBucketRow> sumByDay(@Param("shopId") Long shopId,
                                @Param("from") LocalDateTime from,
                                @Param("to") LocalDateTime to,
                                @Param("offsetMinutes") int offsetMinutes);

    /** Item có SKU gom theo sản phẩm, nên đổi tên không tách dòng. Item cũ không có SKU gom theo tên đã lưu. */
    @Query("""
            SELECT p.id AS productId,
                   MAX(p.name) AS productName,
                   MAX(oi.productName) AS snapshotName,
                   SUM(oi.quantity) AS quantity,
                   SUM(oi.totalPrice) AS amount
            FROM OrderItem oi
            JOIN oi.order o
            LEFT JOIN Sku s ON s.id = oi.skuId AND s.shop.id = :shopId
            LEFT JOIN s.product p
            WHERE o.shop.id = :shopId
              AND o.status = com.saasoms.modules.order.entity.OrderStatus.DELIVERED
              AND COALESCE(o.orderedAt, o.createdAt) >= :from
              AND COALESCE(o.orderedAt, o.createdAt) < :to
            GROUP BY p.id, CASE WHEN p.id IS NULL THEN oi.productName ELSE '' END
            ORDER BY SUM(oi.totalPrice) DESC, SUM(oi.quantity) DESC, MAX(oi.productName) ASC
            """)
    List<TopProductRow> findTopProducts(@Param("shopId") Long shopId,
                                        @Param("from") LocalDateTime from,
                                        @Param("to") LocalDateTime to,
                                        Pageable pageable);

    @Query("""
            SELECT p.category AS category,
                   SUM(oi.quantity) AS quantity,
                   SUM(oi.totalPrice) AS amount
            FROM OrderItem oi
            JOIN oi.order o
            LEFT JOIN Sku s ON s.id = oi.skuId AND s.shop.id = :shopId
            LEFT JOIN s.product p
            WHERE o.shop.id = :shopId
              AND o.status = com.saasoms.modules.order.entity.OrderStatus.DELIVERED
              AND COALESCE(o.orderedAt, o.createdAt) >= :from
              AND COALESCE(o.orderedAt, o.createdAt) < :to
            GROUP BY p.category
            """)
    List<CategoryRow> sumByCategory(@Param("shopId") Long shopId,
                                    @Param("from") LocalDateTime from,
                                    @Param("to") LocalDateTime to);

    interface DayBucketRow {
        String getBucket();

        BigDecimal getDeliveredRevenue();

        BigDecimal getOpenAmount();
    }

    interface TopProductRow {
        Long getProductId();

        String getProductName();

        String getSnapshotName();

        Long getQuantity();

        BigDecimal getAmount();
    }

    interface CategoryRow {
        String getCategory();

        Long getQuantity();

        BigDecimal getAmount();
    }
}
